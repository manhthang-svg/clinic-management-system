package clinic.backend.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.dto.response.PrescriptionResponse;
import clinic.backend.entity.ClinicFacility;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.MedicineCatalog;
import clinic.backend.entity.Patient;
import clinic.backend.entity.Prescription;
import clinic.backend.entity.PrescriptionDetail;
import clinic.backend.entity.PrescriptionDiagnosis;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.Visit;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.MedicalRecordStatus;
import clinic.backend.enums.MedicineControlGroup;
import clinic.backend.enums.PrescriptionSubmissionResult;
import clinic.backend.enums.Role;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitInsuranceVerifyStatus;
import clinic.backend.enums.VisitStatus;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.ClinicFacilityRepository;
import clinic.backend.repository.PrescriptionRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrescriptionPrintServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant NOW = Instant.parse("2026-11-03T03:00:00Z");
    private static final Instant EARLIER = NOW.minusSeconds(3_600);
    private static final UUID DOCTOR_ID = UUID.randomUUID();
    private static final UUID VISIT_ID = UUID.randomUUID();

    @Mock private ClosedRecordPrintGuard guard;
    @Mock private PrescriptionServiceImpl prescriptions;
    @Mock private PrescriptionRepository prescriptionRepository;
    @Mock private ClinicFacilityRepository clinicFacilityRepository;
    @Mock private StaffAccountRepository staffAccountRepository;
    @Mock private AuditLogService auditLogService;
    private PrescriptionPrintServiceImpl service;
    private ConcludingVisitGuard.Context context;
    private Prescription prescription;
    private final PrescriptionResponse.Submission acceptedSend = new PrescriptionResponse.Submission(
            (short) 2, "ACCEPTED", List.of(), NOW, NOW, NOW, "Bác sĩ An", "02", "CN=CA giả lập", "Bác sĩ An", true,
            null);

    @BeforeEach
    void setUp() {
        service = new PrescriptionPrintServiceImpl(guard, prescriptions, prescriptionRepository,
                clinicFacilityRepository, staffAccountRepository, auditLogService, Clock.fixed(NOW, ZONE));
        Patient patient = new Patient(
                UUID.randomUUID(), NOW, "BN001", null, null, "Nguyễn Văn An", LocalDate.of(1990, 1, 1),
                (short) 1, null, null, "Kinh", "01", "Việt Nam", "VN", "Hà Nội", null, null,
                "Hà Nội", "001", null, null, null, null, null, null, null, null, null, UUID.randomUUID());
        Visit visit = new Visit(
                VISIT_ID, NOW, "DK0001", patient.getId(), UUID.randomUUID(), UUID.randomUUID(), NOW,
                VisitDoituongbnLoai.DV, null, null, null, "Đau họng", false, null,
                LocalDate.ofInstant(NOW, ZONE), 1, VisitStatus.WAITING_CONCLUSION,
                VisitInsuranceVerifyStatus.NOT_APPLICABLE);
        MedicalRecord record = new MedicalRecord(
                UUID.randomUUID(), NOW, VISIT_ID, "BA-DK0001", "NGOAITRU", MedicalRecordStatus.LOCKED, NOW, DOCTOR_ID);
        StaffAccount doctor = StaffAccount.create(
                DOCTOR_ID, "doctor01", "hash", "Bác sĩ An", null, null, null, null, null, null, Role.DOCTOR, null, NOW);
        context = new ConcludingVisitGuard.Context(visit, patient, record, doctor);

        prescription = new Prescription(UUID.randomUUID(), EARLIER, "DT-DK0001", "79001abc1234-c", record.getId());
        prescription.record(DOCTOR_ID, "Viêm họng cấp", (short) 2, "Tái khám nếu sốt", "Uống nhiều nước", EARLIER);
        MedicineCatalog amoxicillin = new MedicineCatalog(UUID.randomUUID(), NOW, new MedicineCatalog.CatalogEntry(
                "AMO500", "VD-AMO500", null, "Amoxicilin 500mg", "Amoxicilin", "500mg", "Uống", "Viên",
                MedicineControlGroup.THUONG));
        prescription.replaceDetails(List.of(new PrescriptionDetail(
                UUID.randomUUID(), NOW, prescription, "DT-DK0001-01", amoxicillin, new BigDecimal("21.00"), "1 viên",
                (short) 3, (short) 7, "Uống sau ăn", null, LocalDate.ofInstant(NOW, ZONE))));

        when(prescriptionRepository.findById(prescription.getId())).thenReturn(Optional.of(prescription));
        when(prescriptions.visitId(prescription)).thenReturn(VISIT_ID);
        lenient().when(guard.require(any(), eq(VISIT_ID))).thenReturn(context);
        ClinicFacility facility = mock(ClinicFacility.class);
        lenient().when(facility.getTenCskcb()).thenReturn("Phòng khám METPH");
        lenient().when(clinicFacilityRepository.findCurrent()).thenReturn(Optional.of(facility));
        lenient().when(staffAccountRepository.findById(DOCTOR_ID)).thenReturn(Optional.of(doctor));
        lenient().when(prescriptions.lastSubmission(prescription)).thenReturn(acceptedSend);
    }

    @Test
    void printsTheAcceptedPrescriptionWithTheSentDiagnosesAndTheVerifiedSignature() {
        accept();

        var form = service.preparePrint(principal(), prescription.getId());

        assertThat(form.prescriptionCode()).isEqualTo("79001abc1234-c");
        assertThat(form.copy()).isFalse();
        assertThat(form.facility().name()).isEqualTo("Phòng khám METPH");
        assertThat(form.patient().fullName()).isEqualTo("Nguyễn Văn An");
        assertThat(form.patient().gender()).isEqualTo("Nam");
        assertThat(form.diagnoses()).extracting(item -> item.code()).containsExactly("J02.9");
        assertThat(form.followUpDate()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(form.lines()).singleElement().satisfies(line -> {
            assertThat(line.activeIngredient()).isEqualTo("Amoxicilin");
            assertThat(line.name()).isEqualTo("Amoxicilin 500mg");
            assertThat(line.quantity()).isEqualByComparingTo("21");
        });
        assertThat(form.signature()).isEqualTo(acceptedSend);
        assertThat(form.doctorName()).isEqualTo("Bác sĩ An");
        assertThat(form.printedAt()).isEqualTo(NOW);
        assertThat(form.printedBy()).isEqualTo("Bác sĩ An");
        assertThat(prescription.getPrintedAt()).isEqualTo(NOW);
        verify(prescriptionRepository).save(prescription);
        verify(auditLogService).recordBusinessChange(
                eq(DOCTOR_ID), eq(AuditLogAction.PRINT), eq("prescriptions"), eq(prescription.getId()), eq("DK0001"),
                eq(Map.of("prescriptionCode", "79001abc1234-c", "reprint", false)), eq(NOW));
    }

    @Test
    void keepsTheFirstPrintTimeAndAuditsAReprint() {
        accept();
        prescription.markPrinted(EARLIER);

        var form = service.preparePrint(principal(), prescription.getId());

        assertThat(form.copy()).isTrue();
        assertThat(prescription.getPrintedAt()).isEqualTo(EARLIER);
        verify(prescriptionRepository, never()).save(any());
        verify(auditLogService).recordBusinessChange(
                eq(DOCTOR_ID), eq(AuditLogAction.PRINT), eq("prescriptions"), eq(prescription.getId()), eq("DK0001"),
                eq(Map.of("prescriptionCode", "79001abc1234-c", "reprint", true)), eq(NOW));
    }

    @Test
    void previewsWithoutRecordingAPrint() {
        accept();

        var form = service.printPreview(principal(), prescription.getId());

        assertThat(form.printedAt()).isNull();
        assertThat(form.printedBy()).isNull();
        assertThat(form.copy()).isFalse();
        assertThat(prescription.getPrintedAt()).isNull();
        verify(auditLogService, never()).recordBusinessChange(any(), any(), any(), any(), any(), anyMap(), any());
    }

    @Test
    void previewsACopyOnceThePrescriptionWasPrinted() {
        accept();
        prescription.markPrinted(EARLIER);

        assertThat(service.printPreview(principal(), prescription.getId()).copy()).isTrue();
    }

    @Test
    void printsOnlyOnceTheRecordIsClosed() {
        accept();
        when(guard.require(any(), eq(VISIT_ID))).thenThrow(new AppException(ErrorCode.PRINT_RECORD_NOT_CLOSED));

        assertThatThrownBy(() -> service.preparePrint(principal(), prescription.getId()))
                .isInstanceOfSatisfying(AppException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PRINT_RECORD_NOT_CLOSED));
        assertThat(prescription.getPrintedAt()).isNull();
        verify(auditLogService, never()).recordBusinessChange(any(), any(), any(), any(), any(), anyMap(), any());
    }

    @Test
    void refusesToPrintAPrescriptionThatWasNotAccepted() {
        prescription.recordSubmission(PrescriptionSubmissionResult.REJECTED, NOW);

        assertThatThrownBy(() -> service.preparePrint(principal(), prescription.getId()))
                .isInstanceOfSatisfying(AppException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PRESCRIPTION_NOT_PRINTABLE));
        assertThat(prescription.getPrintedAt()).isNull();
        verify(auditLogService, never()).recordBusinessChange(any(), any(), any(), any(), any(), anyMap(), any());
    }

    /** Accepted with the diagnoses and follow-up date sent with it. */
    private void accept() {
        prescription.replaceDiagnoses(List.of(new PrescriptionDiagnosis(
                UUID.randomUUID(), NOW, prescription, "J02.9", "Viêm họng cấp", (short) 1)),
                LocalDate.of(2026, 11, 10), NOW);
        prescription.recordSubmission(PrescriptionSubmissionResult.ACCEPTED, NOW);
    }

    private static StaffPrincipal principal() {
        return new StaffPrincipal(DOCTOR_ID, UUID.randomUUID());
    }
}
