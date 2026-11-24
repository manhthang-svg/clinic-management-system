package clinic.backend.service.impl;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.config.properties.FollowUpReminderProperties;
import clinic.backend.dto.request.SaveFollowUpAppointmentRequest;
import clinic.backend.entity.ClinicFacility;
import clinic.backend.entity.Conclusion;
import clinic.backend.entity.FollowUpAppointment;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.Patient;
import clinic.backend.entity.Prescription;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.Visit;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.MedicalRecordStatus;
import clinic.backend.enums.PrescriptionSubmissionResult;
import clinic.backend.enums.Role;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitInsuranceVerifyStatus;
import clinic.backend.enums.VisitStatus;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.ClinicFacilityRepository;
import clinic.backend.repository.ConclusionRepository;
import clinic.backend.repository.FollowUpAppointmentRepository;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.PrescriptionRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.AuditLogService;
import clinic.backend.utils.UuidV7Generator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
class FollowUpScheduleServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    /** 10:00 on 03/11/2026 in the clinic. */
    private static final Instant NOW = Instant.parse("2026-11-03T03:00:00Z");
    private static final LocalDate NEXT_MONTH = LocalDate.of(2026, 12, 3);
    private static final UUID DOCTOR_ID = UUID.randomUUID();
    private static final UUID VISIT_ID = UUID.randomUUID();

    @Mock private ConcludingVisitGuard guard;
    @Mock private ClosedRecordPrintGuard printGuard;
    @Mock private FollowUpAppointmentRepository appointmentRepository;
    @Mock private ConclusionRepository conclusionRepository;
    @Mock private PrescriptionRepository prescriptionRepository;
    @Mock private MedicalRecordRepository medicalRecordRepository;
    @Mock private ClinicFacilityRepository clinicFacilityRepository;
    @Mock private StaffAccountRepository staffAccountRepository;
    @Mock private AuditLogService auditLogService;
    private FollowUpScheduleServiceImpl service;
    private ConcludingVisitGuard.Context context;
    private Conclusion conclusion;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZONE);
        service = new FollowUpScheduleServiceImpl(guard, printGuard, appointmentRepository, conclusionRepository,
                prescriptionRepository, medicalRecordRepository, clinicFacilityRepository, staffAccountRepository,
                auditLogService, new UuidV7Generator(clock, new SecureRandom()), clock,
                new FollowUpReminderProperties(true, 1, null));
        Patient patient = new Patient(
                UUID.randomUUID(), NOW, "BN001", null, null, "Nguyễn Văn An", LocalDate.of(1960, 5, 1),
                (short) 1, null, null, "Kinh", "01", "Việt Nam", "VN", "12 Lê Lợi", null, null,
                "Hà Nội", "001", null, null, null, null, null, null, null, null, null, UUID.randomUUID());
        Visit visit = new Visit(
                VISIT_ID, NOW, "DK0000000123", patient.getId(), UUID.randomUUID(), UUID.randomUUID(), NOW,
                VisitDoituongbnLoai.DV, null, null, null, "Tăng huyết áp", false, null,
                LocalDate.ofInstant(NOW, ZONE), 1, VisitStatus.WAITING_CONCLUSION,
                VisitInsuranceVerifyStatus.NOT_APPLICABLE);
        MedicalRecord record = new MedicalRecord(
                UUID.randomUUID(), NOW, VISIT_ID, "BA-DK0001", "NGOAITRU", MedicalRecordStatus.OPEN, NOW, DOCTOR_ID);
        StaffAccount doctor = StaffAccount.create(
                DOCTOR_ID, "doctor01", "hash", "Bác sĩ An", null, null, null, null, null, null, Role.DOCTOR, null, NOW);
        context = new ConcludingVisitGuard.Context(visit, patient, record, doctor);
        conclusion = new Conclusion(UUID.randomUUID(), NOW, record.getId());
        conclusion.record(DOCTOR_ID, "Tăng huyết áp", "I10", "Tăng huyết áp vô căn", "Điều trị ngoại trú",
                (short) 2, null, null, null, NOW);

        lenient().when(guard.require(any(), eq(VISIT_ID))).thenReturn(context);
        lenient().when(printGuard.require(any(), eq(VISIT_ID))).thenReturn(context);
        lenient().when(conclusionRepository.findByMedicalRecordId(record.getId())).thenReturn(Optional.of(conclusion));
        lenient().when(prescriptionRepository.findByMedicalRecordId(record.getId())).thenReturn(Optional.empty());
        lenient().when(appointmentRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        lenient().when(appointmentRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        lenient().when(medicalRecordRepository.findById(record.getId())).thenReturn(Optional.of(record));
        ClinicFacility facility = mock(ClinicFacility.class);
        lenient().when(facility.getTenCskcb()).thenReturn("Phòng khám METPH");
        lenient().when(clinicFacilityRepository.findCurrent()).thenReturn(Optional.of(facility));
        lenient().when(staffAccountRepository.findById(DOCTOR_ID)).thenReturn(Optional.of(doctor));
    }

    @Test
    void schedulesTheVisitAppointmentWithItsSlipNumberAndAuditsIt() {
        when(appointmentRepository.findByMedicalRecordId(context.record().getId())).thenReturn(Optional.empty());

        var form = service.save(principal(), VISIT_ID,
                new SaveFollowUpAppointmentRequest(NEXT_MONTH, "  Đo huyết áp mỗi sáng ", null));

        assertThat(form.appointment().slipNumber()).isEqualTo("GH-DK0000000123");
        assertThat(form.appointment().followUpDate()).isEqualTo(NEXT_MONTH);
        assertThat(form.appointment().advice()).isEqualTo("Đo huyết áp mỗi sáng");
        assertThat(form.appointment().status()).isEqualTo("SCHEDULED");
        assertThat(form.visitDate()).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(form.maxDate()).isEqualTo(LocalDate.of(2027, 11, 3));
        verify(appointmentRepository).save(any(FollowUpAppointment.class));
        verify(auditLogService).recordEntityChange(
                eq(DOCTOR_ID), eq(form.appointment().id()), eq(AuditLogAction.CREATE), eq("follow_up_appointments"),
                eq(Map.of()), eq(Map.of("followUpDate", "2026-12-03", "advice", "Đo huyết áp mỗi sáng",
                        "maicd", "I10", "slipNumber", "GH-DK0000000123")), eq(NOW));
    }

    @Test
    void needsTheSavedConclusion() {
        when(conclusionRepository.findByMedicalRecordId(context.record().getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(principal(), VISIT_ID,
                new SaveFollowUpAppointmentRequest(NEXT_MONTH, null, null)))
                .isInstanceOfSatisfying(AppException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.FOLLOW_UP_CONCLUSION_REQUIRED));
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void refusesAFollowUpDateOnTheVisitDay() {
        assertThatThrownBy(() -> service.save(principal(), VISIT_ID,
                new SaveFollowUpAppointmentRequest(LocalDate.of(2026, 11, 3), null, null)))
                .isInstanceOfSatisfying(AppException.class,
                        error -> assertThat(error.getDetails()).containsEntry("reason", "NOT_AFTER_VISIT"));
    }

    @Test
    void auditsOnlyTheChangedFieldsAndKeepsTheSlipNumber() {
        FollowUpAppointment existing = existing();

        var form = service.save(principal(), VISIT_ID,
                new SaveFollowUpAppointmentRequest(NEXT_MONTH.plusDays(7), "Đo huyết áp mỗi sáng", 1L));

        assertThat(form.appointment().slipNumber()).isEqualTo("GH-DK0000000123");
        assertThat(existing.getFollowUpDate()).isEqualTo(NEXT_MONTH.plusDays(7));
        verify(auditLogService).recordEntityChange(
                eq(DOCTOR_ID), eq(existing.getId()), eq(AuditLogAction.UPDATE), eq("follow_up_appointments"),
                eq(Map.of("followUpDate", "2026-12-03")), eq(Map.of("followUpDate", "2026-12-10")), eq(NOW));
    }

    @Test
    void writesNoAuditRowWhenNothingChanged() {
        existing();

        service.save(principal(), VISIT_ID,
                new SaveFollowUpAppointmentRequest(NEXT_MONTH, "Đo huyết áp mỗi sáng", 1L));

        verify(appointmentRepository, never()).saveAndFlush(any());
        verify(auditLogService, never()).recordEntityChange(any(), any(), any(), any(), anyMap(), anyMap(), any());
    }

    @Test
    void refusesAChangeWithoutTheLoadedVersion() {
        existing();

        assertThatThrownBy(() -> service.save(principal(), VISIT_ID,
                new SaveFollowUpAppointmentRequest(NEXT_MONTH, null, 0L)))
                .isInstanceOfSatisfying(AppException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION));
    }

    @Test
    void refusesToChangeAnAppointmentCancelledByReception() {
        FollowUpAppointment existing = existing();
        existing.cancel("Bệnh nhân chuyển nơi khám", NOW);

        assertThatThrownBy(() -> service.save(principal(), VISIT_ID,
                new SaveFollowUpAppointmentRequest(NEXT_MONTH.plusDays(1), null, 1L)))
                .isInstanceOfSatisfying(AppException.class, error -> assertThat(error.getErrorCode())
                        .isEqualTo(ErrorCode.FOLLOW_UP_APPOINTMENT_NOT_SCHEDULED));
    }

    @Test
    void showsTheDateSentWithTheAcceptedPrescription() {
        when(appointmentRepository.findByMedicalRecordId(context.record().getId())).thenReturn(Optional.empty());
        Prescription prescription = new Prescription(
                UUID.randomUUID(), NOW, "DT-DK0000000123", "79001abc1234-c", context.record().getId());
        prescription.replaceDiagnoses(java.util.List.of(), NEXT_MONTH, NOW);
        prescription.recordSubmission(PrescriptionSubmissionResult.ACCEPTED, NOW);
        when(prescriptionRepository.findByMedicalRecordId(context.record().getId()))
                .thenReturn(Optional.of(prescription));

        var form = service.get(principal(), VISIT_ID);

        assertThat(form.prescriptionAccepted()).isTrue();
        assertThat(form.prescriptionFollowUpDate()).isEqualTo(NEXT_MONTH);
        assertThat(form.appointment()).isNull();
        assertThat(form.diagnoses()).extracting(item -> item.code()).containsExactly("I10");
    }

    @Test
    void printsThePhieuHenKhamLaiAndRecordsTheFirstPrint() {
        FollowUpAppointment appointment = existingById();

        var slip = service.preparePrint(principal(), appointment.getId());

        assertThat(slip.slipNumber()).isEqualTo("GH-DK0000000123");
        assertThat(slip.copy()).isFalse();
        assertThat(slip.facility().name()).isEqualTo("Phòng khám METPH");
        assertThat(slip.patient().gender()).isEqualTo("Nam");
        assertThat(slip.patient().address()).isEqualTo("12 Lê Lợi, Hà Nội");
        assertThat(slip.insurance()).isNull();
        assertThat(slip.visitDate()).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(slip.diagnosis().code()).isEqualTo("I10");
        assertThat(slip.followUpDate()).isEqualTo(NEXT_MONTH);
        assertThat(slip.doctorName()).isEqualTo("Bác sĩ An");
        assertThat(slip.printedBy()).isEqualTo("Bác sĩ An");
        assertThat(appointment.getPrintedAt()).isEqualTo(NOW);
        verify(auditLogService).recordBusinessChange(
                eq(DOCTOR_ID), eq(AuditLogAction.PRINT), eq("follow_up_appointments"), eq(appointment.getId()),
                eq("DK0000000123"), eq(Map.of("slipNumber", "GH-DK0000000123", "reprint", false)), eq(NOW));
    }

    @Test
    void keepsTheFirstPrintTimeOnAReprint() {
        FollowUpAppointment appointment = existingById();
        Instant earlier = NOW.minusSeconds(600);
        appointment.markPrinted(earlier);

        var slip = service.preparePrint(principal(), appointment.getId());

        assertThat(slip.copy()).isTrue();
        assertThat(appointment.getPrintedAt()).isEqualTo(earlier);
        verify(auditLogService).recordBusinessChange(
                any(), eq(AuditLogAction.PRINT), any(), any(), any(),
                eq(Map.of("slipNumber", "GH-DK0000000123", "reprint", true)), any());
    }

    @Test
    void previewsWithoutRecordingAPrint() {
        FollowUpAppointment appointment = existingById();

        var slip = service.printPreview(principal(), appointment.getId());

        assertThat(slip.printedAt()).isNull();
        assertThat(slip.copy()).isFalse();
        assertThat(appointment.getPrintedAt()).isNull();
        verify(auditLogService, never()).recordBusinessChange(any(), any(), any(), any(), any(), anyMap(), any());
    }

    @Test
    void previewsACopyOnceTheSlipWasPrinted() {
        FollowUpAppointment appointment = existingById();
        appointment.markPrinted(NOW.minusSeconds(600));

        assertThat(service.printPreview(principal(), appointment.getId()).copy()).isTrue();
    }

    @Test
    void printsOnlyOnceTheRecordIsClosed() {
        FollowUpAppointment appointment = existingById();
        when(printGuard.require(any(), eq(VISIT_ID))).thenThrow(new AppException(ErrorCode.PRINT_RECORD_NOT_CLOSED));

        assertThatThrownBy(() -> service.preparePrint(principal(), appointment.getId()))
                .isInstanceOfSatisfying(AppException.class, error -> assertThat(error.getErrorCode())
                        .isEqualTo(ErrorCode.PRINT_RECORD_NOT_CLOSED));
        assertThat(appointment.getPrintedAt()).isNull();
        verify(auditLogService, never()).recordBusinessChange(any(), any(), any(), any(), any(), anyMap(), any());
    }

    @Test
    void printsTheInsuranceCardOfAnInsuranceVisit() {
        Visit insured = mock(Visit.class);
        when(insured.getDoituongbnLoai()).thenReturn(VisitDoituongbnLoai.BHYT);
        when(insured.getMabhyt()).thenReturn("HC4010123456789");
        when(insured.getTungaybhyt()).thenReturn(LocalDate.of(2026, 1, 1));
        when(insured.getDenngaybhyt()).thenReturn(LocalDate.of(2026, 12, 31));
        when(insured.getThoigianvaovien()).thenReturn(NOW);
        context = new ConcludingVisitGuard.Context(insured, context.patient(), context.record(), context.doctor());
        when(printGuard.require(any(), eq(VISIT_ID))).thenReturn(context);
        FollowUpAppointment appointment = existingById();

        var slip = service.printPreview(principal(), appointment.getId());

        assertThat(slip.insurance().cardNumber()).isEqualTo("HC4010123456789");
        assertThat(slip.insurance().validFrom()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(slip.insurance().validTo()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void refusesToPrintAnAppointmentThatWasUsed() {
        FollowUpAppointment appointment = existingById();
        appointment.markArrived(UUID.randomUUID(), NOW);

        assertThatThrownBy(() -> service.preparePrint(principal(), appointment.getId()))
                .isInstanceOfSatisfying(AppException.class, error -> assertThat(error.getErrorCode())
                        .isEqualTo(ErrorCode.FOLLOW_UP_APPOINTMENT_NOT_SCHEDULED));
    }

    /** The visit's saved appointment, version 1, on 03/12/2026 with advice. */
    private FollowUpAppointment existing() {
        FollowUpAppointment appointment = FollowUpAppointment.schedule(
                UUID.randomUUID(), NOW, context.record().getId(), "GH-DK0000000123", context.patient().getId(),
                DOCTOR_ID, "I10", NEXT_MONTH, "Đo huyết áp mỗi sáng");
        ReflectionTestUtils.setField(appointment, "version", 1L);
        lenient().when(appointmentRepository.findByMedicalRecordId(context.record().getId()))
                .thenReturn(Optional.of(appointment));
        return appointment;
    }

    private FollowUpAppointment existingById() {
        FollowUpAppointment appointment = existing();
        when(appointmentRepository.findById(appointment.getId())).thenReturn(Optional.of(appointment));
        return appointment;
    }

    private static StaffPrincipal principal() {
        return new StaffPrincipal(DOCTOR_ID, UUID.randomUUID());
    }
}
