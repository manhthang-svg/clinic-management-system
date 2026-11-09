package clinic.backend.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.dto.request.ReturnParaclinicalOrderToWaitingRequest;
import clinic.backend.entity.ParaclinicalOrder;
import clinic.backend.entity.ParaclinicalOrderDetail;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.StaffRoomAssignment;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.ParaclinicalOrderDetailStatus;
import clinic.backend.enums.ParaclinicalOrderStatus;
import clinic.backend.enums.Role;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.ClinicalExaminationRepository;
import clinic.backend.repository.DutyScheduleRepository;
import clinic.backend.repository.MedicalHistoryRepository;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.MedicalServiceRepository;
import clinic.backend.repository.ParaclinicalOrderDetailRepository;
import clinic.backend.repository.PatientRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.StaffRoomAssignmentRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.service.AuditLogService;
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

/** UC-66 Perform Paraclinical Order: confirm performed and return to waiting (AF-02). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TechnicianQueuePerformOrderTest {

    private static final Instant ACCEPTED = Instant.parse("2026-11-09T01:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-09T01:20:00Z");
    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final UUID PERFORMER_ID = UUID.randomUUID();
    private static final UUID COLLEAGUE_ID = UUID.randomUUID();

    @Mock RoomRepository roomRepository;
    @Mock StaffAccountRepository staffAccountRepository;
    @Mock StaffRoomAssignmentRepository staffRoomAssignmentRepository;
    @Mock ParaclinicalOrderDetailRepository paraclinicalOrderDetailRepository;
    @Mock MedicalRecordRepository medicalRecordRepository;
    @Mock VisitRepository visitRepository;
    @Mock PatientRepository patientRepository;
    @Mock ClinicalExaminationRepository clinicalExaminationRepository;
    @Mock MedicalHistoryRepository medicalHistoryRepository;
    @Mock MedicalServiceRepository medicalServiceRepository;
    @Mock DutyScheduleRepository dutyScheduleRepository;
    @Mock AuditLogService auditLogService;

    private TechnicianQueueServiceImpl service;
    private ParaclinicalOrderDetail detail;

    @BeforeEach
    void setUp() {
        service = new TechnicianQueueServiceImpl(
                roomRepository, staffAccountRepository, staffRoomAssignmentRepository,
                paraclinicalOrderDetailRepository, medicalRecordRepository, visitRepository, patientRepository,
                clinicalExaminationRepository, medicalHistoryRepository, medicalServiceRepository,
                dutyScheduleRepository, auditLogService, Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh")));
        detail = acceptedDetail();
        when(paraclinicalOrderDetailRepository.findById(detail.getId())).thenReturn(Optional.of(detail));
        assignToRoom(PERFORMER_ID);
        assignToRoom(COLLEAGUE_ID);
    }

    @Test
    void performerConfirmsThatTheServiceWasPerformed() {
        service.completeOrder(detail.getId(), PERFORMER_ID);

        assertThat(detail.getStatus()).isEqualTo(ParaclinicalOrderDetailStatus.PENDING_RESULT);
        assertThat(detail.getPerformedAt()).isEqualTo(NOW);
        assertThat(detail.getPerformerAccountId()).isEqualTo(PERFORMER_ID);
        verify(paraclinicalOrderDetailRepository).save(detail);
        verify(auditLogService).recordBusinessChange(eq(PERFORMER_ID), eq(AuditLogAction.UPDATE),
                eq("paraclinical_order_details"), eq(detail.getId()), eq("CD0000000009-01"), anyMap(), eq(NOW));
    }

    @Test
    void anotherTechnicianOfTheRoomCannotConfirmIt() {
        assertThatThrownBy(() -> service.completeOrder(detail.getId(), COLLEAGUE_ID))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));

        assertThat(detail.getStatus()).isEqualTo(ParaclinicalOrderDetailStatus.IN_PROGRESS);
        verify(paraclinicalOrderDetailRepository, never()).save(any());
    }

    @Test
    void aServiceThatIsNotInProgressCannotBeConfirmed() {
        service.completeOrder(detail.getId(), PERFORMER_ID);

        assertThatThrownBy(() -> service.completeOrder(detail.getId(), PERFORMER_ID))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PARACLINICAL_SERVICE_NOT_IN_PROGRESS));
    }

    @Test
    void returnsTheServiceToItsPlaceInTheWaitingListWithTheReason() {
        service.returnToWaiting(detail.getId(), PERFORMER_ID,
                new ReturnParaclinicalOrderToWaitingRequest("  Bệnh nhân chưa nhịn ăn  "));

        assertThat(detail.getStatus()).isEqualTo(ParaclinicalOrderDetailStatus.WAITING);
        assertThat(detail.getQueueNumber()).isEqualTo(7);
        assertThat(detail.getPerformerAccountId()).isNull();
        assertThat(detail.getAcceptedAt()).isNull();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, ?>> audit = ArgumentCaptor.forClass(Map.class);
        verify(auditLogService).recordBusinessChange(eq(PERFORMER_ID), eq(AuditLogAction.UPDATE),
                eq("paraclinical_order_details"), eq(detail.getId()), eq("CD0000000009-01"), audit.capture(), eq(NOW));
        Map<String, ?> values = audit.getValue();
        assertThat(values.get("action")).isEqualTo("RETURN_TO_WAITING");
        assertThat(values.get("reason")).isEqualTo("Bệnh nhân chưa nhịn ăn");
    }

    @Test
    void anotherTechnicianCannotReturnTheServiceToWaiting() {
        assertThatThrownBy(() -> service.returnToWaiting(detail.getId(), COLLEAGUE_ID,
                new ReturnParaclinicalOrderToWaitingRequest("Máy hỏng")))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));

        assertThat(detail.getStatus()).isEqualTo(ParaclinicalOrderDetailStatus.IN_PROGRESS);
    }

    @Test
    void theEntityRejectsAConfirmationByAnotherTechnician() {
        assertThatThrownBy(() -> detail.completePerformance(COLLEAGUE_ID, NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> detail.returnToWaiting(COLLEAGUE_ID, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    private ParaclinicalOrderDetail acceptedDetail() {
        ParaclinicalOrder order = new ParaclinicalOrder(
                UUID.randomUUID(), ACCEPTED, "CD0000000009", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Đau bụng", (short) 2, "Thường", "Phòng khám Nội", BigDecimal.valueOf(150000),
                ParaclinicalOrderStatus.RELEASED);
        ParaclinicalOrderDetail created = new ParaclinicalOrderDetail(
                UUID.randomUUID(), ACCEPTED, order, UUID.randomUUID(), "CD0000000009-01", ACCEPTED,
                "Siêu âm ổ bụng", null, null, BigDecimal.ONE, BigDecimal.valueOf(150000),
                BigDecimal.valueOf(150000), BigDecimal.ZERO, BigDecimal.valueOf(150000), null,
                ParaclinicalOrderDetailStatus.PENDING_PAYMENT);
        created.releaseTo(ROOM_ID, 7, ACCEPTED);
        created.accept(PERFORMER_ID, ACCEPTED);
        return created;
    }

    private void assignToRoom(UUID technicianId) {
        StaffAccount technician = mock(StaffAccount.class);
        when(technician.getRole()).thenReturn(Role.TECHNICIAN);
        when(staffAccountRepository.findById(technicianId)).thenReturn(Optional.of(technician));
        when(staffRoomAssignmentRepository.findByStaffAccountIdAndRemovedAtIsNull(technicianId)).thenReturn(Optional.of(
                new StaffRoomAssignment(UUID.randomUUID(), technicianId, ROOM_ID, ACCEPTED, null, null, null, 0L)));
    }
}
