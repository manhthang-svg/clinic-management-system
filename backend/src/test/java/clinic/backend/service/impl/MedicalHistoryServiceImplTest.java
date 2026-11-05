package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.dto.request.UpdateMedicalHistoryRequest;
import clinic.backend.dto.request.UpdateMedicalHistoryRequest.AllergyItem;
import clinic.backend.dto.request.UpdateMedicalHistoryRequest.HistoryItem;
import clinic.backend.entity.MedicalHistory;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.StaffRoomAssignment;
import clinic.backend.entity.Visit;
import clinic.backend.enums.AllergySeverity;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.MedicalHistoryCategory;
import clinic.backend.enums.MedicalRecordStatus;
import clinic.backend.enums.VisitStatus;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.MedicalHistoryRepository;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.StaffRoomAssignmentRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.service.AuditLogService;
import clinic.backend.utils.UuidV7Generator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MedicalHistoryServiceImplTest {

    private static final UUID DOCTOR_ID = UUID.randomUUID();
    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final UUID PATIENT_ID = UUID.randomUUID();
    private static final UUID VISIT_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-05T02:00:00.123456Z");

    @Mock VisitRepository visitRepository;
    @Mock MedicalRecordRepository medicalRecordRepository;
    @Mock MedicalHistoryRepository medicalHistoryRepository;
    @Mock StaffRoomAssignmentRepository staffRoomAssignmentRepository;
    @Mock StaffAccountRepository staffAccountRepository;
    @Mock AuditLogService auditLogService;
    @Mock UuidV7Generator ids;

    private MedicalHistoryServiceImpl service;
    private Visit visit;

    @BeforeEach
    void setUp() {
        service = new MedicalHistoryServiceImpl(visitRepository, medicalRecordRepository, medicalHistoryRepository,
                staffRoomAssignmentRepository, staffAccountRepository, auditLogService, ids,
                Clock.fixed(NOW, ZoneOffset.UTC));
        visit = mock(Visit.class);
        when(visit.getId()).thenReturn(VISIT_ID);
        when(visit.getPatientId()).thenReturn(PATIENT_ID);
        when(visit.getRoomId()).thenReturn(ROOM_ID);
        when(visit.getSovaovien()).thenReturn("DK0000000001");
        when(visit.getStatus()).thenReturn(VisitStatus.IN_EXAMINATION);
        when(visitRepository.findById(VISIT_ID)).thenReturn(Optional.of(visit));
        when(medicalRecordRepository.findByVisitId(VISIT_ID)).thenReturn(Optional.empty());
        when(ids.next()).thenAnswer(invocation -> UUID.randomUUID());
        assignDoctorTo(ROOM_ID);
    }

    @Test
    void replacesHistoryAndAllergiesAndRecordsOldAndNewValues() {
        MedicalHistory old = MedicalHistory.drugAllergy(
                UUID.randomUUID(), NOW.minusSeconds(60), PATIENT_ID, null, "Aspirin", null, null, DOCTOR_ID);
        when(medicalHistoryRepository.findByPatientIdOrderByCreatedAtAsc(PATIENT_ID)).thenReturn(List.of(old));

        var response = service.updateForVisit(DOCTOR_ID, VISIT_ID, new UpdateMedicalHistoryRequest(
                List.of(new HistoryItem(MedicalHistoryCategory.SURGERY, "  Mổ ruột thừa 2019 ")),
                List.of(new AllergyItem("Penicillin", " ", AllergySeverity.SEVERE)),
                false, old.getUpdatedAt()));

        assertThat(response.editable()).isTrue();
        assertThat(response.history()).extracting("content").containsExactly("Mổ ruột thừa 2019");
        assertThat(response.allergies()).extracting("drug").containsExactly("Penicillin");
        assertThat(response.allergies().getFirst().reaction()).isNull();
        assertThat(response.updatedAt()).isEqualTo(Instant.parse("2026-11-05T02:00:00.123Z"));
        verify(medicalHistoryRepository).deleteAllInBatch(List.of(old));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, ?>> audit = ArgumentCaptor.forClass(Map.class);
        verify(auditLogService).recordBusinessChange(eq(DOCTOR_ID), eq(AuditLogAction.UPDATE), eq("patients"),
                eq(PATIENT_ID), eq("DK0000000001"), audit.capture(), any());
        assertThat(audit.getValue()).containsKeys("visitId", "old", "new");
    }

    @Test
    void noKnownDrugAllergyIsSavedAsAnAbsentStatement() {
        when(medicalHistoryRepository.findByPatientIdOrderByCreatedAtAsc(PATIENT_ID)).thenReturn(List.of());

        var response = service.updateForVisit(DOCTOR_ID, VISIT_ID,
                new UpdateMedicalHistoryRequest(List.of(), List.of(), true, null));

        assertThat(response.noKnownDrugAllergy()).isTrue();
        assertThat(response.allergies()).isEmpty();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MedicalHistory>> saved = ArgumentCaptor.forClass(List.class);
        verify(medicalHistoryRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).singleElement()
                .satisfies(item -> assertThat(item.isNoKnownDrugAllergy()).isTrue());
    }

    @Test
    void rejectsAllergiesTogetherWithNoKnownDrugAllergy() {
        assertValidationFails(new UpdateMedicalHistoryRequest(
                List.of(), List.of(new AllergyItem("Aspirin", null, null)), true, null), "allergies");
    }

    @Test
    void rejectsMedicalHistoryLongerThan2000Characters() {
        List<HistoryItem> history = java.util.stream.IntStream.range(0, 9)
                .mapToObj(i -> new HistoryItem(MedicalHistoryCategory.OTHER, "x".repeat(255)))
                .toList();
        assertValidationFails(new UpdateMedicalHistoryRequest(history, List.of(), false, null), "history");
    }

    @Test
    void rejectsAStaleLastUpdatedTime() {
        MedicalHistory current = MedicalHistory.personal(UUID.randomUUID(), NOW.minusSeconds(5), PATIENT_ID, null,
                MedicalHistoryCategory.CHRONIC_DISEASE, "Tăng huyết áp", UUID.randomUUID());
        when(medicalHistoryRepository.findByPatientIdOrderByCreatedAtAsc(PATIENT_ID)).thenReturn(List.of(current));

        assertThatThrownBy(() -> service.updateForVisit(DOCTOR_ID, VISIT_ID,
                new UpdateMedicalHistoryRequest(List.of(), List.of(), true, null)))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION));
        verify(medicalHistoryRepository, never()).saveAll(any());
    }

    @Test
    void doctorOfAnotherRoomGetsAReadOnlyPanelAndCannotSave() {
        assignDoctorTo(UUID.randomUUID());
        when(medicalHistoryRepository.findByPatientIdOrderByCreatedAtAsc(PATIENT_ID)).thenReturn(List.of());

        var response = service.getForVisit(DOCTOR_ID, VISIT_ID);

        assertThat(response.editable()).isFalse();
        assertThat(response.readOnlyReason()).isEqualTo("NOT_IN_EXAMINATION");
        assertThatThrownBy(() -> service.updateForVisit(DOCTOR_ID, VISIT_ID,
                new UpdateMedicalHistoryRequest(List.of(), List.of(), true, null)))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
    }

    @Test
    void visitThatIsNotBeingExaminedIsReadOnly() {
        when(visit.getStatus()).thenReturn(VisitStatus.WAITING);
        when(medicalHistoryRepository.findByPatientIdOrderByCreatedAtAsc(PATIENT_ID)).thenReturn(List.of());

        assertThat(service.getForVisit(DOCTOR_ID, VISIT_ID).readOnlyReason()).isEqualTo("NOT_IN_EXAMINATION");
    }

    @Test
    void closedMedicalRecordCannotBeChanged() {
        MedicalRecord record = mock(MedicalRecord.class);
        when(record.getStatus()).thenReturn(MedicalRecordStatus.LOCKED);
        when(medicalRecordRepository.findByVisitId(VISIT_ID)).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.updateForVisit(DOCTOR_ID, VISIT_ID,
                new UpdateMedicalHistoryRequest(List.of(), List.of(), true, null)))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEDICAL_RECORD_CLOSED));
        verify(auditLogService, never()).recordBusinessChange(any(), any(), any(), any(), any(), anyMap(), any());
    }

    @Test
    void unknownVisitIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(visitRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getForVisit(DOCTOR_ID, unknown))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VISIT_NOT_FOUND));
    }

    private void assertValidationFails(UpdateMedicalHistoryRequest request, String field) {
        when(medicalHistoryRepository.findByPatientIdOrderByCreatedAtAsc(PATIENT_ID)).thenReturn(List.of());
        assertThatThrownBy(() -> service.updateForVisit(DOCTOR_ID, VISIT_ID, request))
                .isInstanceOfSatisfying(AppException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REQUEST_VALIDATION_FAILED);
                    assertThat(exception.getDetails()).containsKey(field);
                });
    }

    private void assignDoctorTo(UUID roomId) {
        StaffRoomAssignment assignment = new StaffRoomAssignment(
                UUID.randomUUID(), DOCTOR_ID, roomId, NOW, null, null, null, 0L);
        when(staffRoomAssignmentRepository.findByStaffAccountIdAndRemovedAtIsNull(DOCTOR_ID))
                .thenReturn(Optional.of(assignment));
    }
}
