package clinic.backend.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import clinic.backend.entity.Conclusion;
import clinic.backend.entity.Patient;
import clinic.backend.entity.Prescription;
import clinic.backend.entity.PrescriptionDetail;
import clinic.backend.entity.PrescriptionDiagnosis;
import clinic.backend.entity.Visit;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The prescription as sent to the national prescription system (BR-59-11, QĐ 808/QĐ-BYT VII.3): UTF-8 JSON, keys in
 * the order of VII.3, absent optional values left out. It carries no {@code signature}: the digital signature is out
 * of scope (BR-47) and VII.3 row 27 does not require it.
 */
final class PrescriptionPayloadBuilder {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /**
     * QĐ 808 VII.3 row 14: required for type c, from the Ministry's list, which the project does not have; fixed
     * to outpatient (PO 12/11). The code value is ASSUMPTION until the list is added (Decisions needed 4).
     */
    static final int OUTPATIENT_TREATMENT = 1;
    /** QĐ 808 VII.3 rows 10, 11, 18–20, 22: text fields of at most 500 characters. */
    static final int TEXT_MAX = 500;
    /** QĐ 808 VII.3 row 24: a string of at most 12 digits. */
    private static final Pattern PHONE = Pattern.compile("[0-9]{1,12}");
    private static final ObjectMapper JSON = new ObjectMapper();

    private PrescriptionPayloadBuilder() {
    }

    /**
     * @param weightKg           latest weight of the visit, if any
     * @param accompanyingPerson BR-59-07: for a child under 72 months, the relative's name, phone and address
     * @param followUpDate       the follow-up date copied onto the prescription, if any
     */
    static byte[] payload(
            Prescription prescription, Visit visit, Patient patient, Conclusion conclusion, BigDecimal weightKg,
            String accompanyingPerson, LocalDate followUpDate) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("loai_don_thuoc", prescription.getLoaidon());
        payload.put("ma_don_thuoc", prescription.getMadonthuoc());
        payload.put("ho_ten_benh_nhan", patient.getHoten());
        payload.put("ngay_sinh_benh_nhan", DATE.format(patient.getNgaysinh()));
        putIfPresent(payload, "ma_dinh_danh_cong_dan", citizenId(patient.getCccdSo()));
        putIfPresent(payload, "can_nang", weightKg == null ? null : weightKg.stripTrailingZeros());
        payload.put("gioi_tinh", nationalSex(patient.getGioitinh()));
        putIfPresent(payload, "ma_so_bao_hiem_y_te",
                visit.isInsuranceCovered() ? text(visit.getInsuranceSocialNumber()) : null);
        putIfPresent(payload, "thong_tin_nguoi_giam_ho", accompanyingPerson);
        payload.put("dia_chi", address(patient));
        payload.put("chan_doan", prescription.getDiagnoses().stream()
                .sorted(Comparator.comparingInt(PrescriptionDiagnosis::getThuTu))
                .map(diagnosis -> {
                    // QĐ 808 VII.3 row 12 names ma_benh, ten_benh, ket_luan (the sample request names
                    // ma_chan_doan, ten_chan_doan; the table is followed, ASSUMPTION).
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("ma_benh", diagnosis.getMaicd());
                    item.put("ten_benh", diagnosis.getTenicd());
                    putIfPresent(item, "ket_luan", text(conclusion.getChandoan()));
                    return item;
                })
                .toList());
        putIfPresent(payload, "luu_y", text(prescription.getLuuY()));
        payload.put("hinh_thuc_dieu_tri", OUTPATIENT_TREATMENT);
        payload.put("thong_tin_don_thuoc", prescription.getDetails().stream()
                .sorted(Comparator.comparing(PrescriptionDetail::getMachidinh))
                .map(PrescriptionPayloadBuilder::medicine)
                .toList());
        putIfPresent(payload, "loi_dan", text(prescription.getLoiDan()));
        putIfPresent(payload, "so_dien_thoai_nguoi_kham_benh", phone(patient.getSodienthoai()));
        LocalDate prescribedOn = LocalDate.ofInstant(prescription.getPrescribedAt(), CLINIC_ZONE);
        putIfPresent(payload, "ngay_tai_kham",
                followUpDate == null ? null : ChronoUnit.DAYS.between(prescribedOn, followUpDate));
        payload.put("ngay_gio_ke_don", DATE_TIME.format(prescription.getPrescribedAt().atZone(CLINIC_ZONE)));
        try {
            return JSON.writeValueAsBytes(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("The prescription payload cannot be written", exception);
        }
    }

