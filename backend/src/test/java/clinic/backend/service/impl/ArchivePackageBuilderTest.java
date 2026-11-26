package clinic.backend.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import clinic.backend.entity.Conclusion;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.Patient;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.Visit;
import clinic.backend.enums.MedicalRecordStatus;
import clinic.backend.enums.RecordArchiveRetentionCategory;
import clinic.backend.enums.Role;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitInsuranceVerifyStatus;
import clinic.backend.enums.VisitStatus;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ArchivePackageBuilderTest {

    private static final Instant ADMITTED = Instant.parse("2026-11-17T01:05:00Z");
    private static final Instant CLOSED = Instant.parse("2026-11-17T03:30:00Z");

    @Mock private ClinicFacilityRepository facilityRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private StaffAccountRepository staffAccountRepository;
    @Mock private MedicalHistoryRepository medicalHistoryRepository;
    @Mock private ClinicalExaminationRepository examinationRepository;
    @Mock private VitalSignRepository vitalSignRepository;
    @Mock private ParaclinicalOrderRepository paraclinicalOrderRepository;
    @Mock private ImagingResultRepository imagingResultRepository;
    @Mock private LabResultRepository labResultRepository;
    @Mock private PrescriptionRepository prescriptionRepository;
    @Mock private PrescriptionSubmissionRepository submissionRepository;
    @Mock private FollowUpAppointmentRepository followUpRepository;

    private ArchivePackageBuilder builder;
    private ArchivePackageBuilder.Closing closing;

    @BeforeEach
    void setUp() {
        builder = new ArchivePackageBuilder(facilityRepository, roomRepository, departmentRepository,
                staffAccountRepository, medicalHistoryRepository, examinationRepository, vitalSignRepository,
                paraclinicalOrderRepository, imagingResultRepository, labResultRepository, prescriptionRepository,
                submissionRepository, followUpRepository);
        UUID doctorId = UUID.randomUUID();
        StaffAccount doctor = StaffAccount.create(doctorId, "doctor01", "hash", "Bác sĩ An", "ThS.BS", null, null,
                null, null, null, Role.DOCTOR, null, ADMITTED);
        Patient patient = new Patient(
                UUID.randomUUID(), ADMITTED, "BN00000001", null, null, "Nguyễn Văn An", LocalDate.of(1990, 1, 1),
                (short) 1, null, null, "Kinh", "01", "Việt Nam", "VN", "Hà Nội", null, null,
                "Hà Nội", "001", null, null, null, null, null, null, null, null, null, UUID.randomUUID());
        Visit visit = new Visit(
                UUID.randomUUID(), ADMITTED, "DK0000000001", patient.getId(), UUID.randomUUID(), UUID.randomUUID(),
                ADMITTED, VisitDoituongbnLoai.DV, null, null, null, "Đau họng", false, null,
                LocalDate.of(2026, 11, 17), 1, VisitStatus.COMPLETED, VisitInsuranceVerifyStatus.NOT_APPLICABLE);
        MedicalRecord record = new MedicalRecord(UUID.randomUUID(), ADMITTED, visit.getId(), "BA-DK0000000001",
                "NGOAITRU", MedicalRecordStatus.LOCKED, ADMITTED, doctorId);
        Conclusion conclusion = new Conclusion(UUID.randomUUID(), ADMITTED, record.getId());
        conclusion.record(doctorId, "Viêm họng cấp", "J02.9", "Viêm họng cấp, không đặc hiệu", "Điều trị ngoại trú",
                "Khỏi", "Uống nhiều nước", null, null, CLOSED);
        conclusion.discharge(LocalDate.of(2026, 11, 17), CLOSED);
        closing = new ArchivePackageBuilder.Closing(visit, patient, record, conclusion, doctor, "LT-DK0000000001",
                CLOSED, RecordArchiveRetentionCategory.STANDARD, LocalDate.of(2036, 12, 31));
    }

    @Test
    void writesTheRecordInTheCv365StructureWithItsMetadata() throws Exception {
        JsonNode root = new ObjectMapper().readTree(builder.build(closing));

        assertThat(root.fieldNames()).toIterable().containsExactly("DuLieuChu", "HoSoBenhAn", "extensions");
        JsonNode metadata = root.get("DuLieuChu");
        assertThat(metadata.get("phienbanchuan").asText()).isEqualTo("CV365/2025");
        assertThat(metadata.get("soluutru").asText()).isEqualTo("LT-DK0000000001");
        assertThat(metadata.get("nguoidonghoso").asText()).isEqualTo("ThS.BS Bác sĩ An");
        assertThat(metadata.get("thoidiemdonghoso").asText()).isEqualTo("202611171030");
        assertThat(metadata.get("thoihanluutru_nam").asInt()).isEqualTo(10);
        assertThat(metadata.get("hanluutru").asText()).isEqualTo("20361231");

        JsonNode record = root.get("HoSoBenhAn");
        assertThat(record.fieldNames()).toIterable().containsExactly("ThongTinBenhNhan", "ThongTinVaoVien",
                "ThongTinDieuTri", "YLenhThuocVatTu", "PhieuChiDinh", "KetQuaChanDoanHinhAnh", "KetQuaXetNghiem");
        JsonNode patient = record.get("ThongTinBenhNhan");
        assertThat(patient.get("sovaovien").asText()).isEqualTo("DK0000000001");
        assertThat(patient.get("soluutru").asText()).isEqualTo("LT-DK0000000001");
        assertThat(patient.get("hoten").asText()).isEqualTo("NGUYỄN VĂN AN");
        assertThat(patient.get("ngaysinh").asText()).isEqualTo("19900101");
        assertThat(patient.get("tuoi").asInt()).isEqualTo(36);
        assertThat(patient.get("gioitinh").asInt()).isEqualTo(1);
        assertThat(patient.get("thoigianvaovien").asText()).isEqualTo("202611170805");
        assertThat(patient.get("doituongbn_loai").asText()).isEqualTo("DV");

        JsonNode treatment = record.get("ThongTinDieuTri");
        assertThat(treatment.get("maicd").asText()).isEqualTo("J02.9");
        assertThat(treatment.get("ngayravien").asText()).isEqualTo("20261117");
        assertThat(treatment.get("tinhtrangravien").get("loidanbacsi").asText()).isEqualTo("Uống nhiều nước");
        assertThat(record.get("YLenhThuocVatTu").get("danhsach")).isEmpty();
        assertThat(root.get("extensions").get("donthuoc").isNull()).isTrue();
        // UC-53 BR-53-10: the keys exist even when the examination has no value.
        assertThat(root.get("extensions").has("khambenh_bophan")).isTrue();
        assertThat(root.get("extensions").has("daxuly")).isTrue();
    }

    @Test
    void form15Bv1RendersFromTheBuiltPackage() throws Exception {
        JsonNode root = new ObjectMapper().readTree(builder.build(closing));

        String html = new Form15Bv1Renderer().html(root, new Form15Bv1Renderer.Stamp(
                "Sở Y tế Hà Nội", "ThS.BS Bác sĩ An", CLOSED));

        assertThat(html).contains("NGUYỄN VĂN AN", "Viêm họng cấp",
                "Đã xác nhận điện tử lúc 10:30 17/11/2026", "Số lưu trữ: LT-DK0000000001",
                "Uống nhiều nước", "Không chỉ định cận lâm sàng.");
        assertThat(html).doesNotContain(Form15Bv1Renderer.NOT_IN_PACKAGE);
    }

    @Test
    void theSameRecordAlwaysGivesTheSameBytes() {
        assertThat(builder.build(closing)).isEqualTo(builder.build(closing));
    }
}
