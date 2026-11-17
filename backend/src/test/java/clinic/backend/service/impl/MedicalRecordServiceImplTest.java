package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.dto.response.MedicalRecordDetailResponse;
import clinic.backend.dto.response.MedicalRecordItemResponse;
import clinic.backend.dto.response.MedicalRecordPatientItemResponse;
import clinic.backend.dto.response.PageResponse;
import clinic.backend.dto.response.PatientVisitItemResponse;
import clinic.backend.entity.ClinicalExamination;
import clinic.backend.entity.Conclusion;
import clinic.backend.entity.FollowUpAppointment;
import clinic.backend.entity.MedicalHistory;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.Patient;
import clinic.backend.entity.Room;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.Visit;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.MedicalRecordStatus;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitStatus;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.ClinicalExaminationRepository;
import clinic.backend.repository.ConclusionRepository;
import clinic.backend.repository.FollowUpAppointmentRepository;
import clinic.backend.repository.MedicalHistoryRepository;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.PatientRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicalRecordServiceImplTest {

    @Mock private MedicalRecordRepository medicalRecordRepository;
    @Mock private VisitRepository visitRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private StaffAccountRepository staffAccountRepository;
    @Mock private ClinicalExaminationRepository clinicalExaminationRepository;
    @Mock private ConclusionRepository conclusionRepository;
    @Mock private FollowUpAppointmentRepository followUpAppointmentRepository;
    @Mock private MedicalHistoryRepository medicalHistoryRepository;
    @Mock private clinic.backend.repository.VitalSignRepository vitalSignRepository;
    @Mock private clinic.backend.repository.VisitTransferRepository visitTransferRepository;
    @Mock private clinic.backend.repository.ParaclinicalOrderRepository paraclinicalOrderRepository;
    @Mock private clinic.backend.repository.ParaclinicalOrderDetailRepository paraclinicalOrderDetailRepository;
    @Mock private clinic.backend.repository.LabResultRepository labResultRepository;
    @Mock private clinic.backend.repository.ImagingResultRepository imagingResultRepository;
    @Mock private clinic.backend.repository.ImagingResultImageRepository imagingResultImageRepository;
    @Mock private clinic.backend.repository.PrescriptionRepository prescriptionRepository;
    @Mock private AuditLogService auditLogService;

    private Clock clock;
    private MedicalRecordServiceImpl service;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-11-17T04:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        service = new MedicalRecordServiceImpl(
                medicalRecordRepository,
                visitRepository,
                patientRepository,
                roomRepository,
                staffAccountRepository,
                clinicalExaminationRepository,
                conclusionRepository,
                followUpAppointmentRepository,
                medicalHistoryRepository,
                vitalSignRepository,
                visitTransferRepository,
                paraclinicalOrderRepository,
                paraclinicalOrderDetailRepository,
                labResultRepository,
                imagingResultRepository,
                imagingResultImageRepository,
                prescriptionRepository,
                auditLogService,
                clock
        );
    }

    @Test
    @DisplayName("searchPatients (Screen #39 Cấp 1): ném lỗi MSG50 khi ngày bắt đầu sau ngày kết thúc")
    void searchPatients_fromAfterTo_throwsMSG50() {
        LocalDate from = LocalDate.of(2026, 11, 20);
        LocalDate to = LocalDate.of(2026, 11, 10);

        assertThatThrownBy(() -> service.searchPatients(from, to, null, null, null, null, 0, 10, null))
                .isInstanceOf(AppException.class)
                .hasMessage("Ngày bắt đầu không được sau ngày kết thúc.")
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REQUEST_PARAMETER_INVALID);
    }

    @Test
    @DisplayName("searchPatients (Screen #39 Cấp 1): trả về danh sách bệnh nhân và ghi audit log")
    void searchPatients_success() {
        UUID patientId = UUID.randomUUID();
        UUID doctorAccountId = UUID.randomUUID();

        MedicalRecordRepository.PatientMedicalRecordSummaryRow row = new MedicalRecordRepository.PatientMedicalRecordSummaryRow() {
            @Override public UUID getPatientId() { return patientId; }
            @Override public String getPatientCode() { return "BN00000123"; }
            @Override public String getFullName() { return "Nguyễn Văn A"; }
            @Override public LocalDate getBirthDate() { return LocalDate.of(1990, 5, 20); }
            @Override public Short getGender() { return 1; }
            @Override public String getCitizenId() { return "012345678901"; }
            @Override public String getPhoneNumber() { return "0901234567"; }
            @Override public Long getTotalVisits() { return 3L; }
            @Override public LocalDate getLastVisitDate() { return LocalDate.of(2026, 11, 15); }
            @Override public String getLastDiagnosisIcd() { return "J00"; }
            @Override public String getLastDiagnosisName() { return "Viêm mũi họng cấp"; }
        };

        Page<MedicalRecordRepository.PatientMedicalRecordSummaryRow> pageResult = new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1);
        when(medicalRecordRepository.searchPatientsSummary(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(pageResult);

        PageResponse<MedicalRecordPatientItemResponse> response = service.searchPatients(
                null, null, "COMPLETED", null, null, "Nguyễn", 0, 20, doctorAccountId);

        assertThat(response.content()).hasSize(1);
        MedicalRecordPatientItemResponse item = response.content().getFirst();
        assertThat(item.patientCode()).isEqualTo("BN00000123");
        assertThat(item.fullName()).isEqualTo("Nguyễn Văn A");
        assertThat(item.gender()).isEqualTo("Nam");
        assertThat(item.totalVisits()).isEqualTo(3L);
        assertThat(item.lastDiagnosisIcd()).isEqualTo("J00");
        assertThat(item.lastDiagnosisName()).isEqualTo("Viêm mũi họng cấp");

        verify(auditLogService).recordBusinessChange(
                eq(doctorAccountId),
                eq(AuditLogAction.VIEW_RECORD),
                eq("PATIENT_LIST"),
                eq(doctorAccountId),
                eq("PATIENT_SUMMARY"),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("getPatientVisits (Screen #39 Cấp 2): trả về danh sách lượt khám và ghi audit log")
    void getPatientVisits_success() {
        UUID patientId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        UUID medicalRecordId = UUID.randomUUID();
        UUID doctorAccountId = UUID.randomUUID();
        Instant now = Instant.now();

        MedicalRecordRepository.PatientVisitRow row = new MedicalRecordRepository.PatientVisitRow() {
            @Override public UUID getVisitId() { return visitId; }
            @Override public UUID getMedicalRecordId() { return medicalRecordId; }
            @Override public String getRegistrationCode() { return "DK0000000456"; }
            @Override public Instant getRegisteredAt() { return now; }
            @Override public String getRoomName() { return "Phòng Khám Nội 1"; }
            @Override public String getAcademicTitle() { return "BS.CKI"; }
            @Override public String getDoctorName() { return "Trần Văn B"; }
            @Override public VisitDoituongbnLoai getDoituongbnLoai() { return VisitDoituongbnLoai.BHYT; }
            @Override public String getFinalDiagnosisIcd() { return "I10"; }
            @Override public String getFinalDiagnosisName() { return "Tăng huyết áp vô căn"; }
            @Override public VisitStatus getVisitStatus() { return VisitStatus.COMPLETED; }
        };

        Patient patient = org.mockito.Mockito.mock(Patient.class);
        when(patient.getMabenhnhan()).thenReturn("BN00000123");
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));
        when(medicalRecordRepository.findVisitsByPatientId(patientId)).thenReturn(List.of(row));

        List<PatientVisitItemResponse> result = service.getPatientVisits(patientId, doctorAccountId);

        assertThat(result).hasSize(1);
        PatientVisitItemResponse item = result.getFirst();
        assertThat(item.registrationCode()).isEqualTo("DK0000000456");
        assertThat(item.roomName()).isEqualTo("Phòng Khám Nội 1");
        assertThat(item.doctorName()).isEqualTo("BS.CKI Trần Văn B");
        assertThat(item.patientType()).isEqualTo("BHYT");
        assertThat(item.finalDiagnosisIcd()).isEqualTo("I10");
        assertThat(item.finalDiagnosisName()).isEqualTo("Tăng huyết áp vô căn");
        assertThat(item.status()).isEqualTo("COMPLETED");
        assertThat(item.statusLabel()).isEqualTo("Hoàn thành");

        verify(auditLogService).recordBusinessChange(
                eq(doctorAccountId),
                eq(AuditLogAction.VIEW_RECORD),
                eq("PATIENT"),
                eq(patientId),
                eq("BN00000123"),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("getMedicalRecords: ném lỗi khi ngày bắt đầu sau ngày kết thúc")
    void getMedicalRecords_fromAfterTo_throwsException() {
        LocalDate from = LocalDate.of(2026, 11, 20);
        LocalDate to = LocalDate.of(2026, 11, 10);

        assertThatThrownBy(() -> service.getMedicalRecords(from, to, null, null, null, 0, 10))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REQUEST_PARAMETER_INVALID);
    }
}
