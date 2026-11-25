// thu thap du lieu va chuyen sang json
package clinic.backend.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import clinic.backend.entity.ClinicFacility;
import clinic.backend.entity.ClinicalExamination;
import clinic.backend.entity.Conclusion;
import clinic.backend.entity.Department;
import clinic.backend.entity.ExaminationFinding;
import clinic.backend.entity.FollowUpAppointment;
import clinic.backend.entity.ImagingResult;
import clinic.backend.entity.LabResult;
import clinic.backend.entity.LabResultItem;
import clinic.backend.entity.MedicalHistory;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.ParaclinicalOrder;
import clinic.backend.entity.ParaclinicalOrderDetail;
import clinic.backend.entity.Patient;
import clinic.backend.entity.Prescription;
import clinic.backend.entity.PrescriptionDetail;
import clinic.backend.entity.PrescriptionSubmission;
import clinic.backend.entity.Room;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.Visit;
import clinic.backend.entity.VitalSign;
import clinic.backend.enums.OrganSystem;
import clinic.backend.enums.RecordArchiveRetentionCategory;
import clinic.backend.repository.ClinicFacilityRepository;
import clinic.backend.repository.ClinicalExaminationRepository;
import clinic.backend.repository.DepartmentRepository;
import clinic.backend.repository.FollowUpAppointmentRepository;
import clinic.backend.repository.ImagingResultRepository;
import clinic.backend.repository.LabResultRepository;
import clinic.backend.repository.MedicalHistoryRepository;
import clinic.backend.repository.ParaclinicalOrderRepository;
import clinic.backend.repository.PrescriptionRepository;
import clinic.backend.repository.PrescriptionSubmissionRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.VitalSignRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * UC-58 BR-58-06: Trình xây dựng gói dữ liệu lưu trữ hồ sơ bệnh án điện tử (Archive Package Builder).
 * Đóng gói toàn bộ hồ sơ bệnh án tại thời điểm đóng bệnh án (theo Luật KBCB 2023 Điều 2 khoản 17)
 * theo cấu trúc trao đổi chuẩn của Công văn 365/TTYQG-GPQLCL Phụ lục "Mô tả dữ liệu trao đổi hồ sơ bệnh án điện tử"
 * dưới dạng JSON hoặc XML.
 *
 * <p>Quyết định thiết kế (Decision A - 17/11/2026): Chỉ ghi nhận các trường dữ liệu CV 365 mà phòng khám quản lý;
 * bỏ qua các nhóm nội trú và phẫu thuật (Nhóm VIII - XI, giường bệnh, chuyển khoa).
 * Các dữ liệu mở rộng ngoài CV 365 được đưa vào nhóm {@code extensions};
 * Dữ liệu chủ quản lý lưu trữ (theo Luật Lưu trữ 2024 Điều 33 khoản 2) được đưa vào {@code DuLieuChu}.
 * Định dạng tuân thủ BR-54: giới tính 1/2/3, thời gian yyyyMMddHHmm và ngày yyyyMMdd theo múi giờ Asia/Ho_Chi_Minh,
 * họ tên bệnh nhân viết hoa toàn bộ. Giữ thứ tự LinkedHashMap để đảm bảo tính toàn vẹn (tạo ra cùng chuỗi byte và mã băm).
 */
@Component
@RequiredArgsConstructor
public class ArchivePackageBuilder {

    static final String SPEC_VERSION = "CV365/2025";
    static final String PACKAGE_FORMAT = "UC58-ARCHIVE/1";

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final ObjectWriter PRETTY_JSON = new ObjectMapper()
            .writer(new DefaultPrettyPrinter()
                    .withObjectIndenter(new DefaultIndenter("  ", "\n"))
                    .withArrayIndenter(new DefaultIndenter("  ", "\n")));

    private static final Map<String, String> ORGAN_KEYS = Map.of(
            OrganSystem.CARDIOVASCULAR.name(), "tuanhoan",
            OrganSystem.RESPIRATORY.name(), "hohap",
            OrganSystem.GASTROINTESTINAL.name(), "tieuhoa",
            OrganSystem.RENAL_URINARY.name(), "thantietnieu",
            OrganSystem.NEUROLOGICAL.name(), "thankinh",
            OrganSystem.MUSCULOSKELETAL.name(), "coxuongkhop",
            OrganSystem.EAR_NOSE_THROAT.name(), "taimuihong",
            OrganSystem.ENDOCRINE_NUTRITIONAL_OTHER.name(), "noitiet_dinhduong_benhlikhac");

    private final ClinicFacilityRepository facilityRepository;
    private final RoomRepository roomRepository;
    private final DepartmentRepository departmentRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final MedicalHistoryRepository medicalHistoryRepository;
    private final ClinicalExaminationRepository examinationRepository;
    private final VitalSignRepository vitalSignRepository;
    private final ParaclinicalOrderRepository paraclinicalOrderRepository;
    private final ImagingResultRepository imagingResultRepository;
    private final LabResultRepository labResultRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionSubmissionRepository submissionRepository;
    private final FollowUpAppointmentRepository followUpRepository;

