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

import clinic.backend.dto.request.ReturnParaclinicalOrderToWaitingRequest;
import clinic.backend.entity.DutySchedule;
import clinic.backend.entity.ParaclinicalOrder;
import clinic.backend.entity.ParaclinicalOrderDetail;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.StaffRoomAssignment;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.DutyShift;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.ParaclinicalOrderDetailStatus;
import clinic.backend.enums.ParaclinicalOrderStatus;
import clinic.backend.enums.Role;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.ClinicalExaminationRepository;
import clinic.backend.repository.DutyScheduleRepository;
import clinic.backend.repository.ImagingResultRepository;
import clinic.backend.repository.LabResultRepository;
import clinic.backend.repository.MedicalHistoryRepository;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.MedicalServiceRepository;
import clinic.backend.repository.ParaclinicalOrderDetailRepository;
import clinic.backend.repository.PatientRepository;
import clinic.backend.repository.RoomQueueCounterRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.StaffRoomAssignmentRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.service.AuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    @Mock RoomQueueCounterRepository roomQueueCounterRepository;
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
    @Mock ImagingResultRepository imagingResultRepository;
    @Mock LabResultRepository labResultRepository;
    @Mock AuditLogService auditLogService;

    private TechnicianQueueServiceImpl service;
    private ParaclinicalOrderDetail detail;

    @BeforeEach
    void setUp() {
        service = serviceAt(NOW);
        detail = acceptedDetail();
        when(paraclinicalOrderDetailRepository.findById(detail.getId())).thenReturn(Optional.of(detail));
        assignToRoom(PERFORMER_ID);
        assignToRoom(COLLEAGUE_ID);
    }

    @Test
    void letsThePerformerFinishAfterHoursWhileNewAdmissionsAreBlocked() {
        Instant afterHours = Instant.parse("2026-11-09T11:00:00Z");
        service = serviceAt(afterHours);
        ParaclinicalOrderDetail waiting = acceptedDetail();
        waiting.returnToWaiting(PERFORMER_ID, NOW);
        waiting.assignToOrderShift(PERFORMER_ID);
        when(paraclinicalOrderDetailRepository.findById(waiting.getId())).thenReturn(Optional.of(waiting));

        assertThatThrownBy(() -> service.acceptOrder(waiting.getId(), PERFORMER_ID))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TECHNICIAN_NOT_ASSIGNED_TO_ROOM));
        assertThatThrownBy(() -> service.completeOrder(detail.getId(), COLLEAGUE_ID))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));

        assertThat(service.getQueueDetail(detail.getId(), PERFORMER_ID).canComplete()).isTrue();
        assertThatThrownBy(() -> service.getQueueDetail(detail.getId(), COLLEAGUE_ID))
                .isInstanceOfSatisfying(AppException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
        assertThat(service.completeOrder(detail.getId(), PERFORMER_ID).canComplete()).isFalse();
        assertThat(detail.getStatus()).isEqualTo(ParaclinicalOrderDetailStatus.PENDING_RESULT);
        assertThat(detail.getPerformedAt()).isEqualTo(afterHours);
        assertThat(waiting.getStatus()).isEqualTo(ParaclinicalOrderDetailStatus.WAITING);
    }

    @Test
    void handsOverWaitingServicesAndKeepsAcceptedServicesWithTheirPerformer() {
        Instant afternoon = Instant.parse("2026-11-09T07:00:00Z");
        LocalDate date = LocalDate.parse("2026-11-09");
        service = serviceAt(afternoon);
        DutySchedule morningDuty = new DutySchedule(UUID.randomUUID(), ACCEPTED, date, DutyShift.MORNING,
                PERFORMER_ID, ROOM_ID, null, UUID.randomUUID());
        DutySchedule afternoonDuty = new DutySchedule(UUID.randomUUID(), ACCEPTED, date, DutyShift.AFTERNOON,
                COLLEAGUE_ID, ROOM_ID, null, UUID.randomUUID());
        when(dutyScheduleRepository.findByRoomIdAndWorkDateBetweenOrderByWorkDateAscShiftAsc(ROOM_ID, date, date))
                .thenReturn(List.of(morningDuty, afternoonDuty));
        ParaclinicalOrderDetail waiting = waitingDetail(detail.getOrder(), ROOM_ID);
        waiting.assignToOrderShift(PERFORMER_ID);
        waiting.checkIn(8, PERFORMER_ID, NOW);
        ParaclinicalOrderDetail sibling = waitingDetail(detail.getOrder(), UUID.randomUUID());
        sibling.assignToOrderShift(PERFORMER_ID);
        when(paraclinicalOrderDetailRepository.findById(waiting.getId())).thenReturn(Optional.of(waiting));
        when(paraclinicalOrderDetailRepository.findByOrderId(detail.getOrder().getId()))
                .thenReturn(List.of(detail, waiting, sibling));
        when(paraclinicalOrderDetailRepository.findByOrderIdIn(any())).thenReturn(List.of(detail, waiting, sibling));
        when(paraclinicalOrderDetailRepository.findQueueDetails(eq(ROOM_ID), any())).thenReturn(List.of(detail, waiting));

        var queue = service.getQueue(ROOM_ID, null, null, null, PERFORMER_ID);
        assertThat(queue.canManage()).isFalse();
        assertThat(queue.hasOwnWork()).isTrue();
        assertThat(queue.items()).extracting(item -> item.id()).containsExactly(detail.getId(), waiting.getId());
        assertThat(queue.items().stream().filter(item -> item.id().equals(waiting.getId())).findFirst().get().canManageOrder()).isFalse();
        assertThat(service.getDashboardQueue(ROOM_ID, PERFORMER_ID).stats().waiting()).isZero();
        assertThat(service.getDashboardQueue(ROOM_ID, COLLEAGUE_ID).items())
                .extracting(item -> item.id()).containsExactly(waiting.getId());
        var nextQueue = service.getQueue(ROOM_ID, null, null, null, COLLEAGUE_ID);
        assertThat(nextQueue.canManage()).isTrue();
        assertThat(nextQueue.stats().total()).isEqualTo(1);
        assertThat(nextQueue.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(waiting.getId());
            assertThat(item.canManageOrder()).isTrue();
            assertThat(item.allOrdersToday()).extracting(related -> related.id()).containsExactly(waiting.getId());
        });
        assertThatThrownBy(() -> service.acceptOrder(waiting.getId(), PERFORMER_ID)).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> service.completeOrder(detail.getId(), COLLEAGUE_ID)).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> service.getQueueDetail(detail.getId(), COLLEAGUE_ID)).isInstanceOf(AppException.class);

        service.acceptOrder(waiting.getId(), COLLEAGUE_ID);
        service.completeOrder(detail.getId(), PERFORMER_ID);

        assertThat(waiting.getPerformerAccountId()).isEqualTo(COLLEAGUE_ID);
        assertThat(waiting.getStatus()).isEqualTo(ParaclinicalOrderDetailStatus.IN_PROGRESS);
        assertThat(waiting.getQueueNumber()).isEqualTo(8);
        assertThat(detail.getStatus()).isEqualTo(ParaclinicalOrderDetailStatus.PENDING_RESULT);
        assertThat(sibling.isCheckedIn()).isFalse();
        assertThat(sibling.getStatus()).isEqualTo(ParaclinicalOrderDetailStatus.WAITING);
        when(paraclinicalOrderDetailRepository.findPendingResultsByRoom(ROOM_ID)).thenReturn(List.of(detail));
        assertThat(service.getPendingResults(ROOM_ID, null, null, null, PERFORMER_ID).items())
                .extracting(item -> item.id()).containsExactly(detail.getId());
        assertThat(service.getPendingResults(ROOM_ID, null, null, null, COLLEAGUE_ID).items())
                .extracting(item -> item.id()).containsExactly(detail.getId());
        assertThat(service.getPendingResults(ROOM_ID, null, null, null, COLLEAGUE_ID).items().getFirst().isPerformer()).isFalse();
        assertThat(service.getQueue(ROOM_ID, "no matching patient", null, null, PERFORMER_ID).hasOwnWork()).isTrue();
        detail.completeWithResult(PERFORMER_ID, afternoon);
        assertThat(service.getQueue(ROOM_ID, null, null, null, PERFORMER_ID).hasOwnWork()).isFalse();
    }

    @Test
    void handsOverOrdersCreatedDuringLunchBreakOrShiftEdgeToAfternoon() {
        Instant afternoon = Instant.parse("2026-11-09T07:00:00Z"); // 14:00 UTC+7
        LocalDate date = LocalDate.parse("2026-11-09");
        service = serviceAt(afternoon);
        DutySchedule afternoonDuty = new DutySchedule(UUID.randomUUID(), ACCEPTED, date, DutyShift.AFTERNOON,
                COLLEAGUE_ID, ROOM_ID, null, UUID.randomUUID());
        when(dutyScheduleRepository.findByRoomIdAndWorkDateBetweenOrderByWorkDateAscShiftAsc(ROOM_ID, date, date))
                .thenReturn(List.of(afternoonDuty));

        // Chỉ định tạo lúc 11:45 (nghỉ trưa) hoặc sau khi ca sáng bị rút ngắn giờ
        Instant lunchBreakOrder = Instant.parse("2026-11-09T04:45:00Z"); // 11:45 UTC+7
        ParaclinicalOrderDetail lunchDetail = new ParaclinicalOrderDetail(
                UUID.randomUUID(), lunchBreakOrder, detail.getOrder(), UUID.randomUUID(), "CD0000000009-02",
                lunchBreakOrder, "Siêu âm tuyến giáp", null, null, BigDecimal.ONE, BigDecimal.valueOf(100000),
                BigDecimal.valueOf(100000), BigDecimal.ZERO, BigDecimal.valueOf(100000), ROOM_ID,
                ParaclinicalOrderDetailStatus.WAITING);
        lunchDetail.assignToOrderShift(PERFORMER_ID); // Gán ca sáng
        lunchDetail.checkIn(9, PERFORMER_ID, lunchBreakOrder);
        when(paraclinicalOrderDetailRepository.findById(lunchDetail.getId())).thenReturn(Optional.of(lunchDetail));
        when(paraclinicalOrderDetailRepository.findQueueDetails(eq(ROOM_ID), any())).thenReturn(List.of(lunchDetail));
        when(paraclinicalOrderDetailRepository.findByOrderId(detail.getOrder().getId())).thenReturn(List.of(lunchDetail));
        when(paraclinicalOrderDetailRepository.findByOrderIdIn(any())).thenReturn(List.of(lunchDetail));

        var queue = service.getQueue(ROOM_ID, null, null, null, COLLEAGUE_ID);
        assertThat(queue.canManage()).isTrue();
        assertThat(queue.items()).extracting(item -> item.id()).containsExactly(lunchDetail.getId());
        assertThat(queue.items().getFirst().canManageOrder()).isTrue();

        service.acceptOrder(lunchDetail.getId(), COLLEAGUE_ID);
        assertThat(lunchDetail.getPerformerAccountId()).isEqualTo(COLLEAGUE_ID);
    }

    @Test
    void technicianOutsideShiftCanViewQueueInReadOnlyModeAndCannotOperate() {
        LocalDate date = LocalDate.parse("2026-11-09");
        DutySchedule afternoonDuty = new DutySchedule(UUID.randomUUID(), ACCEPTED, date, DutyShift.AFTERNOON,
                COLLEAGUE_ID, ROOM_ID, null, UUID.randomUUID());
        when(dutyScheduleRepository.findByRoomIdAndWorkDateBetweenOrderByWorkDateAscShiftAsc(ROOM_ID, date, date))
                .thenReturn(List.of(afternoonDuty));

        ParaclinicalOrderDetail waiting = waitingDetail(detail.getOrder(), ROOM_ID);
        when(paraclinicalOrderDetailRepository.findQueueDetails(eq(ROOM_ID), any())).thenReturn(List.of(waiting));
        when(paraclinicalOrderDetailRepository.findById(waiting.getId())).thenReturn(Optional.of(waiting));

        var queue = service.getQueue(ROOM_ID, null, null, null, PERFORMER_ID);
        assertThat(queue.canManage()).isFalse();
        assertThat(queue.stats().total()).isEqualTo(1);
        assertThat(queue.items()).hasSize(1);
        assertThat(queue.items().getFirst().id()).isEqualTo(waiting.getId());
        assertThat(queue.items().getFirst().canManageOrder()).isFalse();

        var queueDetail = service.getQueueDetail(waiting.getId(), PERFORMER_ID);
        assertThat(queueDetail.canManageOrder()).isFalse();

        assertThatThrownBy(() -> service.acceptOrder(waiting.getId(), PERFORMER_ID))
                .isInstanceOfSatisfying(AppException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.TECHNICIAN_NOT_ASSIGNED_TO_ROOM));
        assertThatThrownBy(() -> service.checkInOrder(waiting.getId(), PERFORMER_ID))
                .isInstanceOfSatisfying(AppException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.TECHNICIAN_NOT_ASSIGNED_TO_ROOM));
        assertThatThrownBy(() -> service.skipOrder(waiting.getId(), PERFORMER_ID))
                .isInstanceOfSatisfying(AppException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.TECHNICIAN_NOT_ASSIGNED_TO_ROOM));
    }

    @Test
    void technicianOutsideShiftCanViewPendingResultsInReadOnlyMode() {
        LocalDate date = LocalDate.parse("2026-11-09");
        DutySchedule morningDuty = new DutySchedule(UUID.randomUUID(), ACCEPTED, date, DutyShift.MORNING,
                COLLEAGUE_ID, ROOM_ID, null, UUID.randomUUID());
        when(dutyScheduleRepository.findByRoomIdAndWorkDateBetweenOrderByWorkDateAscShiftAsc(ROOM_ID, date, date))
                .thenReturn(List.of(morningDuty));

        ParaclinicalOrderDetail pending = waitingDetail(detail.getOrder(), ROOM_ID);
        pending.checkIn(1, COLLEAGUE_ID, NOW);
        pending.accept(COLLEAGUE_ID, NOW);
        pending.completePerformance(COLLEAGUE_ID, NOW);
        when(paraclinicalOrderDetailRepository.findPendingResultsByRoom(ROOM_ID)).thenReturn(List.of(pending));

        var pendingResults = service.getPendingResults(ROOM_ID, null, null, null, PERFORMER_ID);
        assertThat(pendingResults.canManage()).isFalse();
        assertThat(pendingResults.stats().total()).isEqualTo(1);
        assertThat(pendingResults.items()).hasSize(1);
        assertThat(pendingResults.items().getFirst().id()).isEqualTo(pending.getId());
        assertThat(pendingResults.items().getFirst().isPerformer()).isFalse();

        var colleagueResults = service.getPendingResults(ROOM_ID, null, null, null, COLLEAGUE_ID);
        assertThat(colleagueResults.canManage()).isTrue();
        assertThat(colleagueResults.items().getFirst().isPerformer()).isTrue();
        var dashboardResults = service.getDashboardPendingResults(ROOM_ID, PERFORMER_ID);
        assertThat(dashboardResults.items()).isEmpty();
        assertThat(dashboardResults.stats().total()).isZero();
        assertThat(service.getDashboardPendingResults(ROOM_ID, COLLEAGUE_ID).items())
                .extracting(item -> item.id()).containsExactly(pending.getId());
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

    private TechnicianQueueServiceImpl serviceAt(Instant time) {
        return new TechnicianQueueServiceImpl(
                roomRepository, roomQueueCounterRepository, staffAccountRepository, staffRoomAssignmentRepository,
                paraclinicalOrderDetailRepository, medicalRecordRepository, visitRepository, patientRepository,
                clinicalExaminationRepository, medicalHistoryRepository, medicalServiceRepository,
                dutyScheduleRepository, new DutyShiftConfigServiceImpl(new ObjectMapper().findAndRegisterModules()),
                imagingResultRepository, labResultRepository,
                auditLogService, Clock.fixed(time, ZoneId.of("Asia/Ho_Chi_Minh")));
    }

    private ParaclinicalOrderDetail acceptedDetail() {
        ParaclinicalOrder order = new ParaclinicalOrder(
                UUID.randomUUID(), ACCEPTED, "CD0000000009", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Đau bụng", (short) 2, "Thường", "Phòng khám Nội", BigDecimal.valueOf(150000),
                ParaclinicalOrderStatus.RELEASED);
        ParaclinicalOrderDetail created = waitingDetail(order, ROOM_ID);
        created.checkIn(7, PERFORMER_ID, ACCEPTED);
        created.accept(PERFORMER_ID, ACCEPTED);
        return created;
    }

    private ParaclinicalOrderDetail waitingDetail(ParaclinicalOrder order, UUID roomId) {
        return new ParaclinicalOrderDetail(
                UUID.randomUUID(), ACCEPTED, order, UUID.randomUUID(), "CD0000000009-01", ACCEPTED,
                "Siêu âm ổ bụng", null, null, BigDecimal.ONE, BigDecimal.valueOf(150000),
                BigDecimal.valueOf(150000), BigDecimal.ZERO, BigDecimal.valueOf(150000), roomId,
                ParaclinicalOrderDetailStatus.WAITING);
    }

    private void assignToRoom(UUID technicianId) {
        StaffAccount technician = mock(StaffAccount.class);
        when(technician.getRole()).thenReturn(Role.TECHNICIAN);
        when(staffAccountRepository.findById(technicianId)).thenReturn(Optional.of(technician));
        when(staffRoomAssignmentRepository.findByStaffAccountIdAndRemovedAtIsNull(technicianId)).thenReturn(Optional.of(
                new StaffRoomAssignment(UUID.randomUUID(), technicianId, ROOM_ID, ACCEPTED, null, null, null, 0L)));
    }
}
