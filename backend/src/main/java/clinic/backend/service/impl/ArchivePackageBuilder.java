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
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * UC-58 BR-58-06 archive package: the whole medical record at closure (Luật KBCB 2023 Điều 2 khoản 17) in the
 * exchange structure of the CV 365 Phụ lục "Mô tả dữ liệu trao đổi hồ sơ bệnh án điện tử", as JSON.
 *
 * <p>Decision A (17/11/2026): only the CV 365 fields the clinic stores are written; the inpatient and surgery
 * groups (VIII to XI, beds, transfers between departments) are left out. Data without a CV 365 field goes in
 * {@code extensions}; the metadata ("dữ liệu chủ", Luật Lưu trữ 2024 Điều 33 khoản 2) goes in {@code DuLieuChu}.
 * Values follow BR-54: sex 1/2/3, times yyyyMMddHHmm and dates yyyyMMdd in Asia/Ho_Chi_Minh, the patient's name
 * in capitals. Maps keep insertion order so the same record always gives the same bytes and hash.
 */
@Component
@RequiredArgsConstructor
public class ArchivePackageBuilder {

    static final String SPEC_VERSION = "CV365/2025";
    static final String PACKAGE_FORMAT = "UC58-ARCHIVE/1";

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final ObjectMapper JSON = new ObjectMapper();

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

    /** What the closing transaction knows that the database rows do not hold yet. */
    public record Closing(
            Visit visit, Patient patient, MedicalRecord record, Conclusion conclusion, StaffAccount doctor,
            String soluutru, Instant closedAt, RecordArchiveRetentionCategory retentionCategory,
            LocalDate expiresOn) {
    }

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

    public byte[] build(Closing closing) {
        try {
            return JSON.writeValueAsBytes(buildPayload(closing));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize the archive package", exception);
        }
    }

    public byte[] buildXml(Closing closing) {
        return clinic.backend.utils.Cv365XmlSerializer.serialize(buildPayload(closing));
    }

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

    /** CV 365 group I. */
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

    /** CV 365 group II. */
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

    /** CV 365 group III. */
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
        values.put("chandoan_ma", conclusion == null ? null : conclusion.getChandoanMa());
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

    /** CV 365 group IV: the prescription; the clinic sells no medicine (BR-33). */
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

    /** CV 365 group V. */
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

    /** CV 365 group VI. */
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

    /** CV 365 group VII. */
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

    /** Data the clinic keeps that has no CV 365 field. */
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

    /** The academic title and name, e.g. "ThS.BS Nguyễn Văn A". */
    static String staffName(StaffAccount account) {
        if (account == null) {
            return null;
        }
        String title = account.getAcademicTitle();
        return title == null || title.isBlank() ? account.getFullName() : title.trim() + " " + account.getFullName();
    }

    private static String date(LocalDate value) {
        return value == null ? null : value.format(DATE);
    }

    private static String dateTime(Instant value) {
        return value == null ? null : DATE_TIME.format(value.atZone(CLINIC_ZONE));
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private static Integer age(LocalDate birthDate, Instant visitTime) {
        if (birthDate == null || visitTime == null) {
            return null;
        }
        return java.time.Period.between(birthDate, LocalDate.ofInstant(visitTime, CLINIC_ZONE)).getYears();
    }
}