    /**
     * Bản ghi dữ liệu ngữ cảnh đóng hồ sơ (Closing Context).
     * Chứa các thông tin trong phiên giao dịch đóng bệnh án mà cơ sở dữ liệu có thể chưa cập nhật hoặc đang xử lý.
     *
     * @param visit Lượt khám của bệnh nhân
     * @param patient Thông tin hành chính bệnh nhân
     * @param record Hồ sơ bệnh án
     * @param conclusion Kết luận điều trị của bác sĩ
     * @param doctor Bác sĩ thực hiện đóng hồ sơ bệnh án
     * @param soluutru Mã số lưu trữ hồ sơ bệnh án (VD: LT-DK...)
     * @param closedAt Thời điểm đóng hồ sơ
     * @param retentionCategory Nhóm loại thời hạn lưu trữ theo luật (5 năm, 10 năm, 15 năm, 20 năm, v.v.)
     * @param expiresOn Ngày hết hạn lưu trữ hồ sơ
     */
    public record Closing(
            Visit visit, Patient patient, MedicalRecord record, Conclusion conclusion, StaffAccount doctor,
            String soluutru, Instant closedAt, RecordArchiveRetentionCategory retentionCategory,
            LocalDate expiresOn) {
    }

    /**
     * Tập hợp toàn bộ dữ liệu từ các entity liên quan để tạo cấu trúc Map phân cấp hoàn chỉnh của gói lưu trữ.
     * Cấu trúc bao gồm 3 khối gốc:
     * - DuLieuChu: Siêu dữ liệu quản lý lưu trữ (Luật Lưu trữ 2024 Điều 33)
     * - HoSoBenhAn: 7 nhóm thông tin lâm sàng chuẩn hóa theo Công văn 365
     * - extensions: Các dữ liệu nghiệp vụ riêng của phòng khám (khám bộ phận, đơn thuốc điện tử, hẹn tái khám)
     *
     * @param closing Ngữ cảnh đóng hồ sơ bệnh án
     * @return Map phân cấp chứa toàn bộ nội dung gói hồ sơ bệnh án
     */
    public Map<String, Object> buildPayload(Closing closing) {
        Visit visit = closing.visit();
        Room room = roomRepository.findById(visit.getRoomId()).orElse(null);
        Department department = room == null || room.getDepartmentId() == null
                ? null : departmentRepository.findById(room.getDepartmentId()).orElse(null);
        ClinicalExamination examination =
                examinationRepository.findByMedicalRecordId(closing.record().getId()).orElse(null);
        List<ParaclinicalOrder> orders = paraclinicalOrderRepository.findAllByMedicalRecordId(closing.record().getId());
        Prescription prescription = prescriptionRepository.findByMedicalRecordId(closing.record().getId()).orElse(null);
        FollowUpAppointment followUp = followUpRepository.findByMedicalRecordId(closing.record().getId()).orElse(null);
        List<ParaclinicalOrderDetail> details = orders.stream().flatMap(order -> order.getDetails().stream()).toList();
        Set<UUID> detailIds = details.stream().map(ParaclinicalOrderDetail::getId).collect(Collectors.toSet());
        Map<UUID, ImagingResult> imaging = detailIds.isEmpty() ? Map.of()
                : imagingResultRepository.findByOrderDetailIdIn(detailIds).stream()
                        .collect(Collectors.toMap(ImagingResult::getOrderDetailId, Function.identity()));
        Map<UUID, LabResult> labs = detailIds.isEmpty() ? Map.of()
                : labResultRepository.findByOrderDetailIdIn(detailIds).stream()
                        .collect(Collectors.toMap(LabResult::getOrderDetailId, Function.identity()));
        Map<UUID, StaffAccount> staff = staff(closing, examination, orders, imaging, labs, prescription);

        Map<String, Object> hoSo = new LinkedHashMap<>();
        hoSo.put("ThongTinBenhNhan", patientGroup(closing, department));
        hoSo.put("ThongTinVaoVien", admissionGroup(closing, room, examination, staff));
        hoSo.put("ThongTinDieuTri", treatmentGroup(closing, department));
        hoSo.put("YLenhThuocVatTu", prescriptionGroup(visit, prescription, staff));
        hoSo.put("PhieuChiDinh", orderGroup(visit, orders, staff));
        hoSo.put("KetQuaChanDoanHinhAnh", imagingGroup(visit, details, imaging, staff));
        hoSo.put("KetQuaXetNghiem", labGroup(visit, details, labs, staff));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("DuLieuChu", metadata(closing));
        root.put("HoSoBenhAn", hoSo);
        root.put("extensions", extensions(closing, examination, prescription, followUp));
        return root;
    }

    /**
     * Xuất toàn bộ gói dữ liệu hồ sơ bệnh án thành chuỗi byte JSON được định dạng đẹp (pretty-printed 2 khoảng trắng).
     *
     * @param closing Ngữ cảnh đóng hồ sơ bệnh án
     * @return Mảng byte chứa nội dung JSON
     * @throws IllegalStateException Nếu không thể tuần tự hóa JSON
     */
    public byte[] build(Closing closing) {
        try {
            return PRETTY_JSON.writeValueAsBytes(buildPayload(closing));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize the archive package", exception);
        }
    }

    /**
     * Xuất toàn bộ gói dữ liệu hồ sơ bệnh án thành chuỗi byte XML chuẩn Công văn 365.
     * Sử dụng tiện ích {@link clinic.backend.utils.Cv365XmlSerializer}.
     *
     * @param closing Ngữ cảnh đóng hồ sơ bệnh án
     * @return Mảng byte chứa nội dung XML chuẩn CV 365
     */
    public byte[] buildXml(Closing closing) {
        return clinic.backend.utils.Cv365XmlSerializer.serialize(buildPayload(closing));
    }

