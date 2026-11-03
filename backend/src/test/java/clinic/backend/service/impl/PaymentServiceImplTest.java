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

import clinic.backend.dto.request.ConfirmPaymentRequest;
import clinic.backend.entity.MedicalService;
import clinic.backend.entity.ParaclinicalOrder;
import clinic.backend.entity.ParaclinicalOrderDetail;
import clinic.backend.entity.Payment;
import clinic.backend.entity.Room;
import clinic.backend.entity.Visit;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.ParaclinicalOrderDetailStatus;
import clinic.backend.enums.ParaclinicalOrderStatus;
import clinic.backend.enums.PaymentMethod;
import clinic.backend.enums.PaymentStatus;
import clinic.backend.enums.PaymentType;
import clinic.backend.enums.RoomType;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitInsuranceVerifyStatus;
import clinic.backend.enums.VisitStatus;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.MedicalServiceRepository;
import clinic.backend.repository.ParaclinicalOrderDetailRepository;
import clinic.backend.repository.ParaclinicalOrderRepository;
import clinic.backend.repository.PatientRepository;
import clinic.backend.repository.PaymentRepository;
import clinic.backend.repository.RoomQueueCounterRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.service.AuditLogService;
import clinic.backend.repository.VisitRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-28T01:00:00Z");
    private static final UUID VISIT_ID = UUID.randomUUID();
    private static final UUID SERVICE_ID = UUID.randomUUID();

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private VisitRepository visitRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private ParaclinicalOrderRepository paraclinicalOrderRepository;

    @Mock
    private ParaclinicalOrderDetailRepository paraclinicalOrderDetailRepository;

    @Mock
    private MedicalServiceRepository medicalServiceRepository;

    @Mock
    private RoomQueueCounterRepository queueCounterRepository;

    @Mock
    private AuditLogService auditLogService;

    private PaymentServiceImpl service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new PaymentServiceImpl(
                paymentRepository,
                visitRepository,
                patientRepository,
                roomRepository,
                paraclinicalOrderRepository,
                paraclinicalOrderDetailRepository,
                medicalServiceRepository,
                queueCounterRepository,
                auditLogService,
                Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh")));
    }

    @Test
    void trimsSearchBeforeDelegatingPaymentQuery() {
        Payment payment = Payment.createExaminationFee(
                UUID.randomUUID(), NOW, "P-001", VISIT_ID,
                BigDecimal.valueOf(100000), BigDecimal.ZERO, BigDecimal.valueOf(100000));
        when(paymentRepository.search(
                null, null, "P-001", PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(payment)));
        when(visitRepository.findById(VISIT_ID)).thenReturn(Optional.empty());

        var response = service.getPayments(
                null, null, "  P-001  ", PageRequest.of(0, 10));

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().getFirst().paymentCode()).isEqualTo("P-001");
        verify(paymentRepository).search(null, null, "P-001", PageRequest.of(0, 10));
    }

    @Test
    void refusesToPrintPendingPayment() {
        Payment payment = Payment.createExaminationFee(
                UUID.randomUUID(), NOW, "P-002", VISIT_ID,
                BigDecimal.valueOf(100000), BigDecimal.ZERO, BigDecimal.valueOf(100000));
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.getPrintablePayment(payment.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("đã thanh toán hoặc miễn phí");
    }

    @Test
    void confirmsExaminationPaymentAndMovesVisitToWaiting() {
        UUID cashierId = UUID.randomUUID();
        Payment payment = Payment.createExaminationFee(
                UUID.randomUUID(), NOW, "P-003", VISIT_ID,
                BigDecimal.valueOf(100000), BigDecimal.ZERO, BigDecimal.valueOf(100000));
        Visit visit = visit(VisitStatus.WAITING_FEE_PAYMENT);
        when(paymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        when(visitRepository.findById(VISIT_ID)).thenReturn(Optional.of(visit));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(visitRepository.save(any(Visit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.confirmPayment(
                cashierId,
                payment.getId(),
                new ConfirmPaymentRequest(PaymentMethod.CASH, null));

        assertThat(response.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(visit.getStatus()).isEqualTo(VisitStatus.WAITING);
        assertThat(payment.getReceiptNumber()).startsWith("PT");
        assertThat(payment.getCashierAccountId()).isEqualTo(cashierId);
        verify(paymentRepository).save(payment);
        verify(visitRepository).save(visit);
        verify(paraclinicalOrderRepository, never()).findByPaymentId(any());
        verify(auditLogService).recordBusinessChange(
                eq(cashierId), eq(AuditLogAction.UPDATE), eq("payments"), eq(payment.getId()),
                eq(visit.getSovaovien()), any(), eq(NOW));
    }

    @Test
    void routesEachPaidServiceToTheActiveRoomWithFewestWaitingOrders() {
        UUID cashierId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        Payment payment = paraclinicalPayment();
        ParaclinicalOrder order = order(payment);
        ParaclinicalOrderDetail detail = detail(order, SERVICE_ID);
        order.addDetail(detail);
        Room busy = room(groupId, "CLS01");
        Room free = room(groupId, "CLS02");
        MedicalService medicalService = mock(MedicalService.class);
        when(medicalService.getServiceGroupId()).thenReturn(groupId);
        stubConfirm(payment, VisitStatus.WAITING_PARACLINICAL);
        when(paraclinicalOrderRepository.findByPaymentId(payment.getId())).thenReturn(Optional.of(order));
        when(medicalServiceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(medicalService));
        when(roomRepository.findAllByServiceGroupIdAndStatusOrderByRoomCodeAsc(groupId, ActivationStatus.ACTIVE))
                .thenReturn(List.of(busy, free));
        when(paraclinicalOrderDetailRepository.countByRoomIdAndStatus(busy.getId(), ParaclinicalOrderDetailStatus.WAITING))
                .thenReturn(3L);
        when(paraclinicalOrderDetailRepository.countByRoomIdAndStatus(free.getId(), ParaclinicalOrderDetailStatus.WAITING))
                .thenReturn(1L);
        when(queueCounterRepository.takeNext(free.getId(), LocalDate.of(2026, 10, 28))).thenReturn(7);

        service.confirmPayment(cashierId, payment.getId(), new ConfirmPaymentRequest(PaymentMethod.BANK_TRANSFER, null));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getStatus()).isEqualTo(ParaclinicalOrderStatus.RELEASED);
        assertThat(detail.getStatus()).isEqualTo(ParaclinicalOrderDetailStatus.WAITING);
        assertThat(detail.getRoomId()).isEqualTo(free.getId());
        assertThat(detail.getQueueNumber()).isEqualTo(7);
        assertThat(detail.getReleasedAt()).isEqualTo(NOW);
    }

    @Test
    void keepsTheOrderWaitingWithoutRoomWhenNoActiveRoomPerformsIt() {
        UUID cashierId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        Payment payment = paraclinicalPayment();
        ParaclinicalOrder order = order(payment);
        ParaclinicalOrderDetail detail = detail(order, SERVICE_ID);
        order.addDetail(detail);
        MedicalService medicalService = mock(MedicalService.class);
        when(medicalService.getServiceGroupId()).thenReturn(groupId);
        stubConfirm(payment, VisitStatus.WAITING_PARACLINICAL);
        when(paraclinicalOrderRepository.findByPaymentId(payment.getId())).thenReturn(Optional.of(order));
        when(medicalServiceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(medicalService));
        when(roomRepository.findAllByServiceGroupIdAndStatusOrderByRoomCodeAsc(groupId, ActivationStatus.ACTIVE))
                .thenReturn(List.of());

        service.confirmPayment(cashierId, payment.getId(), new ConfirmPaymentRequest(PaymentMethod.CASH, null));

        assertThat(detail.getStatus()).isEqualTo(ParaclinicalOrderDetailStatus.WAITING);
        assertThat(detail.getRoomId()).isNull();
        assertThat(detail.getQueueNumber()).isNull();
        verify(queueCounterRepository, never()).takeNext(any(), any());
        ArgumentCaptor<Map<String, ?>> audit = ArgumentCaptor.captor();
        verify(auditLogService).recordBusinessChange(
                eq(cashierId), eq(AuditLogAction.UPDATE), eq("payments"), eq(payment.getId()),
                any(), audit.capture(), eq(NOW));
        assertThat(audit.getValue().get("unroutedServices")).isEqualTo(List.of("Siêu âm ổ bụng"));
    }

    @Test
    void refusesToConfirmAReceiptThatIsNoLongerPending() {
        Payment payment = Payment.createExaminationFee(
                UUID.randomUUID(), NOW, "P-004", VISIT_ID,
                BigDecimal.valueOf(100000), BigDecimal.ZERO, BigDecimal.valueOf(100000));
        payment.markAsPaid(PaymentMethod.CASH, UUID.randomUUID(), "PT20261028-P-004");
        when(paymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.confirmPayment(
                UUID.randomUUID(), payment.getId(), new ConfirmPaymentRequest(PaymentMethod.CASH, null)))
                .isInstanceOfSatisfying(AppException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_NOT_PENDING);
                    assertThat(exception.getDetails())
                            .containsEntry("receiptNumber", "PT20261028-P-004")
                            .containsEntry("status", "PAID");
                });
        verify(auditLogService, never()).recordBusinessChange(any(), any(), any(), any(), any(), any(), any());
    }

    private void stubConfirm(Payment payment, VisitStatus visitStatus) {
        when(paymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        when(visitRepository.findById(VISIT_ID)).thenReturn(Optional.of(visit(visitStatus)));
    }

    private Payment paraclinicalPayment() {
        return Payment.createParaclinicalFee(
                UUID.randomUUID(), NOW, "P-005", VISIT_ID,
                BigDecimal.valueOf(200000), BigDecimal.ZERO, BigDecimal.valueOf(200000));
    }

    private ParaclinicalOrder order(Payment payment) {
        return new ParaclinicalOrder(
                UUID.randomUUID(), NOW, "CD1", UUID.randomUUID(), UUID.randomUUID(), payment.getId(),
                null, (short) 2, null, "Phòng khám 1", BigDecimal.valueOf(200000),
                ParaclinicalOrderStatus.PENDING_PAYMENT);
    }

    private ParaclinicalOrderDetail detail(ParaclinicalOrder order, UUID serviceId) {
        return new ParaclinicalOrderDetail(
                UUID.randomUUID(), NOW, order, serviceId, "CDD1", NOW, "Siêu âm ổ bụng", null, null,
                BigDecimal.ONE, BigDecimal.valueOf(200000), BigDecimal.valueOf(200000), BigDecimal.ZERO,
                BigDecimal.valueOf(200000), null, ParaclinicalOrderDetailStatus.PENDING_PAYMENT);
    }

    private Room room(UUID groupId, String code) {
        return new Room(UUID.randomUUID(), NOW, UUID.randomUUID(), groupId, code, "Phòng " + code, "Tầng 2",
                RoomType.PARACLINICAL, ActivationStatus.ACTIVE);
    }

    private Visit visit(VisitStatus status) {
        return new Visit(
                VISIT_ID,
                NOW,
                "VV202610280000001",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                NOW,
                VisitDoituongbnLoai.DV,
                null,
                null,
                null,
                "Khám bệnh",
                false,
                null,
                LocalDate.of(2026, 10, 28),
                1,
                status,
                VisitInsuranceVerifyStatus.NOT_APPLICABLE);
    }
}
