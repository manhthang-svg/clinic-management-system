package clinic.backend.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import clinic.backend.config.properties.InsuranceBillingProperties;
import clinic.backend.entity.ParaclinicalOrderDetail;
import clinic.backend.entity.Payment;
import clinic.backend.entity.PaymentItem;
import clinic.backend.entity.Visit;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.PaymentStatus;
import clinic.backend.repository.ParaclinicalOrderRepository;
import clinic.backend.repository.PaymentRepository;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.InsuranceCoverageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InsuranceCoverageServiceImpl implements InsuranceCoverageService {

    private static final short FULL_COVERAGE = 100;
    private static final String PAYMENT_AUDIT_ENTITY = "payments";

    private final InsuranceBillingProperties properties;
    private final PaymentRepository paymentRepository;
    private final ParaclinicalOrderRepository paraclinicalOrderRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;

    @Override
    public BigDecimal coPaymentExemptionThreshold() {
        return properties.baseSalary()
                .multiply(properties.coPaymentExemptionPercent())
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
    }

    @Override
    public short appliedCoverageRate(BigDecimal scopeTotal, short cardCoverageRate) {
        return scopeTotal.compareTo(coPaymentExemptionThreshold()) < 0 ? FULL_COVERAGE : cardCoverageRate;
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal scopeTotal(UUID visitId) {
        return scopeTotal(paymentRepository.findByVisitId(visitId));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recalculate(Visit visit) {
        if (!visit.isCoverageOpen()) {
            return;
        }
        Instant now = clock.instant();
        List<Payment> payments = paymentRepository.findByVisitId(visit.getId());
        BigDecimal scopeTotal = scopeTotal(payments);
        short appliedRate = appliedCoverageRate(scopeTotal, visit.getInsuranceCoverageRate());

        for (Payment payment : payments) {
            if (!payment.isRepriceable()) {
                continue;
            }
            BigDecimal patientBefore = payment.getPatientTotalAmount();
            payment.repriceInsurance(appliedRate, now);
            paymentRepository.save(payment);
            syncOrderLines(payment);
            if (patientBefore.compareTo(payment.getPatientTotalAmount()) != 0) {
                audit(visit, payment, appliedRate, scopeTotal, patientBefore, now);
            }
        }
    }

    private static BigDecimal scopeTotal(List<Payment> payments) {
        return payments.stream()
                .filter(payment -> payment.getStatus() != PaymentStatus.CANCELLED)
                .flatMap(payment -> payment.getItems().stream())
                .filter(item -> !item.isCancelled())
                .map(PaymentItem::getInsuranceScopeAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** The paraclinical order lines keep a copy of the split shown on the order slip (UC-50). */
    private void syncOrderLines(Payment payment) {
        paraclinicalOrderRepository.findByPaymentId(payment.getId()).ifPresent(order -> {
            Map<UUID, PaymentItem> itemsByDetail = new LinkedHashMap<>();
            payment.getItems().forEach(item -> itemsByDetail.put(item.getOrderDetailId(), item));
            for (ParaclinicalOrderDetail detail : order.getDetails()) {
                PaymentItem item = itemsByDetail.get(detail.getId());
                if (item != null) {
                    detail.syncAmounts(item);
                }
            }
            paraclinicalOrderRepository.save(order);
        });
    }

    private void audit(
            Visit visit, Payment payment, short appliedRate, BigDecimal scopeTotal, BigDecimal patientBefore,
            Instant now) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("rule", "BR-27");
        values.put("appliedCoverageRate", appliedRate);
        values.put("scopeTotal", scopeTotal);
        values.put("patientAmountBefore", patientBefore);
        values.put("patientAmount", payment.getPatientTotalAmount());
        values.put("status", payment.getStatus().name());
        auditLogService.recordSystemChange(
                AuditLogAction.UPDATE, PAYMENT_AUDIT_ENTITY, payment.getId(), visit.getSovaovien(), values, now);
    }
}
