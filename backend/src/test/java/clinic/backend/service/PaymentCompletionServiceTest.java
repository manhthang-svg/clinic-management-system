package clinic.backend.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.entity.MedicalService;
import clinic.backend.entity.ParaclinicalOrder;
import clinic.backend.entity.ParaclinicalOrderDetail;
import clinic.backend.entity.Payment;
import clinic.backend.entity.Room;
import clinic.backend.entity.Visit;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ParaclinicalOrderDetailStatus;
import clinic.backend.enums.ParaclinicalOrderStatus;
import clinic.backend.enums.PaymentMethod;
import clinic.backend.enums.RoomType;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitInsuranceVerifyStatus;
import clinic.backend.enums.VisitStatus;
import clinic.backend.repository.MedicalServiceRepository;
import clinic.backend.repository.ParaclinicalOrderDetailRepository;
import clinic.backend.repository.ParaclinicalOrderRepository;
import clinic.backend.repository.RoomQueueCounterRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.VisitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentCompletionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-28T01:00:00Z");
    private static final UUID VISIT_ID = UUID.randomUUID();
    private static final UUID SERVICE_ID = UUID.randomUUID();

    @Mock private VisitRepository visitRepository;
    @Mock private ParaclinicalOrderRepository paraclinicalOrderRepository;
    @Mock private ParaclinicalOrderDetailRepository paraclinicalOrderDetailRepository;
    @Mock private MedicalServiceRepository medicalServiceRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private RoomQueueCounterRepository queueCounterRepository;
    @Mock private AuditLogService auditLogService;

    private PaymentCompletionService service;

    @BeforeEach
    void setUp() {
        service = new PaymentCompletionService(
                visitRepository, paraclinicalOrderRepository, paraclinicalOrderDetailRepository,
                medicalServiceRepository, roomRepository, queueCounterRepository, auditLogService,
                Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh")));
    }

    @Test
    void movesThePaidVisitToTheWaitingListAndAudits() {
        UUID cashierId = UUID.randomUUID();
        Payment payment = Payment.createExaminationFee(
                UUID.randomUUID(), NOW, "HD1", VISIT_ID,
                BigDecimal.valueOf(100000), BigDecimal.ZERO, BigDecimal.valueOf(100000));
        payment.markAsPaid(PaymentMethod.CASH, cashierId, "PT20261028-HD1");
        Visit visit = unpaidVisit();
        when(visitRepository.findById(VISIT_ID)).thenReturn(Optional.of(visit));
        when(queueCounterRepository.takeNext(visit.getRoomId(), LocalDate.of(2026, 10, 28))).thenReturn(4);

        service.completePaidReceipt(payment, cashierId);

        // I1-D48: the queue number is issued with the payment, not at registration.
        assertThat(visit.getStatus()).isEqualTo(VisitStatus.WAITING);
        assertThat(visit.getQueueNumber()).isEqualTo(4);
        verify(paraclinicalOrderRepository, never()).findByPaymentId(any());
        verify(auditLogService).recordBusinessChange(
                eq(cashierId), eq(AuditLogAction.UPDATE), eq("payments"), eq(payment.getId()),
                eq(visit.getSovaovien()), argThat(details -> Integer.valueOf(4).equals(details.get("queueNumber"))),
                eq(NOW));
    }

    @Test
    void doesNotIssueASecondQueueNumberWhenTheVisitIsAlreadyWaiting() {
        Payment payment = Payment.createExaminationFee(
                UUID.randomUUID(), NOW, "HD2", VISIT_ID,
                BigDecimal.valueOf(100000), BigDecimal.ZERO, BigDecimal.valueOf(100000));
        payment.markAsPaid(PaymentMethod.CASH, UUID.randomUUID(), "PT20261028-HD2");
        Visit visit = visit(VisitStatus.WAITING);
        when(visitRepository.findById(VISIT_ID)).thenReturn(Optional.of(visit));

        service.completePaidReceipt(payment, null);

        assertThat(visit.getQueueNumber()).isEqualTo(1);
        verify(queueCounterRepository, never()).takeNext(any(), any());
    }

    @Test
    void routesEachPaidServiceToTheActiveRoomWithFewestWaitingOrders() {
        UUID groupId = UUID.randomUUID();
        Payment payment = paidParaclinicalFee();
        ParaclinicalOrder order = order(payment);
        ParaclinicalOrderDetail detail = detail(order);
        order.addDetail(detail);
        Room busy = room(groupId, "CLS01");
        Room free = room(groupId, "CLS02");
        stubParaclinical(payment, order, groupId, List.of(busy, free));
        when(paraclinicalOrderDetailRepository.countByRoomIdAndStatus(busy.getId(), ParaclinicalOrderDetailStatus.WAITING))
                .thenReturn(3L);
        when(paraclinicalOrderDetailRepository.countByRoomIdAndStatus(free.getId(), ParaclinicalOrderDetailStatus.WAITING))
                .thenReturn(1L);

        service.completePaidReceipt(payment, UUID.randomUUID());

        assertThat(order.getStatus()).isEqualTo(ParaclinicalOrderStatus.RELEASED);
        assertThat(detail.getStatus()).isEqualTo(ParaclinicalOrderDetailStatus.WAITING);
        assertThat(detail.getRoomId()).isEqualTo(free.getId());
        assertThat(detail.getQueueNumber()).isNull();
        assertThat(detail.getReleasedAt()).isEqualTo(NOW);
        verify(queueCounterRepository, never()).takeNext(any(), any());
    }

    @Test
    void keepsTheOrderWaitingWithoutRoomWhenNoActiveRoomPerformsIt() {
        UUID groupId = UUID.randomUUID();
        Payment payment = paidParaclinicalFee();
        ParaclinicalOrder order = order(payment);
        ParaclinicalOrderDetail detail = detail(order);
        order.addDetail(detail);
        stubParaclinical(payment, order, groupId, List.of());

        service.completePaidReceipt(payment, null);

        assertThat(detail.getStatus()).isEqualTo(ParaclinicalOrderDetailStatus.WAITING);
        assertThat(detail.getRoomId()).isNull();
        verify(queueCounterRepository, never()).takeNext(any(), any());
        ArgumentCaptor<Map<String, ?>> audit = ArgumentCaptor.captor();
        // Confirmed by PayOS: no cashier, so the audit row is a system change without an actor.
        verify(auditLogService).recordSystemChange(
                eq(AuditLogAction.UPDATE), eq("payments"), eq(payment.getId()),
                any(), audit.capture(), eq(NOW));
        verify(auditLogService, never()).recordBusinessChange(any(), any(), any(), any(), any(), any(), any());
        assertThat(audit.getValue().get("unroutedServices")).isEqualTo(List.of("Siêu âm ổ bụng"));
        assertThat(audit.getValue().get("paymentMethod")).isEqualTo("BANK_TRANSFER");
    }

    @Test
    void leavesOrdersReleasedAtOrderTimeInTheirRoomWhenTheInsuranceVisitPays() {
        Payment payment = paidParaclinicalFee();
        ParaclinicalOrder order = order(payment);
        ParaclinicalOrderDetail detail = detail(order);
        order.addDetail(detail);
        UUID roomId = UUID.randomUUID();
        detail.releaseTo(roomId, 3, NOW);
        order.release();
        when(visitRepository.findById(VISIT_ID)).thenReturn(Optional.of(visit(VisitStatus.WAITING_CONCLUSION)));
        when(paraclinicalOrderRepository.findByPaymentId(payment.getId())).thenReturn(Optional.of(order));

        service.completePaidReceipt(payment, UUID.randomUUID());

        assertThat(detail.getRoomId()).isEqualTo(roomId);
        assertThat(detail.getQueueNumber()).isEqualTo(3);
        verify(queueCounterRepository, never()).takeNext(any(), any());
    }

    @Test
    void releasesAnOrderWithoutPaymentForUc49() {
        UUID groupId = UUID.randomUUID();
        Payment payment = paidParaclinicalFee();
        ParaclinicalOrder order = order(payment);
        ParaclinicalOrderDetail detail = detail(order);
        order.addDetail(detail);
        Room room = room(groupId, "CLS01");
        MedicalService medicalService = mock(MedicalService.class);
        when(medicalService.getServiceGroupId()).thenReturn(groupId);
        when(medicalServiceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(medicalService));
        when(roomRepository.findAllByServiceGroupIdAndStatusOrderByRoomCodeAsc(groupId, ActivationStatus.ACTIVE))
                .thenReturn(List.of(room));

        List<String> unrouted = service.releaseOrder(order);

        assertThat(unrouted).isEmpty();
        assertThat(order.getStatus()).isEqualTo(ParaclinicalOrderStatus.RELEASED);
        assertThat(detail.getStatus()).isEqualTo(ParaclinicalOrderDetailStatus.WAITING);
        assertThat(detail.getRoomId()).isEqualTo(room.getId());
        assertThat(detail.getQueueNumber()).isNull();
    }

    @Test
    void sendsAPaidServiceToTheRoomTheDoctorChoseEvenWhenAnotherIsLessBusy() {
        UUID groupId = UUID.randomUUID();
        Payment payment = paidParaclinicalFee();
        ParaclinicalOrder order = order(payment);
        Room chosen = room(groupId, "CLS01");
        ParaclinicalOrderDetail detail = detail(order, chosen.getId());
        order.addDetail(detail);
        when(visitRepository.findById(VISIT_ID)).thenReturn(Optional.of(visit(VisitStatus.WAITING_PARACLINICAL)));
        when(paraclinicalOrderRepository.findByPaymentId(payment.getId())).thenReturn(Optional.of(order));
        when(roomRepository.findById(chosen.getId())).thenReturn(Optional.of(chosen));

        service.completePaidReceipt(payment, UUID.randomUUID());

        // Case 1: the chosen room keeps the service; queue number is null until on-site check-in.
        assertThat(detail.getRoomId()).isEqualTo(chosen.getId());
        assertThat(detail.getQueueNumber()).isNull();
        verify(roomRepository, never()).findAllByServiceGroupIdAndStatusOrderByRoomCodeAsc(any(), any());
    }

    @Test
    void fallsBackToTheLeastBusyRoomWhenTheChosenRoomWasDeactivated() {
        UUID groupId = UUID.randomUUID();
        Payment payment = paidParaclinicalFee();
        ParaclinicalOrder order = order(payment);
        Room closed = new Room(UUID.randomUUID(), NOW, UUID.randomUUID(), groupId, "CLS01", "Phòng CLS01", "Tầng 2",
                RoomType.PARACLINICAL, ActivationStatus.INACTIVE);
        Room open = room(groupId, "CLS02");
        ParaclinicalOrderDetail detail = detail(order, closed.getId());
        order.addDetail(detail);
        stubParaclinical(payment, order, groupId, List.of(open));
        when(roomRepository.findById(closed.getId())).thenReturn(Optional.of(closed));

        service.completePaidReceipt(payment, UUID.randomUUID());

        assertThat(detail.getRoomId()).isEqualTo(open.getId());
        assertThat(detail.getQueueNumber()).isNull();
    }

    private void stubParaclinical(Payment payment, ParaclinicalOrder order, UUID groupId, List<Room> rooms) {
        MedicalService medicalService = mock(MedicalService.class);
        when(medicalService.getServiceGroupId()).thenReturn(groupId);
        when(visitRepository.findById(VISIT_ID)).thenReturn(Optional.of(visit(VisitStatus.WAITING_PARACLINICAL)));
        when(paraclinicalOrderRepository.findByPaymentId(payment.getId())).thenReturn(Optional.of(order));
        when(medicalServiceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(medicalService));
        when(roomRepository.findAllByServiceGroupIdAndStatusOrderByRoomCodeAsc(groupId, ActivationStatus.ACTIVE))
                .thenReturn(rooms);
    }

    private static Payment paidParaclinicalFee() {
        Payment payment = Payment.createParaclinicalFee(
                UUID.randomUUID(), NOW, "HD2", VISIT_ID,
                BigDecimal.valueOf(200000), BigDecimal.ZERO, BigDecimal.valueOf(200000));
        payment.markAsPaidByPayOS("PT20261028-HD2");
        return payment;
    }

    private static ParaclinicalOrder order(Payment payment) {
        return new ParaclinicalOrder(
                UUID.randomUUID(), NOW, "CD2", UUID.randomUUID(), UUID.randomUUID(), payment.getId(),
                null, (short) 2, null, "Phòng khám 1", BigDecimal.valueOf(200000),
                ParaclinicalOrderStatus.PENDING_PAYMENT);
    }

    private static ParaclinicalOrderDetail detail(ParaclinicalOrder order) {
        return detail(order, null);
    }

    private static ParaclinicalOrderDetail detail(ParaclinicalOrder order, UUID chosenRoomId) {
        return new ParaclinicalOrderDetail(
                UUID.randomUUID(), NOW, order, SERVICE_ID, "CD2-01", NOW, "Siêu âm ổ bụng", null, null,
                BigDecimal.ONE, BigDecimal.valueOf(200000), BigDecimal.valueOf(200000), BigDecimal.ZERO,
                BigDecimal.valueOf(200000), chosenRoomId, ParaclinicalOrderDetailStatus.PENDING_PAYMENT);
    }

    private static Room room(UUID groupId, String code) {
        return new Room(UUID.randomUUID(), NOW, UUID.randomUUID(), groupId, code, "Phòng " + code, "Tầng 2",
                RoomType.PARACLINICAL, ActivationStatus.ACTIVE);
    }

    private static Visit unpaidVisit() {
        return new Visit(VISIT_ID, NOW, "VV202610280000001", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), NOW, VisitDoituongbnLoai.DV, null, null, null, "Khám bệnh", false, null,
                LocalDate.of(2026, 10, 28), null, VisitStatus.WAITING_FEE_PAYMENT,
                VisitInsuranceVerifyStatus.NOT_APPLICABLE);
    }

    private static Visit visit(VisitStatus status) {
        return new Visit(VISIT_ID, NOW, "VV202610280000001", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), NOW, VisitDoituongbnLoai.DV, null, null, null, "Khám bệnh", false, null,
                LocalDate.of(2026, 10, 28), 1, status, VisitInsuranceVerifyStatus.NOT_APPLICABLE);
    }
}
