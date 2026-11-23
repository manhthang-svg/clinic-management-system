package clinic.backend.service.impl;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.config.properties.ArchiveProperties;
import clinic.backend.dto.request.ConfirmConclusionRequest;
import clinic.backend.entity.Conclusion;
import clinic.backend.entity.DutySchedule;
import clinic.backend.entity.ExportLog;
import clinic.backend.entity.FollowUpAppointment;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.ParaclinicalOrder;
import clinic.backend.entity.ParaclinicalOrderDetail;
import clinic.backend.entity.Patient;
import clinic.backend.entity.Prescription;
import clinic.backend.entity.RecordArchive;
import clinic.backend.entity.RecordSignature;
import clinic.backend.entity.Room;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.StaffRoomAssignment;
import clinic.backend.entity.Visit;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.DutyShift;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.ExportLogPurpose;
import clinic.backend.enums.MedicalRecordStatus;
import clinic.backend.enums.ParaclinicalOrderDetailStatus;
import clinic.backend.enums.PrescriptionLienthongStatus;
import clinic.backend.enums.RecordArchiveRetentionCategory;
import clinic.backend.enums.RecordSignatureDocumentType;
import clinic.backend.enums.RecordSignatureMethod;
import clinic.backend.enums.Role;
import clinic.backend.enums.RoomType;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitInsuranceVerifyStatus;
import clinic.backend.enums.VisitStatus;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.ConclusionRepository;
import clinic.backend.repository.DutyScheduleRepository;
import clinic.backend.repository.ExportLogRepository;
import clinic.backend.repository.FollowUpAppointmentRepository;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.ParaclinicalOrderRepository;
import clinic.backend.repository.PatientRepository;
import clinic.backend.repository.PrescriptionRepository;
import clinic.backend.repository.RecordArchiveRepository;
import clinic.backend.repository.RecordSignatureRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.StaffRoomAssignmentRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.InsuranceCoverageService;
import clinic.backend.utils.UuidV7Generator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicalRecordClosureServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant NOW = Instant.parse("2026-11-17T03:00:00Z");
    private static final UUID DOCTOR_ID = UUID.randomUUID();
    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final UUID VISIT_ID = UUID.randomUUID();
    private static final byte[] PACKAGE = "{\"HoSoBenhAn\":{}}".getBytes(StandardCharsets.UTF_8);

    @Mock private VisitRepository visitRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private StaffAccountRepository staffAccountRepository;
    @Mock private StaffRoomAssignmentRepository assignmentRepository;
    @Mock private DutyScheduleRepository dutyScheduleRepository;
    @Mock private MedicalRecordRepository medicalRecordRepository;
    @Mock private ConclusionRepository conclusionRepository;
    @Mock private ParaclinicalOrderRepository paraclinicalOrderRepository;
    @Mock private PrescriptionRepository prescriptionRepository;
    @Mock private FollowUpAppointmentRepository followUpRepository;
    @Mock private ExportLogRepository exportLogRepository;
    @Mock private RecordArchiveRepository recordArchiveRepository;
    @Mock private RecordSignatureRepository recordSignatureRepository;
    @Mock private InsuranceCoverageService insuranceCoverageService;
    @Mock private ArchivePackageBuilder packageBuilder;
    @Mock private AuditLogService auditLogService;

    @TempDir Path archiveRoot;

    private MedicalRecordClosureServiceImpl service;
    private Visit visit;
    private MedicalRecord record;
    private Conclusion conclusion;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZONE);
        ConcludingVisitGuard guard = new ConcludingVisitGuard(
                visitRepository, patientRepository, roomRepository, staffAccountRepository, assignmentRepository,
                dutyScheduleRepository, new DutyShiftConfigServiceImpl(new ObjectMapper().findAndRegisterModules()),
                medicalRecordRepository, clock);
        service = new MedicalRecordClosureServiceImpl(
                guard, visitRepository, medicalRecordRepository, conclusionRepository, paraclinicalOrderRepository,
                prescriptionRepository, followUpRepository, exportLogRepository, recordArchiveRepository,
                recordSignatureRepository, insuranceCoverageService, packageBuilder,
                new ArchiveStore(new ArchiveProperties(archiveRoot)), auditLogService,
                new UuidV7Generator(clock, new SecureRandom()), clock);
    }

    @Test
    void confirmsTheConclusionClosesLocksAndArchivesTheRecord() throws Exception {
        stubVisit(VisitStatus.WAITING_CONCLUSION, MedicalRecordStatus.OPEN);
        stubConclusion();
        stubOrders(ParaclinicalOrderDetailStatus.COMPLETED, ParaclinicalOrderDetailStatus.CANCELLED);
        stubSaves();

        var response = service.confirm(principal(), VISIT_ID, new ConfirmConclusionRequest(3L, null));

        assertThat(visit.getStatus()).isEqualTo(VisitStatus.COMPLETED);
        assertThat(visit.getCompletedAt()).isEqualTo(NOW);
        assertThat(record.getStatus()).isEqualTo(MedicalRecordStatus.LOCKED);
        assertThat(record.getClosedAt()).isEqualTo(NOW);
        assertThat(record.getClosedByAccountId()).isEqualTo(DOCTOR_ID);
        assertThat(record.getSoluutru()).isEqualTo("LT-DK0001");
        assertThat(conclusion.getNgayravien()).isEqualTo(LocalDate.of(2026, 11, 17));
        verify(insuranceCoverageService).recalculate(visit);

        assertThat(response.visitStatus()).isEqualTo("COMPLETED");
        assertThat(response.archiveNumber()).isEqualTo("LT-DK0001");
        assertThat(response.retentionCategory()).isEqualTo("STANDARD");
        assertThat(response.retentionYears()).isEqualTo((short) 10);
        assertThat(response.expiresOn()).isEqualTo(LocalDate.of(2036, 12, 31));
        // Nothing else to print than the conclusion (UC-57).
        assertThat(response.prescriptionId()).isNull();
        assertThat(response.followUpAppointmentId()).isNull();

        Path file = archiveRoot.resolve("2026/LT-DK0001.json");
        assertThat(Files.readAllBytes(file)).isEqualTo(PACKAGE);
        assertThat(Files.isWritable(file)).isFalse();
        String hash = ArchiveStore.sha256(PACKAGE);

        ArgumentCaptor<ExportLog> exportLog = ArgumentCaptor.forClass(ExportLog.class);
        verify(exportLogRepository).save(exportLog.capture());
        assertThat(exportLog.getValue().getPurpose()).isEqualTo(ExportLogPurpose.LUU_TRU);
        assertThat(exportLog.getValue().getFileUri()).isEqualTo("2026/LT-DK0001.json");
        assertThat(exportLog.getValue().getFileSha256()).isEqualTo(hash);

        ArgumentCaptor<RecordArchive> archive = ArgumentCaptor.forClass(RecordArchive.class);
        verify(recordArchiveRepository).save(archive.capture());
        assertThat(archive.getValue().getPackageSha256()).isEqualTo(hash);
        assertThat(archive.getValue().getRetentionCategory()).isEqualTo(RecordArchiveRetentionCategory.STANDARD);
        assertThat(archive.getValue().getRetentionYears()).isEqualTo((short) 10);
        assertThat(archive.getValue().getExportLogId()).isEqualTo(exportLog.getValue().getId());

        ArgumentCaptor<RecordSignature> signature = ArgumentCaptor.forClass(RecordSignature.class);
        verify(recordSignatureRepository).save(signature.capture());
        assertThat(signature.getValue().getSignatureMethod()).isEqualTo(RecordSignatureMethod.ELECTRONIC_CONFIRMATION);
        assertThat(signature.getValue().getDocumentType()).isEqualTo(RecordSignatureDocumentType.BENH_AN_TONG_HOP);
        assertThat(signature.getValue().getDocumentSha256()).isEqualTo(hash);
        assertThat(signature.getValue().getSignerAccountId()).isEqualTo(DOCTOR_ID);
        assertThat(signature.getValue().getSignerName()).isEqualTo("ThS.BS Bác sĩ An");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Map<String, Object>> oldValues = ArgumentCaptor.forClass(java.util.Map.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Map<String, Object>> newValues = ArgumentCaptor.forClass(java.util.Map.class);
        verify(auditLogService).recordEntityChange(eq(DOCTOR_ID), eq(record.getId()), eq(AuditLogAction.LOCK),
                eq("medical_records"), oldValues.capture(), newValues.capture(), eq(NOW));
        // AuditLogServiceImpl refuses a change whose two sides list different fields.
        assertThat(oldValues.getValue().keySet()).isEqualTo(newValues.getValue().keySet());
        assertThat(oldValues.getValue()).containsEntry("status", "OPEN").containsEntry("soluutru", null);
        assertThat(newValues.getValue()).containsEntry("status", "LOCKED").containsEntry("soluutru", "LT-DK0001");
        verify(auditLogService).recordBusinessChange(eq(DOCTOR_ID), eq(AuditLogAction.EXPORT),
                eq("record_archives"), any(UUID.class), eq("DK0001"), anyMap(), eq(NOW));
    }

    @Test
    void anAccidentRecordIsKeptTwentyYears() {
        stubVisit(VisitStatus.IN_EXAMINATION, MedicalRecordStatus.OPEN);
        stubConclusion();
        stubSaves();

        var response = service.confirm(principal(), VISIT_ID,
                new ConfirmConclusionRequest(3L, "work_accident"));

        assertThat(response.retentionCategory()).isEqualTo("WORK_ACCIDENT");
        assertThat(response.retentionYears()).isEqualTo((short) 20);
        assertThat(response.expiresOn()).isEqualTo(LocalDate.of(2046, 12, 31));
    }

    @Test
    void refusesAnUnknownRetentionCategory() {
        stubVisit(VisitStatus.WAITING_CONCLUSION, MedicalRecordStatus.OPEN);

        assertError(new ConfirmConclusionRequest(3L, "DEATH"), ErrorCode.CLOSE_INVALID);
    }

    @Test
    void refusesWithoutASavedConclusion() {
        stubVisit(VisitStatus.WAITING_CONCLUSION, MedicalRecordStatus.OPEN);
        when(conclusionRepository.findByMedicalRecordId(record.getId())).thenReturn(Optional.empty());

        assertError(new ConfirmConclusionRequest(3L, null), ErrorCode.CLOSE_CONCLUSION_REQUIRED);
    }

    @Test
    void refusesAStaleConclusionVersion() {
        stubVisit(VisitStatus.WAITING_CONCLUSION, MedicalRecordStatus.OPEN);
        stubConclusion();

        assertError(new ConfirmConclusionRequest(2L, null), ErrorCode.CONCURRENT_MODIFICATION);
        assertError(new ConfirmConclusionRequest(null, null), ErrorCode.CONCURRENT_MODIFICATION);
    }

    @Test
    void refusesWhileAServiceHasNoResult() {
        stubVisit(VisitStatus.WAITING_CONCLUSION, MedicalRecordStatus.OPEN);
        stubConclusion();
        stubOrders(ParaclinicalOrderDetailStatus.COMPLETED, ParaclinicalOrderDetailStatus.PENDING_RESULT,
                ParaclinicalOrderDetailStatus.IN_PROGRESS, ParaclinicalOrderDetailStatus.CANCELLED);

        assertThatThrownBy(() -> service.confirm(principal(), VISIT_ID, new ConfirmConclusionRequest(3L, null)))
                .isInstanceOfSatisfying(AppException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CLOSE_PARACLINICAL_RESULTS_PENDING);
                    assertThat(exception.getDetails()).containsEntry("count", "2");
                });
        assertNothingChanged();
    }

    @Test
    void refusesWhileThePrescriptionIsNotAccepted() {
        stubVisit(VisitStatus.WAITING_CONCLUSION, MedicalRecordStatus.OPEN);
        stubConclusion();
        for (PrescriptionLienthongStatus status : List.of(PrescriptionLienthongStatus.PENDING,
                PrescriptionLienthongStatus.SENT, PrescriptionLienthongStatus.REJECTED)) {
            Prescription prescription = mock(Prescription.class);
            when(prescription.getLienthongStatus()).thenReturn(status);
            when(prescriptionRepository.findByMedicalRecordId(record.getId())).thenReturn(Optional.of(prescription));

            assertThatThrownBy(() -> service.confirm(principal(), VISIT_ID, new ConfirmConclusionRequest(3L, null)))
                    .isInstanceOfSatisfying(AppException.class, exception -> {
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PRESCRIPTION_NOT_ACCEPTED);
                        assertThat(exception.getDetails()).containsEntry("lienthongStatus", status.name());
                    });
        }
        assertNothingChanged();
    }

    @Test
    void closesWithAnAcceptedPrescription() {
        stubVisit(VisitStatus.WAITING_CONCLUSION, MedicalRecordStatus.OPEN);
        stubConclusion();
        stubSaves();
        Prescription prescription = mock(Prescription.class);
        when(prescription.getLienthongStatus()).thenReturn(PrescriptionLienthongStatus.ACCEPTED);
        UUID prescriptionId = UUID.randomUUID();
        when(prescription.getId()).thenReturn(prescriptionId);
        when(prescriptionRepository.findByMedicalRecordId(record.getId())).thenReturn(Optional.of(prescription));
        FollowUpAppointment appointment = mock(FollowUpAppointment.class);
        UUID appointmentId = UUID.randomUUID();
        when(appointment.isScheduled()).thenReturn(true);
        when(appointment.getId()).thenReturn(appointmentId);
        when(followUpRepository.findByMedicalRecordId(record.getId())).thenReturn(Optional.of(appointment));

        var response = service.confirm(principal(), VISIT_ID, new ConfirmConclusionRequest(3L, null));

        assertThat(visit.getStatus()).isEqualTo(VisitStatus.COMPLETED);
        // UC-60, UC-62 are printed once the record is closed.
        assertThat(response.prescriptionId()).isEqualTo(prescriptionId);
        assertThat(response.followUpAppointmentId()).isEqualTo(appointmentId);
    }

    @Test
    void refusesAClosedRecord() {
        stubVisit(VisitStatus.WAITING_CONCLUSION, MedicalRecordStatus.LOCKED);

        assertError(new ConfirmConclusionRequest(3L, null), ErrorCode.MEDICAL_RECORD_CLOSED);
    }

    @Test
    void closureCheckReportsWhatBlocks() {
        stubVisit(VisitStatus.WAITING_CONCLUSION, MedicalRecordStatus.OPEN);
        stubConclusion();
        stubOrders(ParaclinicalOrderDetailStatus.PENDING_RESULT);
        Prescription prescription = mock(Prescription.class);
        when(prescription.getLienthongStatus()).thenReturn(PrescriptionLienthongStatus.REJECTED);
        when(prescriptionRepository.findByMedicalRecordId(record.getId())).thenReturn(Optional.of(prescription));

        var check = service.closureCheck(principal(), VISIT_ID);

        assertThat(check.conclusionSaved()).isTrue();
        assertThat(check.conclusionVersion()).isEqualTo(3L);
        assertThat(check.pendingParaclinicalCount()).isEqualTo(1);
        assertThat(check.prescriptionStatus()).isEqualTo("REJECTED");
        assertThat(check.followUpScheduled()).isFalse();
        assertThat(check.canClose()).isFalse();
    }

    @Test
    void closureCheckAllowsAVisitWithNothingPending() {
        stubVisit(VisitStatus.IN_EXAMINATION, MedicalRecordStatus.OPEN);
        stubConclusion();

        var check = service.closureCheck(principal(), VISIT_ID);

        assertThat(check.canClose()).isTrue();
        assertThat(check.prescriptionStatus()).isNull();
    }

    private void assertError(ConfirmConclusionRequest request, ErrorCode code) {
        assertThatThrownBy(() -> service.confirm(principal(), VISIT_ID, request))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
        assertNothingChanged();
    }

    private void assertNothingChanged() {
        verify(medicalRecordRepository, never()).saveAndFlush(any());
        verify(recordArchiveRepository, never()).save(any());
        verify(recordSignatureRepository, never()).save(any());
        verify(insuranceCoverageService, never()).recalculate(any());
        assertThat(archiveRoot.toFile().list()).isEmpty();
    }

    private void stubConclusion() {
        conclusion = new Conclusion(UUID.randomUUID(), NOW, record.getId());
        conclusion.record(DOCTOR_ID, "Viêm họng", "J02.9", "Viêm họng cấp", "Điều trị ngoại trú", (short) 1,
                "Uống nhiều nước", null, null, NOW);
        ReflectionTestUtils.setField(conclusion, "version", 3L);
        when(conclusionRepository.findByMedicalRecordId(record.getId())).thenReturn(Optional.of(conclusion));
    }

    private void stubOrders(ParaclinicalOrderDetailStatus... statuses) {
        List<ParaclinicalOrderDetail> details = java.util.Arrays.stream(statuses).map(status -> {
            ParaclinicalOrderDetail detail = mock(ParaclinicalOrderDetail.class);
            when(detail.getStatus()).thenReturn(status);
            return detail;
        }).toList();
        ParaclinicalOrder order = mock(ParaclinicalOrder.class);
        when(order.getDetails()).thenReturn(details);
        when(paraclinicalOrderRepository.findAllByMedicalRecordId(record.getId())).thenReturn(List.of(order));
    }

    private void stubSaves() {
        when(medicalRecordRepository.saveAndFlush(any(MedicalRecord.class))).thenAnswer(call -> call.getArgument(0));
        when(exportLogRepository.save(any(ExportLog.class))).thenAnswer(call -> call.getArgument(0));
        when(recordArchiveRepository.save(any(RecordArchive.class))).thenAnswer(call -> call.getArgument(0));
        when(recordArchiveRepository.existsBySoluutru(anyString())).thenReturn(false);
        when(packageBuilder.build(any())).thenReturn(PACKAGE);
    }

    private void stubVisit(VisitStatus status, MedicalRecordStatus recordStatus) {
        StaffAccount doctor = StaffAccount.create(
                DOCTOR_ID, "doctor01", "hash", "Bác sĩ An", "ThS.BS", null, null, null, null, null,
                Role.DOCTOR, null, NOW);
        Room room = new Room(ROOM_ID, NOW, UUID.randomUUID(), null, "P101", "Phòng khám 101", "Tầng 1",
                RoomType.EXAMINATION, ActivationStatus.ACTIVE);
        Patient patient = new Patient(
                UUID.randomUUID(), NOW, "BN001", null, null, "Nguyễn Văn An", LocalDate.of(1990, 1, 1),
                (short) 1, null, null, "Kinh", "01", "Việt Nam", "VN", "Hà Nội", null, null,
                "Hà Nội", "001", null, null, null, null, null, null, null, null, null, UUID.randomUUID());
        visit = new Visit(
                VISIT_ID, NOW, "DK0001", patient.getId(), ROOM_ID, UUID.randomUUID(), NOW,
                VisitDoituongbnLoai.DV, null, null, null, "Đau họng", false, null,
                LocalDate.ofInstant(NOW, ZONE), 1, status, VisitInsuranceVerifyStatus.NOT_APPLICABLE);
        ReflectionTestUtils.setField(visit, "doctorAccountId", DOCTOR_ID);
        record = new MedicalRecord(
                UUID.randomUUID(), NOW, VISIT_ID, "BA-DK0001", "NGOAITRU", recordStatus, NOW, DOCTOR_ID);
        ReflectionTestUtils.setField(record, "version", 1L);
        StaffRoomAssignment assignment = new StaffRoomAssignment(
                UUID.randomUUID(), DOCTOR_ID, ROOM_ID, NOW, null, null, null, 0L);
        DutySchedule schedule = new DutySchedule(
                UUID.randomUUID(), NOW, LocalDate.ofInstant(NOW, ZONE), DutyShift.MORNING, DOCTOR_ID, ROOM_ID,
                null, null);

        when(staffAccountRepository.findById(DOCTOR_ID)).thenReturn(Optional.of(doctor));
        when(assignmentRepository.findByStaffAccountIdAndRemovedAtIsNull(DOCTOR_ID)).thenReturn(Optional.of(assignment));
        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
        when(dutyScheduleRepository.findByWorkDateAndShiftAndStaffAccountId(
                LocalDate.ofInstant(NOW, ZONE), DutyShift.MORNING, DOCTOR_ID)).thenReturn(Optional.of(schedule));
        lenient().when(visitRepository.findByIdForUpdate(VISIT_ID)).thenReturn(Optional.of(visit));
        when(visitRepository.findById(VISIT_ID)).thenReturn(Optional.of(visit));
        lenient().when(patientRepository.findById(patient.getId())).thenReturn(Optional.of(patient));
        lenient().when(medicalRecordRepository.findByVisitId(VISIT_ID)).thenReturn(Optional.of(record));
        lenient().when(followUpRepository.findByMedicalRecordId(any())).thenReturn(Optional.empty());
        lenient().when(prescriptionRepository.findByMedicalRecordId(any())).thenReturn(Optional.empty());
        lenient().when(paraclinicalOrderRepository.findAllByMedicalRecordId(any())).thenReturn(List.of());
    }

    private static StaffPrincipal principal() {
        return new StaffPrincipal(DOCTOR_ID, UUID.randomUUID());
    }
}