    /**
     * Xây dựng nhóm "DuLieuChu" (Dữ liệu chủ / Master Metadata) theo Luật Lưu trữ 2024 Điều 33 khoản 2.
     * Bao gồm: định dạng gói, phiên bản chuẩn, thông tin cơ sở khám chữa bệnh, mã lưu trữ,
     * bác sĩ đóng hồ sơ, thời điểm đóng, thời hạn lưu trữ và ngày hết hạn lưu trữ.
     *
     * @param closing Ngữ cảnh đóng hồ sơ
     * @return Map chứa các cặp khóa - giá trị của Dữ liệu chủ
     */
    private Map<String, Object> metadata(Closing closing) {
        ClinicFacility facility = facilityRepository.findCurrent().orElse(null);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("dinhdanggoi", PACKAGE_FORMAT);
        values.put("phienbanchuan", SPEC_VERSION);
        values.put("macskcb", facility == null ? null : facility.getMaCskcb());
        values.put("tencskcb", facility == null ? null : facility.getTenCskcb());
        values.put("soluutru", closing.soluutru());
        values.put("soba", closing.record().getSoba());
        values.put("sovaovien", closing.visit().getSovaovien());
        values.put("nguoidonghoso_ma", closing.doctor() == null ? null : closing.doctor().getId().toString());
        values.put("nguoidonghoso", staffName(closing.doctor()));
        values.put("thoidiemdonghoso", dateTime(closing.closedAt()));
        values.put("loaihosoluutru", closing.retentionCategory() == null ? null : closing.retentionCategory().name());
        values.put("thoihanluutru_nam", closing.retentionCategory() == null ? 10 : closing.retentionCategory().retentionYears());
        values.put("hanluutru", date(closing.expiresOn()));
        return values;
    }

    /**
     * Xây dựng Nhóm I CV 365: "ThongTinBenhNhan" (Thông tin hành chính bệnh nhân).
     * Trích xuất thông tin nhân khẩu học (mã BN, CCCD, họ tên in hoa, ngày sinh, tuổi, giới tính,
     * dân tộc, nghề nghiệp, BHYT, địa chỉ, người thân) và tiền sử bệnh tật bản thân, gia đình.
     *
     * @param closing Ngữ cảnh đóng hồ sơ
     * @param department Khoa phụ trách tiếp nhận/khám bệnh
     * @return Map chứa thông tin hành chính bệnh nhân
     */
    private Map<String, Object> patientGroup(Closing closing, Department department) {
        Visit visit = closing.visit();
        Patient patient = closing.patient();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("loaiba", closing.record().getLoaiba());
        values.put("sovaovien", visit.getSovaovien());
        values.put("soba", closing.record().getSoba());
        values.put("soluutru", closing.soluutru());
        values.put("makhoa", department == null ? null : department.getDepartmentCode());
        values.put("tenkhoa", department == null ? null : department.getDepartmentName());
        values.put("thoigianvaovien", dateTime(visit.getThoigianvaovien()));
        values.put("mabenhnhan", patient.getMabenhnhan());
        values.put("cccd_so", patient.getCccdSo());
        values.put("hochieu_so", patient.getHochieuSo());
        values.put("hoten", patient.getHoten() == null ? null : patient.getHoten().toUpperCase(Locale.ROOT));
        values.put("ngaysinh", date(patient.getNgaysinh()));
        values.put("tuoi", age(patient.getNgaysinh(), visit.getThoigianvaovien()));
        values.put("gioitinh", patient.getGioitinh());
        values.put("nghenghiep", patient.getNghenghiep());
        values.put("nghenghiep_ma", patient.getNghenghiepMa());
        values.put("dantoc", patient.getDantoc());
        values.put("dantoc_ma", patient.getDantocMa());
        values.put("ngoaikieu", patient.getNgoaikieu());
        values.put("ngoaikieu_ma", patient.getNgoaikieuMa());
        values.put("tungaybhyt", date(visit.getTungaybhyt()));
        values.put("denngaybhyt", date(visit.getDenngaybhyt()));
        values.put("mabhyt", visit.getMabhyt());
        values.put("noidangkykcbbd", visit.getNoidangkykcbbd());
        values.put("diachi", patient.getDiachi());
        values.put("xaphuong", patient.getXaphuong());
        values.put("tinhthanh", patient.getTinhthanh());
        values.put("tinhthanh_ma", patient.getTinhthanhMa());
        values.put("noilamviec", patient.getNoilamviec());
        values.put("sodienthoai", patient.getSodienthoai());
        values.put("nhommau", patient.getNhommau());
        values.put("yeutorh", patient.getYeutorh());
        values.put("doituongbn_loai", visit.getDoituongbnLoai() == null ? null : visit.getDoituongbnLoai().name());
        values.put("hotennguoithan", patient.getHotennguoithan());
        values.put("diachinguoithan", patient.getDiachinguoithan());
        values.put("sodienthoainguoithan", patient.getSodienthoainguoithan());
        values.put("lydotiepnhan", visit.getLydotiepnhan());
        values.put("noigioithieu_loai", visit.getNoigioithieuLoai());
        values.put("tiensubenhtatcuabanthan", histories(patient.getId()));
        values.put("tiensubenhtatcuagiadinh", closing.record().getTiensubenhtatcuagiadinh());
        return values;
    }

