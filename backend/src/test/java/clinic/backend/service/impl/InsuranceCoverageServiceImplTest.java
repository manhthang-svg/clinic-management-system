package clinic.backend.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import clinic.backend.config.properties.InsuranceBillingProperties;
import clinic.backend.entity.MedicalService;
import clinic.backend.entity.Payment;
import clinic.backend.entity.PaymentItem;
import clinic.backend.entity.Visit;
import clinic.backend.enums.MedicalServiceType;
import clinic.backend.enums.PaymentMethod;
import clinic.backend.enums.PaymentStatus;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitInsuranceVerifyStatus;
import clinic.backend.enums.VisitStatus;
import clinic.backend.repository.ParaclinicalOrderRepository;
import clinic.backend.repository.PaymentRepository;
import clinic.backend.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** BR-27: applied coverage rate of an insurance visit and repricing of its unpaid receipts. */
@ExtendWith(MockitoExtension.class)
class InsuranceCoverageServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-08T03:00:00Z");
    private static final UUID VISIT_ID = UUID.randomUUID();

    @Mock private PaymentRepository paymentRepository;
    @Mock private ParaclinicalOrderRepository paraclinicalOrderRepository;
    @Mock private AuditLogService auditLogService;

    private InsuranceCoverageServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InsuranceCoverageServiceImpl(
                new InsuranceBillingProperties(BigDecimal.valueOf(2_340_000), BigDecimal.valueOf(15)),
                paymentRepository, paraclinicalOrderRepository, auditLogService,
                Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh")));
    }

    @Test
    void theThresholdIsFifteenPercentOfTheBaseSalary() {
        assertThat(service.coPaymentExemptionThreshold()).isEqualByComparingTo("351000");
    }

    @Test
    void coversInFullOnlyStrictlyBelowTheThreshold() {
        assertThat(service.appliedCoverageRate(BigDecimal.valueOf(350_999), (short) 80)).isEqualTo((short) 100);
        assertThat(service.appliedCoverageRate(BigDecimal.valueOf(351_000), (short) 80)).isEqualTo((short) 80);
    }

    @Test
    void ignoresCancelledReceiptsInTheScopeTotal() {
        Payment kept = fee("HD1", 150000, 100000, (short) 100);
        Payment cancelled = fee("HD2", 400000, 300000, (short) 80);
        cancelled.cancelAtEndOfDay(NOW);
        when(paymentRepository.findByVisitId(VISIT_ID)).thenReturn(List.of(kept, cancelled));

        assertThat(service.scopeTotal(VISIT_ID)).isEqualByComparingTo("100000");
    }

    @Test
    void bringsAFeeExemptReceiptBackToPendingWhenTheVisitCrossesTheThreshold() {
        // Alone, the examination was covered at 100% and the patient owed nothing.
        Payment exam = fee("HD1", 100000, 100000, (short) 100);
        assertThat(exam.getStatus()).isEqualTo(PaymentStatus.FEE_EXEMPT);
        Payment imaging = fee("HD2", 300000, 300000, (short) 80);
        when(paymentRepository.findByVisitId(VISIT_ID)).thenReturn(List.of(exam, imaging));

        service.recalculate(visit(VisitStatus.WAITING_PARACLINICAL));

        // 400 000 within scope: the card's 80% applies to both receipts.
        assertThat(exam.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(exam.getInsuranceTotalAmount()).isEqualByComparingTo("80000");
        assertThat(exam.getPatientTotalAmount()).isEqualByComparingTo("20000");
        assertThat(imaging.getPatientTotalAmount()).isEqualByComparingTo("60000");
        verify(auditLogService).recordSystemChange(any(), any(), any(), any(), any(), any());
    }

    @Test
    void leavesAPaidReceiptUntouched() {
        Payment paid = fee("HD1", 100000, 100000, (short) 80);
        paid.markAsPaid(PaymentMethod.CASH, UUID.randomUUID(), "PT1");
        when(paymentRepository.findByVisitId(VISIT_ID)).thenReturn(List.of(paid));

        service.recalculate(visit(VisitStatus.WAITING_CONCLUSION));

        assertThat(paid.getPatientTotalAmount()).isEqualByComparingTo("20000");
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void fixesTheRateOnceTheVisitIsCompleted() {
        service.recalculate(visit(VisitStatus.COMPLETED));

        verifyNoInteractions(paymentRepository);
    }

    @Test
    void neverRepricesASelfPaidVisit() {
        Visit selfPaid = new Visit(VISIT_ID, NOW, "DK0000000001", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), NOW, VisitDoituongbnLoai.DV, null, null, null, "Đau bụng", false, null,
                LocalDate.of(2026, 11, 8), 1, VisitStatus.WAITING, VisitInsuranceVerifyStatus.NOT_APPLICABLE);

        service.recalculate(selfPaid);

        verifyNoInteractions(paymentRepository);
    }

    /** An insurance receipt with one line, priced at the given rate. */
    private static Payment fee(String code, long clinicPrice, long insurancePrice, short rate) {
        MedicalService service = MedicalService.create(UUID.randomUUID(), NOW, code, "Dịch vụ " + code,
                MedicalServiceType.EXAMINATION, null, null, BigDecimal.valueOf(clinicPrice),
                BigDecimal.valueOf(insurancePrice), null);
        BigDecimal scope = PaymentItem.insuranceScope(service);
        BigDecimal insurance = PaymentItem.insuranceAmount(scope, rate);
        Payment payment = Payment.createExaminationFee(UUID.randomUUID(), NOW, code, VISIT_ID,
                BigDecimal.valueOf(clinicPrice), insurance, BigDecimal.valueOf(clinicPrice).subtract(insurance));
        payment.addItem(PaymentItem.createInsuranceItem(UUID.randomUUID(), NOW, payment, service, rate));
        return payment;
    }

    private static Visit visit(VisitStatus status) {
        return new Visit(VISIT_ID, NOW, "DK0000000001", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                NOW, VisitDoituongbnLoai.BHYT, "DN4797000000001", "7000000001", LocalDate.of(2026, 1, 1),
                LocalDate.of(2030, 12, 31), "79014", "Bệnh viện Đa khoa Sài Gòn", null, (short) 80, null, null,
                "Đau bụng", false, null, LocalDate.of(2026, 11, 8), 1, status, VisitInsuranceVerifyStatus.VALID);
    }
}
