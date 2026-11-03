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
import clinic.backend.entity.Payment;
import clinic.backend.entity.Visit;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.PaymentMethod;
import clinic.backend.enums.PaymentStatus;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitInsuranceVerifyStatus;
import clinic.backend.enums.VisitStatus;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.ClinicFacilityRepository;
import clinic.backend.repository.ParaclinicalOrderDetailRepository;
import clinic.backend.repository.PatientRepository;
import clinic.backend.repository.PaymentRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.PaymentCompletionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-28T01:00:00Z");
    private static final UUID VISIT_ID = UUID.randomUUID();

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private VisitRepository visitRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private ParaclinicalOrderDetailRepository paraclinicalOrderDetailRepository;

    @Mock
    private PaymentCompletionService paymentCompletionService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private StaffAccountRepository staffAccountRepository;

    @Mock
    private ClinicFacilityRepository clinicFacilityRepository;

    private PaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PaymentServiceImpl(
                paymentRepository,
                visitRepository,
                patientRepository,
                roomRepository,
                paraclinicalOrderDetailRepository,
                paymentCompletionService,
                auditLogService,
                staffAccountRepository,
                clinicFacilityRepository,
                Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh")));
    }

    @Test
    void trimsSearchBeforeDelegatingPaymentQuery() {
        Payment payment = examinationFee("P-001");
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
    void refusesToPrintAReceiptThatIsNotPaid() {
        Payment payment = examinationFee("P-002");
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.prepareReceiptPrint(UUID.randomUUID(), payment.getId()))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_NOT_PAID));
        verify(auditLogService, never()).recordBusinessChange(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void printsTheExaminationReceiptWithTheVisitNumberAndLogsIt() {
        UUID cashierId = UUID.randomUUID();
        Payment payment = examinationFee("HD1");
        payment.markAsPaid(PaymentMethod.CASH, cashierId, "PT20261028-HD1");
        Visit visit = visit();
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(visitRepository.findById(VISIT_ID)).thenReturn(Optional.of(visit));
        when(auditLogService.hasAction(AuditLogAction.PRINT, "payments", payment.getId())).thenReturn(false);

        var receipt = service.prepareReceiptPrint(cashierId, payment.getId());

        assertThat(receipt.reprint()).isFalse();
        assertThat(receipt.visitQueueNumber()).isEqualTo(5);
        assertThat(receipt.payment().receiptNumber()).isEqualTo("PT20261028-HD1");
        verify(auditLogService).recordBusinessChange(
                eq(cashierId), eq(AuditLogAction.PRINT), eq("payments"), eq(payment.getId()),
                eq(visit.getSovaovien()), eq(Map.of("receiptNumber", "PT20261028-HD1", "reprint", false)), eq(NOW));
    }

    @Test
    void marksASecondPrintAsACopy() {
        Payment payment = examinationFee("HD2");
        payment.markAsPaid(PaymentMethod.CASH, UUID.randomUUID(), "PT20261028-HD2");
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(auditLogService.hasAction(AuditLogAction.PRINT, "payments", payment.getId())).thenReturn(true);

        assertThat(service.prepareReceiptPrint(UUID.randomUUID(), payment.getId()).reprint()).isTrue();
    }

    @Test
    void marksTheReceiptPaidAndHandsOverToTheSharedCompletion() {
        UUID cashierId = UUID.randomUUID();
        Payment payment = examinationFee("P-003");
        when(paymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.confirmPayment(
                cashierId,
                payment.getId(),
                new ConfirmPaymentRequest(PaymentMethod.CASH, null));

        assertThat(response.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getReceiptNumber()).isEqualTo("PT20261028-P-003");
        assertThat(payment.getCashierAccountId()).isEqualTo(cashierId);
        verify(paymentRepository).save(payment);
        verify(paymentCompletionService).completePaidReceipt(payment, cashierId);
    }

    @Test
    void refusesToConfirmAReceiptThatIsNoLongerPending() {
        Payment payment = examinationFee("P-004");
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
        verify(paymentCompletionService, never()).completePaidReceipt(any(), any());
    }

    private static Visit visit() {
        return new Visit(VISIT_ID, NOW, "DK0000000001", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                NOW, VisitDoituongbnLoai.DV, null, null, null, "Khám bệnh", false, null,
                LocalDate.of(2026, 10, 28), 5, VisitStatus.WAITING, VisitInsuranceVerifyStatus.NOT_APPLICABLE);
    }

    private static Payment examinationFee(String code) {
        return Payment.createExaminationFee(
                UUID.randomUUID(), NOW, code, VISIT_ID,
                BigDecimal.valueOf(100000), BigDecimal.ZERO, BigDecimal.valueOf(100000));
    }
}