    /**
     * Lấy danh sách tiền sử bệnh tật của bản thân bệnh nhân (dị ứng, bệnh mãn tính, phẫu thuật...).
     * Kèm thêm thuộc tính mở rộng loaitiensu để phân biệt tiền sử dị ứng thuốc theo quy tắc BR-23.
     *
     * @param patientId ID của bệnh nhân
     * @return Danh sách các bản ghi tiền sử bệnh tật
     */
    private List<Map<String, Object>> histories(UUID patientId) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (MedicalHistory history : medicalHistoryRepository.findByPatientIdOrderByCreatedAtAsc(patientId)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("thongtin_ma", history.getThongtinMa());
            item.put("thongtin_noidung", history.getThongtinNoidung());
            item.put("thongtin_giatri", history.getThongtinGiatri());
            item.put("thongtin_ghichu", history.getThongtinGhichu());
            // Not a CV 365 field: tells a drug allergy from personal history (BR-23).
            item.put("loaitiensu", history.getHistoryType() == null ? null : history.getHistoryType().name());
            items.add(item);
        }
        return items;
    }

    /**
     * Xây dựng Nhóm II CV 365: "ThongTinVaoVien" (Thông tin lúc vào viện / khám lâm sàng ban đầu).
     * Bao gồm: phòng khám, bác sĩ khám, chẩn đoán ban đầu, lý do vào viện, diễn biến bệnh,
     * các chỉ số sinh hiệu ban đầu (mạch, nhiệt độ, huyết áp, nhịp thở, cân nặng, chiều cao)
     * và kết quả khám các cơ quan bộ phận.
     *
     * @param closing Ngữ cảnh đóng hồ sơ
     * @param room Phòng khám thực hiện tiếp nhận
     * @param examination Bản ghi khám bệnh lâm sàng
     * @param staff Bản đồ thông tin tài khoản nhân viên y tế đã truy vấn sẵn
     * @return Map chứa thông tin khám bệnh lúc vào viện
     */
    private Map<String, Object> admissionGroup(
            Closing closing, Room room, ClinicalExamination examination, Map<UUID, StaffAccount> staff) {
        Visit visit = closing.visit();
        VitalSign initial = vitalSignRepository.findByMedicalRecordIdAndIsInitialTrue(closing.record().getId())
                .orElse(null);
        StaffAccount examiner = examination == null ? null : staff.get(examination.getExaminedByAccountId());
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("sovaovien", visit.getSovaovien());
        values.put("sophieu", examination == null ? null : examination.getSophieu());
        values.put("phongkham", room == null ? null : room.getRoomName());
        values.put("maphongkham", room == null ? null : room.getRoomCode());
        values.put("bacsikhambenh", staffName(examiner));
        values.put("mabacsikhambenh", examiner == null ? null : examiner.getPracticeLicenseNo());
        values.put("chandoancuanoigioithieu", visit.getChandoancuanoigioithieu());
        values.put("chandoansobo", examination == null ? null : examination.getChandoansobo());
        values.put("chandoanvaovien", examination == null ? null : examination.getChandoanvaovien());
        values.put("khambenh_chandoanvaovienmaicd",
                examination == null ? null : examination.getKhambenhChandoanvaovienmaicd());
        values.put("chieucao", initial == null ? null : decimal(initial.getChieucao()));
        values.put("denkhambenhluc", dateTime(visit.getDenkhambenhluc()));
        values.put("lydovaovien", visit.getLydovaovien());
        values.put("quatrinhbenhly", examination == null ? null : examination.getQuatrinhbenhly());
        values.put("mach", initial == null ? null : initial.getMach());
        values.put("nhietdo", initial == null ? null : decimal(initial.getNhietdo()));
        values.put("nhiptho", initial == null ? null : initial.getNhiptho());
        values.put("huyetap_tamthu", initial == null ? null : initial.getHuyetapTamthu());
        values.put("huyetap_tamtruong", initial == null ? null : initial.getHuyetapTamtruong());
        values.put("cannang", initial == null ? null : decimal(initial.getCannang()));
        values.put("khambenh_toanthan", examination == null ? null : examination.getKhambenhToanthan());
        values.put("khambenh_caccoquan", examination == null ? Map.of() : organs(examination.getFindings()));
        return values;
    }

    /**
     * Nhóm các dấu chứng bất thường khám cơ quan theo từng hệ cơ quan (tuần hoàn, hô hấp, tiêu hóa...)
     * kèm mã định danh danh mục thuật ngữ (VNCODE, SNOMED).
     *
     * @param findings Danh sách kết quả khám các cơ quan
     * @return Map phân loại theo mã nhóm cơ quan
     */
    private static Map<String, Object> organs(List<ExaminationFinding> findings) {
        Map<String, Object> groups = new LinkedHashMap<>();
        for (ExaminationFinding finding : findings) {
            String key = ORGAN_KEYS.getOrDefault(finding.getCoquan(), finding.getCoquan());
            Map<String, Object> item = new LinkedHashMap<>();
            Map<String, Object> code = new LinkedHashMap<>();
            code.put("VNCODE", finding.getVncode());
            code.put("SNOMED", finding.getSnomed());
            item.put("dauchung_ma", code);
            item.put("dauchung", finding.getDauchung());
            item.put("dauchung_ghichu", finding.getDauchungGhichu());
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) groups.computeIfAbsent(key, ignored -> new ArrayList<>());
            list.add(item);
        }
        return groups;
    }

    /**
     * Xây dựng Nhóm III CV 365: "ThongTinDieuTri" (Thông tin quá trình điều trị và kết luận ra viện).
     * Bao gồm: bác sĩ điều trị, khoa điều trị, ngày ra viện, chẩn đoán chính (mã ICD-10, tên bệnh),
     * các chẩn đoán phụ (ghép ngăn cách bởi dấu chấm phẩy ';'), tóm tắt bệnh án, hướng điều trị,
     * danh sách theo dõi sinh hiệu theo thời gian và tình trạng ra viện (kết quả, lời dặn).
     *
     * @param closing Ngữ cảnh đóng hồ sơ
     * @param department Khoa điều trị
     * @return Map chứa thông tin điều trị và ra viện
     */
    /** CV 365 III.14 as saved; a conclusion saved before it was filled gets it from the main and secondary codes. */
    private static String diagnosisCodes(Conclusion conclusion, String secondaryCodes) {
        if (conclusion == null) return null;
        if (conclusion.getChandoanMa() != null) return conclusion.getChandoanMa();
        if (conclusion.getMaicd() == null) return null;
        return secondaryCodes.isEmpty() ? conclusion.getMaicd() : conclusion.getMaicd() + ";" + secondaryCodes;
    }

    private Map<String, Object> treatmentGroup(Closing closing, Department department) {
        Conclusion conclusion = closing.conclusion();
        String secondaryCodes = conclusion == null ? "" : conclusion.getSecondaryDiagnoses().stream()
                .map(diagnosis -> diagnosis.getMaicd()).collect(Collectors.joining(";"));
        String secondaryNames = conclusion == null ? "" : conclusion.getSecondaryDiagnoses().stream()
                .map(diagnosis -> diagnosis.getTenicd()).collect(Collectors.joining(";"));
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("bacsidieutri", staffName(closing.doctor()));
        values.put("bacsidieutri_ma", closing.doctor() == null ? null : closing.doctor().getPracticeLicenseNo());
        values.put("khoadieutri_ma", department == null ? null : department.getDepartmentCode());
        values.put("khoadieutri", department == null ? null : department.getDepartmentName());
        values.put("ngayravien", conclusion == null ? null : date(conclusion.getNgayravien()));
        values.put("chandoan", conclusion == null ? null : conclusion.getChandoan());
        values.put("chandoan_ma", diagnosisCodes(conclusion, secondaryCodes));
        values.put("maicd", conclusion == null ? null : conclusion.getMaicd());
        values.put("tenicd", conclusion == null ? null : conclusion.getTenicd());
        values.put("maicd_khac", secondaryCodes.isEmpty() ? null : secondaryCodes);
        values.put("tenicd_khac", secondaryNames.isEmpty() ? null : secondaryNames);
        values.put("ghichu", conclusion == null ? null : conclusion.getGhichu());
        values.put("tomtat", conclusion == null ? null : conclusion.getTomtat());
        values.put("huongdieutri", conclusion == null ? null : conclusion.getHuongdieutri());
        List<Map<String, Object>> vitals = new ArrayList<>();
        for (VitalSign sign : vitalSignRepository.findAllByMedicalRecordIdOrderByThoidiemAsc(closing.record().getId())) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("mach", sign.getMach());
            item.put("nhietdo", decimal(sign.getNhietdo()));
            item.put("huyetap_cao", sign.getHuyetapTamthu());
            item.put("huyetap_thap", sign.getHuyetapTamtruong());
            item.put("nhiptho", sign.getNhiptho());
            item.put("cannang", decimal(sign.getCannang()));
            item.put("chieucao", decimal(sign.getChieucao()));
            item.put("thoidiem", dateTime(sign.getThoidiem()));
            vitals.add(item);
        }
        values.put("danhsachsinhhieu", vitals);
        Map<String, Object> discharge = new LinkedHashMap<>();
        discharge.put("ketquadieutri", conclusion == null ? null : conclusion.getKetquadieutri());
        discharge.put("loidanbacsi", conclusion == null ? null : conclusion.getLoidanbacsi());
        discharge.put("ngayravien", conclusion == null ? null : date(conclusion.getNgayravien()));
        values.put("tinhtrangravien", discharge);
        return values;
    }

    /**
     * Xây dựng Nhóm IV CV 365: "YLenhThuocVatTu" (Y lệnh đơn thuốc ngoại trú).
     * Bao gồm: số phiếu, bác sĩ chỉ định, chẩn đoán, đối tượng bệnh nhân và danh sách chi tiết các thuốc
     * (mã thuốc bệnh viện/BYT, tên thuốc, số lượng, liều dùng, cách dùng, đường dùng).
     *
     * @param visit Lượt khám
     * @param prescription Đơn thuốc của bệnh án (nếu có)
     * @param staff Bản đồ tài khoản nhân viên y tế
     * @return Map chứa thông tin đơn thuốc và vật tư y tế
     */
    private Map<String, Object> prescriptionGroup(Visit visit, Prescription prescription, Map<UUID, StaffAccount> staff) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("sovaovien", visit.getSovaovien());
        List<Map<String, Object>> list = new ArrayList<>();
        if (prescription != null) {
            StaffAccount doctor = staff.get(prescription.getDoctorAccountId());
            Map<String, Object> slip = new LinkedHashMap<>();
            slip.put("sophieu", prescription.getSophieu());
            slip.put("bacsichidinh", staffName(doctor));
            slip.put("mabacsichidinh", doctor == null ? null : doctor.getPracticeLicenseNo());
            slip.put("chandoansobo", prescription.getChandoansobo());
            slip.put("doituongbn", prescription.getDoituongbn());
            List<Map<String, Object>> lines = new ArrayList<>();
            for (PrescriptionDetail detail : prescription.getDetails()) {
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("machidinh", detail.getMachidinh());
                line.put("cachdung", detail.getCachdung());
                line.put("ngaykedonthuoc", date(detail.getNgaykedonthuoc()));
                line.put("donvitinh", detail.getDonvitinh());
                line.put("nhom", detail.getNhom());
                line.put("loaithuoc", detail.getLoaithuoc());
                line.put("mathuocvattu_bv", detail.getMathuocvattuBv());
                line.put("mathuocvattu_byt", detail.getMathuocvattuByt());
                line.put("soluongthuocvattu", decimal(detail.getSoluongthuocvattu()));
                line.put("sothutungaydungthuoc", detail.getSothutungaydungthuoc());
                line.put("tenthuocvattu", detail.getTenthuocvattu());
                line.put("ylenhthuocvattu_ghichu", detail.getYlenhthuocvattuGhichu());
                lines.add(line);
            }
            slip.put("danhsach", lines);
            list.add(slip);
        }
        values.put("danhsach", list);
        return values;
    }

    /**
     * Xây dựng Nhóm V CV 365: "PhieuChiDinh" (Danh sách phiếu chỉ định cận lâm sàng).
     * Bao gồm: số phiếu chỉ định, bác sĩ chỉ định, nơi chỉ định, mức độ (thường/cấp cứu),
     * tổng tiền, chi tiết từng dịch vụ chỉ định (tên dịch vụ, đơn giá, số lượng, thành tiền, trạng thái, lý do hủy).
     *
     * @param visit Lượt khám
     * @param orders Danh sách các phiếu chỉ định CLS trong hồ sơ
     * @param staff Bản đồ tài khoản nhân viên y tế
     * @return Map chứa danh sách các phiếu chỉ định CLS
     */
    private Map<String, Object> orderGroup(Visit visit, List<ParaclinicalOrder> orders, Map<UUID, StaffAccount> staff) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("sovaovien", visit.getSovaovien());
        List<Map<String, Object>> list = new ArrayList<>();
        for (ParaclinicalOrder order : orders) {
            StaffAccount doctor = staff.get(order.getDoctorAccountId());
            Map<String, Object> slip = new LinkedHashMap<>();
            slip.put("sophieu", order.getSophieu());
            slip.put("bacsichidinh", staffName(doctor));
            slip.put("mabacsichidinh", doctor == null ? null : doctor.getPracticeLicenseNo());
            slip.put("chandoansobo", order.getChandoansobo());
            slip.put("doituongbn", order.getDoituongbn());
            List<Map<String, Object>> lines = new ArrayList<>();
            for (ParaclinicalOrderDetail detail : order.getDetails()) {
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("machidinh", detail.getMachidinh());
                line.put("ngaychidinh", dateTime(detail.getNgaychidinh()));
                line.put("benhnhanphaitra", decimal(detail.getBenhnhanphaitra()));
                line.put("dongia", decimal(detail.getDongia()));
                line.put("loaichidinh", detail.getLoaichidinh());
                line.put("loaimachidinh", detail.getLoaimachidinh());
                line.put("soluong", decimal(detail.getSoluong()));
                line.put("tenchidinh", detail.getTenchidinh());
                line.put("thanhtien", decimal(detail.getThanhtien()));
                // Not CV 365 fields: the outcome of the service, including a cancelled one (BR-25, BR-41).
                line.put("trangthai", detail.getStatus() == null ? null : detail.getStatus().name());
                line.put("lydohuy", detail.getCancelReason());
                lines.add(line);
            }
            slip.put("danhsach", lines);
            slip.put("mucdochidinh", order.getMucdochidinh());
            slip.put("noichidinh", order.getNoichidinh());
            slip.put("tongcong", decimal(order.getTongcong()));
            list.add(slip);
        }
        values.put("danhsach", list);
        return values;
    }

    /**
     * Xây dựng Nhóm VI CV 365: "KetQuaChanDoanHinhAnh" (Kết quả chẩn đoán hình ảnh: Siêu âm, X-quang, CT...).
     * Bao gồm: số phiếu kết quả, mã chỉ định, ngày có kết quả, bác sĩ chỉ định, bác sĩ thực hiện,
     * mô tả hình ảnh, kết luận chuyên môn, đề nghị/lời dặn, và đường dẫn hình ảnh (PACS/Cloud).
     *
     * @param visit Lượt khám
     * @param details Danh sách chi tiết chỉ định CLS
     * @param results Bản đồ kết quả chẩn đoán hình ảnh theo ID chi tiết chỉ định
     * @param staff Bản đồ tài khoản nhân viên y tế
     * @return Map chứa danh sách các kết quả chẩn đoán hình ảnh
     */
    private Map<String, Object> imagingGroup(
            Visit visit, List<ParaclinicalOrderDetail> details, Map<UUID, ImagingResult> results,
            Map<UUID, StaffAccount> staff) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("sovaovien", visit.getSovaovien());
        List<Map<String, Object>> list = new ArrayList<>();
        for (ParaclinicalOrderDetail detail : details) {
            ImagingResult result = results.get(detail.getId());
            if (result == null) {
                continue;
            }
            StaffAccount orderer = staff.get(detail.getOrder().getDoctorAccountId());
            StaffAccount performer = staff.get(result.getPerformerAccountId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sophieu", result.getSophieu());
            item.put("machidinh", detail.getMachidinh());
            item.put("ngayketqua", dateTime(result.getSubmittedAt()));
            item.put("bacsichidinh", staffName(orderer));
            item.put("mabacsichidinh", orderer == null ? null : orderer.getPracticeLicenseNo());
            item.put("bacsithuchien", staffName(performer));
            item.put("mabacsithuchien", performer == null ? null : performer.getPracticeLicenseNo());
            item.put("chandoan", result.getChandoan());
            item.put("kqcls_denghi", result.getKqclsDenghi());
            item.put("kqcls_ketluan", result.getKqclsKetluan());
            item.put("kqcls_linkhinhanh", result.getKqclsLinkhinhanh());
            item.put("kqcls_mota", result.getKqclsMota());
            list.add(item);
        }
        values.put("danhsach", list);
        return values;
    }

    /**
     * Xây dựng Nhóm VII CV 365: "KetQuaXetNghiem" (Kết quả xét nghiệm sinh hóa, huyết học, miễn dịch...).
     * Bao gồm: thông tin phiếu xét nghiệm, người lấy mẫu, người thực hiện, thời điểm lấy mẫu, mã bệnh phẩm,
     * và danh sách chi tiết từng chỉ số xét nghiệm (mã chỉ số, tên xét nghiệm, giá trị kết quả, đơn vị đo,
     * khoảng giá trị tham chiếu chuẩn, cờ đánh dấu kết quả bất thường).
     *
     * @param visit Lượt khám
     * @param details Danh sách chi tiết chỉ định CLS
     * @param results Bản đồ kết quả xét nghiệm theo ID chi tiết chỉ định
     * @param staff Bản đồ tài khoản nhân viên y tế
     * @return Map chứa danh sách các phiếu kết quả xét nghiệm
     */
    private Map<String, Object> labGroup(
            Visit visit, List<ParaclinicalOrderDetail> details, Map<UUID, LabResult> results,
            Map<UUID, StaffAccount> staff) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("sovaovien", visit.getSovaovien());
        List<Map<String, Object>> list = new ArrayList<>();
        for (ParaclinicalOrderDetail detail : details) {
            LabResult result = results.get(detail.getId());
            if (result == null) {
                continue;
            }
            ParaclinicalOrder order = detail.getOrder();
            StaffAccount orderer = staff.get(order.getDoctorAccountId());
            StaffAccount performer = staff.get(result.getPerformerAccountId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sophieu", result.getSophieu());
            item.put("mabacsichidinh", orderer == null ? null : orderer.getPracticeLicenseNo());
            item.put("bacsichidinh", staffName(orderer));
            item.put("chandoan", result.getChandoan());
            item.put("doituongbn", order.getDoituongbn());
            item.put("ghichu", result.getGhichu());
            item.put("giolaymau", dateTime(result.getGiolaymau()));
            item.put("masobenhpham", result.getMasobenhpham());
            item.put("nguoithuchienxetnghiem", staffName(performer));
            item.put("manguoithuchienxetnghiem", performer == null ? null : performer.getPracticeLicenseNo());
            List<Map<String, Object>> lines = new ArrayList<>();
            for (LabResultItem line : result.getItems()) {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("machidinh", detail.getMachidinh());
                value.put("machiso", line.getMachiso());
                value.put("ngayketqua", dateTime(line.getNgayketqua()));
                value.put("donvi", line.getDonvi());
                value.put("ketqua", line.getKetqua());
                value.put("ketquabatthuong", line.getKetquabatthuong());
                value.put("loaixetnghiem", line.getLoaixetnghiem());
                value.put("nhom", line.getNhom());
                value.put("pkqxn_tenxetnghiem", line.getPkqxnTenxetnghiem());
                value.put("trisothamchieu", line.getTrisothamchieu());
                lines.add(value);
            }
            item.put("danhsach", lines);
            list.add(item);
        }
        values.put("danhsach", list);
        return values;
    }

    /**
     * Xây dựng nhóm dữ liệu mở rộng "extensions" đặc thù của phòng khám không nằm trong chuẩn gốc CV 365.
     * Bao gồm:
     * - Khám bộ phận (văn bản tự do) và "Đã xử lý (thuốc, chăm sóc)" theo Mẫu 15/BV1
     * - Dữ liệu liên thông đơn thuốc quốc gia (mã đơn, trạng thái, lịch sử các lần gửi và phản hồi lỗi)
     * - Giấy hẹn tái khám (số giấy, ngày hẹn, mã ICD, trạng thái)
     * - Tỷ lệ mức hưởng bảo hiểm y tế
     *
     * @param closing Ngữ cảnh đóng hồ sơ
     * @param examination Bản ghi khám lâm sàng
     * @param prescription Đơn thuốc
     * @param followUp Giấy hẹn tái khám
     * @return Map chứa dữ liệu mở rộng
     */
    private Map<String, Object> extensions(
            Closing closing, ClinicalExamination examination, Prescription prescription, FollowUpAppointment followUp) {
        Map<String, Object> values = new LinkedHashMap<>();
        // UC-53 BR-53-10: form 15/BV1 IV.2 (free text) and IV.5 "Đã xử lý (thuốc, chăm sóc)".
        values.put("khambenh_bophan", examination == null ? null : examination.getKhambenhBophan());
        values.put("daxuly", examination == null ? null : examination.getTreatmentCarePerformed());
        Map<String, Object> rx = null;
        if (prescription != null) {
            rx = new LinkedHashMap<>();
            rx.put("madonthuoc", prescription.getMadonthuoc());
            rx.put("loaidon", prescription.getLoaidon());
            rx.put("trangthailienthong", prescription.getLienthongStatus() == null
                    ? null : prescription.getLienthongStatus().name());
            rx.put("thoidiemgui", dateTime(prescription.getLienthongSentAt()));
            rx.put("luuy", prescription.getLuuY());
            rx.put("loidan", prescription.getLoiDan());
            rx.put("ngaytaikham", date(prescription.getNgayTaiKham()));
            List<Map<String, Object>> diagnoses = new ArrayList<>();
            prescription.getDiagnoses().forEach(diagnosis -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("maicd", diagnosis.getMaicd());
                item.put("tenicd", diagnosis.getTenicd());
                item.put("thutu", diagnosis.getThuTu());
                diagnoses.add(item);
            });
            rx.put("chandoan", diagnoses);
            List<Map<String, Object>> attempts = new ArrayList<>();
            for (PrescriptionSubmission submission
                    : submissionRepository.findAllByPrescriptionIdOrderByAttemptNoDesc(prescription.getId())) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("lan", submission.getAttemptNo());
                item.put("thoidiemgui", dateTime(submission.getSubmittedAt()));
                item.put("ketqua", submission.getResult() == null ? null : submission.getResult().name());
                item.put("loi", submission.getErrors());
                item.put("thoidiemphanhoi", dateTime(submission.getRespondedAt()));
                attempts.add(0, item);
            }
            rx.put("lichsugui", attempts);
        }
        values.put("donthuoc", rx);
        Map<String, Object> appointment = null;
        if (followUp != null) {
            appointment = new LinkedHashMap<>();
            appointment.put("sogiayhen", followUp.getSoGiayHen());
            appointment.put("ngayhen", date(followUp.getFollowUpDate()));
            appointment.put("loidanbacsi", followUp.getLoidanbacsi());
            appointment.put("maicd", followUp.getMaicd());
            appointment.put("trangthai", followUp.getStatus() == null ? null : followUp.getStatus().name());
        }
        values.put("lichhentaikham", appointment);
        values.put("mucdohuongbhyt", closing.visit().getInsuranceCoverageRate());
        return values;
    }

    /**
     * Thu thập danh sách ID và truy vấn trước toàn bộ các nhân viên y tế tham gia vào đợt khám
     * (bác sĩ đóng HS, bác sĩ khám bệnh, bác sĩ kê đơn, bác sĩ chỉ định, KTV thực hiện kết quả XN/CĐHA)
     * giúp tránh phát sinh truy vấn N+1 vào cơ sở dữ liệu.
     *
     * @param closing Ngữ cảnh đóng hồ sơ
     * @param examination Bản ghi khám bệnh
     * @param orders Danh sách phiếu chỉ định CLS
     * @param imaging Bản đồ kết quả CĐHA
     * @param labs Bản đồ kết quả xét nghiệm
     * @param prescription Đơn thuốc
     * @return Map ánh xạ giữa UUID tài khoản và đối tượng StaffAccount
     */
    private Map<UUID, StaffAccount> staff(
            Closing closing, ClinicalExamination examination, List<ParaclinicalOrder> orders,
            Map<UUID, ImagingResult> imaging, Map<UUID, LabResult> labs, Prescription prescription) {
        List<UUID> ids = new ArrayList<>();
        ids.add(closing.doctor().getId());
        if (examination != null) {
            ids.add(examination.getExaminedByAccountId());
        }
        orders.forEach(order -> ids.add(order.getDoctorAccountId()));
        imaging.values().forEach(result -> ids.add(result.getPerformerAccountId()));
        labs.values().forEach(result -> ids.add(result.getPerformerAccountId()));
        if (prescription != null) {
            ids.add(prescription.getDoctorAccountId());
        }
        Collection<UUID> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        Map<UUID, StaffAccount> staff = new LinkedHashMap<>();
        staffAccountRepository.findAllById(distinct).forEach(account -> staff.put(account.getId(), account));
        staff.put(closing.doctor().getId(), closing.doctor());
        return staff;
    }

    /**
     * Định dạng tên nhân viên y tế kèm học hàm, học vị chuyên môn (VD: "BSCKII Trần Văn An", "ThS.BS Nguyễn Văn A").
     *
     * @param account Tài khoản nhân viên y tế
     * @return Chuỗi họ tên đầy đủ có kèm học hàm, học vị
     */
    static String staffName(StaffAccount account) {
        if (account == null) {
            return null;
        }
        String title = account.getAcademicTitle();
        return title == null || title.isBlank() ? account.getFullName() : title.trim() + " " + account.getFullName();
    }

    /**
     * Định dạng ngày LocalDate thành chuỗi chuẩn CV 365 ("yyyyMMdd").
     *
     * @param value Giá trị ngày
     * @return Chuỗi ngày định dạng yyyyMMdd hoặc null nếu giá trị là null
     */
    private static String date(LocalDate value) {
        return value == null ? null : value.format(DATE);
    }

    /**
     * Định dạng thời điểm Instant thành chuỗi chuẩn CV 365 ("yyyyMMddHHmm") theo múi giờ phòng khám (Asia/Ho_Chi_Minh).
     *
     * @param value Thời điểm Instant
     * @return Chuỗi thời gian yyyyMMddHHmm hoặc null nếu giá trị là null
     */
    private static String dateTime(Instant value) {
        return value == null ? null : DATE_TIME.format(value.atZone(CLINIC_ZONE));
    }

    /**
     * Định dạng giá trị số thực lớn BigDecimal thành chuỗi chuẩn, loại bỏ các chữ số 0 dư thừa phía sau dấu thập phân.
     *
     * @param value Giá trị số thập phân
     * @return Chuỗi số đã loại bỏ số 0 thừa ở đuôi hoặc null nếu giá trị là null
     */
    private static String decimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    /**
     * Tính số tuổi của bệnh nhân tại thời điểm vào viện.
     *
     * @param birthDate Ngày sinh của bệnh nhân
     * @param visitTime Thời điểm bệnh nhân vào viện
     * @return Số năm tuổi (Integer) hoặc null nếu một trong hai tham số null
     */
    private static Integer age(LocalDate birthDate, Instant visitTime) {
        if (birthDate == null || visitTime == null) {
            return null;
        }
        return java.time.Period.between(birthDate, LocalDate.ofInstant(visitTime, CLINIC_ZONE)).getYears();
    }
}