    /**
     * BR-59-11: cach_dung = "{route}: {dose} × {times}/ngày × {days} ngày, {usage}" — QĐ 808 VII.3 row 22 asks for the
     * route, the dose and the duration (order ASSUMPTION). The sender checks the 500-character limit; it is never cut.
     */
    static String usage(PrescriptionDetail detail) {
        String dosing = "%s × %d/ngày × %d ngày, %s".formatted(
                detail.getLieuMoiLan(), detail.getSoLanNgay(), detail.getSoNgayDung(), detail.getCachdung());
        String route = text(detail.getDuongdung());
        return route == null ? dosing : route + ": " + dosing;
    }

    /**
     * BR-59-11: ten_thuoc = the trade name followed by the strength unless the name already contains it (TT 26/2025
     * Điều 6 khoản 5–6 require the strength; QĐ 808 has no field for it, ASSUMPTION).
     */
    static String medicineName(PrescriptionDetail detail) {
        String name = detail.getTenthuocvattu().strip();
        String strength = text(detail.getHamluong());
        if (strength == null || squeezed(name).contains(squeezed(strength))) {
            return name;
        }
        return name + " " + strength;
    }

    private static String squeezed(String value) {
        return value.replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
    }

    /** BR-59-07: the relative's name, phone and address (QĐ 808 VII.3 row 10). */
    static String accompanyingPerson(Patient patient) {
        String joined = Stream.of(patient.getHotennguoithan(), patient.getSodienthoainguoithan(),
                        patient.getDiachinguoithan())
                .map(PrescriptionPayloadBuilder::text)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));
        return joined.isEmpty() ? null : joined;
    }

    private static Map<String, Object> medicine(PrescriptionDetail detail) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("ma_thuoc", detail.getSoDangKy());
        item.put("biet_duoc", detail.getHoatchat());
        item.put("ten_thuoc", medicineName(detail));
        item.put("don_vi_tinh", detail.getDonvitinh());
        // A whole number of the dispensing unit (BR-59-04).
        item.put("so_luong", detail.getSoluongthuocvattu().stripTrailingZeros().toBigIntegerExact());
        item.put("cach_dung", usage(detail));
        return item;
    }

    /** BR-54: the patient's 1 male / 2 female / 3 undetermined becomes QĐ 808's 2 male / 3 female / 1 unknown. */
    static int nationalSex(short gioitinh) {
        return switch (gioitinh) {
            case 1 -> 2;
            case 2 -> 3;
            default -> 1;
        };
    }

    private static String citizenId(String value) {
        String text = text(value);
        return text != null && text.matches("[0-9]{12}") ? text : null;
    }

    /**
     * BR-59-07: the patient's address, ward and province (also printed, BR-59-13). Sending refuses one longer than
     * {@link #TEXT_MAX}; it is never cut.
     */
    static String address(Patient patient) {
        List<String> parts = Stream.of(patient.getDiachi(), patient.getXaphuong(), patient.getTinhthanh())
                .map(PrescriptionPayloadBuilder::text)
                .filter(Objects::nonNull)
                .toList();
        return String.join(", ", parts);
    }

    /** QĐ 808 VII.3 row 24: sent only as digits, at most 12 (spaces, dots and dashes removed); otherwise left out. */
    static String phone(String value) {
        String text = text(value);
        if (text == null) {
            return null;
        }
        String digits = text.replaceAll("[\\s.\\-]", "");
        return PHONE.matcher(digits).matches() ? digits : null;
    }

    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
