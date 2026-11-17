package clinic.backend.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

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
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

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
    void searchesAllStatusesWithATrimmedKeywordAndTheWholeDayRange() {
        Payment payment = examinationFee("P-001");
        when(paymentRepository.search(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(payment)));

        var response = service.getPayments(null, null, "  P-001  ",
                LocalDate.of(2026, 10, 28), LocalDate.of(2026, 10, 28), PageRequest.of(0, 10));

        assertThat(response.getContent()).singleElement()
                .satisfies(r -> assertThat(r.paymentCode()).isEqualTo("P-001"));
        verify(paymentRepository).search(
                EnumSet.allOf(PaymentStatus.class), null,
                Instant.parse("2026-10-27T17:00:00Z"), Instant.parse("2026-10-28T17:00:00Z"),
                "P-001", PageRequest.of(0, 10, NEWEST_FIRST));
        // ca trang chi tai luot kham 1 lan, khong goi rieng tung phieu
        verify(visitRepository).findAllById(Set.of(VISIT_ID));
        verify(visitRepository, never()).findById(any());
    }

    @Test
    void keepsTheSortTheCallerChose() {
        PageRequest byPaidAt = PageRequest.of(0, 6, Sort.by(Sort.Order.desc("paidAt")));
        when(paymentRepository.search(any(), any(), any(), any(), any(), any())).thenReturn(Page.empty());

        service.getPayments(PaymentStatus.PAID, null, null, null, null, byPaidAt);

        verify(paymentRepository).search(any(), any(), any(), any(), any(), eq(byPaidAt));
    }

    @Test
    void rejectsAOneCharacterKeyword() {
        assertThatThrownBy(() -> service.getPayments(null, null, " a ", null, null, PageRequest.of(0, 10)))
                .isInstanceOfSatisfying(AppException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REQUEST_VALIDATION_FAILED);
                    assertThat(e.getDetails()).containsEntry("search", "TOO_SHORT");
                });
    }

    @Test
    void historyDefaultsToTodayAndSumsPaidReceiptsPerMethod() {
        UUID cashierId = UUID.randomUUID();
        when(paymentRepository.history(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(paymentRepository.historyTotals(null, null, cashierId,
                Instant.parse("2026-10-27T17:00:00Z"), Instant.parse("2026-10-28T17:00:00Z"), null))
                .thenReturn(List.of(total(PaymentMethod.CASH, 300000, 2), total(PaymentMethod.BANK_TRANSFER, 200000, 1)));

        var history = service.getHistory(cashierId, null, null, null, null, null, null, PageRequest.of(0, 50));

        verify(paymentRepository).history(
                EnumSet.of(PaymentStatus.PAID, PaymentStatus.CANCELLED), null, null, cashierId,
                Instant.parse("2026-10-27T17:00:00Z"), Instant.parse("2026-10-28T17:00:00Z"), null, PageRequest.of(0, 50));
        assertThat(history.totalsByMethod()).hasSize(2);
        assertThat(history.totalPatientAmount()).isEqualByComparingTo("500000");
    }

    @Test
    void historyRejectsAReversedDateRange() {
        assertThatThrownBy(() -> service.getHistory(null, null, null, null, null,
                LocalDate.of(2026, 10, 28), LocalDate.of(2026, 10, 27), PageRequest.of(0, 50)))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getDetails()).containsEntry("dateRange", "INVALID_FORMAT"));
    }

    @Test
    void historyRejectsPendingReceipts() {
        assertThatThrownBy(() -> service.getHistory(null, PaymentStatus.PENDING, null, null, null, null, null,
                PageRequest.of(0, 50)))
                .isInstanceOf(AppException.class);
    }

    @Test
    void historySearchesByKeywordOfAtLeastTwoCharacters() {
        UUID cashierId = UUID.randomUUID();
        when(paymentRepository.history(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.getHistory(cashierId, null, null, null, "  binh ", null, null, PageRequest.of(1, 20));

        verify(paymentRepository).history(
                EnumSet.of(PaymentStatus.PAID, PaymentStatus.CANCELLED), null, null, cashierId,
                Instant.parse("2026-10-27T17:00:00Z"), Instant.parse("2026-10-28T17:00:00Z"), "binh",
                PageRequest.of(1, 20));
        verify(paymentRepository).historyTotals(null, null, cashierId,
                Instant.parse("2026-10-27T17:00:00Z"), Instant.parse("2026-10-28T17:00:00Z"), "binh");
        assertThatThrownBy(() -> service.getHistory(cashierId, null, null, null, "b", null, null,
                PageRequest.of(0, 20)))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getDetails()).containsEntry("search", "TOO_SHORT"));
    }

    private static PaymentRepository.MethodTotal total(PaymentMethod method, long amount, long count) {
        return new PaymentRepository.MethodTotal() {
            public PaymentMethod getMethod() {
                return method;
            }

            public BigDecimal getTotal() {
                return BigDecimal.valueOf(amount);
            }

            public Long getCount() {
                return count;
            }
        };
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

    @Test
    void refusesToCollectAnInsuranceVisitBeforeTheConclusion() {
        Payment payment = examinationFee("P-005");
        when(paymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        when(visitRepository.findById(VISIT_ID)).thenReturn(Optional.of(insuranceVisit(VisitStatus.WAITING_CONCLUSION)));

        assertThatThrownBy(() -> service.confirmPayment(
                UUID.randomUUID(), payment.getId(), new ConfirmPaymentRequest(PaymentMethod.CASH, null)))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_AWAITS_CONCLUSION));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(paymentCompletionService, never()).completePaidReceipt(any(), any());
    }

    private static Visit insuranceVisit(VisitStatus status) {
        return new Visit(VISIT_ID, NOW, "DK0000000002", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                NOW, VisitDoituongbnLoai.BHYT, "DN4797000000001", "7000000001", LocalDate.of(2026, 1, 1),
                LocalDate.of(2030, 12, 31), "79014", "Bệnh viện Đa khoa Sài Gòn", null, (short) 80, null, null,
                "Khám bệnh", false, null, LocalDate.of(2026, 10, 28), 6, status, VisitInsuranceVerifyStatus.VALID);
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
