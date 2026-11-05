package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.ParaclinicalOrder;
import clinic.backend.entity.ParaclinicalOrderDetail;
import clinic.backend.entity.Payment;
import clinic.backend.entity.Visit;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.MedicalRecordStatus;
import clinic.backend.enums.ParaclinicalOrderDetailStatus;
import clinic.backend.enums.ParaclinicalOrderStatus;
import clinic.backend.enums.PaymentStatus;
import clinic.backend.enums.VisitStatus;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.ParaclinicalOrderRepository;
import clinic.backend.repository.PaymentRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.PayOSPaymentService;
import clinic.backend.service.VisitEndOfDayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * End-of-day job (BR-22, docs/state-transitions.md section 8, V04, V09, V20, O11, S7). Paid receipts are never
 * cancelled or refunded (Q-10).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisitEndOfDayServiceImpl implements VisitEndOfDayService {

    /** Reason recorded for every change made by this job (docs/state-transitions.md section 8). */
    static final String END_OF_DAY_REASON = "HET_NGAY";

    private static final String VISIT_AUDIT_ENTITY = "visits";
    private static final String PAYMENT_AUDIT_ENTITY = "payments";
    private static final String RECORD_AUDIT_ENTITY = "medical_records";
    private static final String ORDER_AUDIT_ENTITY = "paraclinical_orders";
    private static final String ORDER_DETAIL_AUDIT_ENTITY = "paraclinical_order_details";

    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final VisitRepository visitRepository;
    private final PaymentRepository paymentRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final ParaclinicalOrderRepository paraclinicalOrderRepository;
    private final PayOSPaymentService payOSPaymentService;
    private final AuditLogService auditLogService;

    private enum Outcome { CANCELLED, ABANDONED, DEFERRED, ALREADY_FINISHED }

    @Override
    public Result closeUnfinishedVisits() {
        LocalDate today = LocalDate.now(clock);
        List<UUID> visitIds = visitRepository.findUnfinishedIdsBefore(today, VisitRepository.FINISHED_STATUSES);
        int cancelled = 0;
        int abandoned = 0;
        int deferred = 0;
        for (UUID visitId : visitIds) {
            switch (closeSafely(visitId)) {
                case CANCELLED -> cancelled++;
                case ABANDONED -> abandoned++;
                case DEFERRED -> deferred++;
                case ALREADY_FINISHED -> { }
            }
        }
        return new Result(cancelled, abandoned, deferred);
    }

    /** One visit failing (a concurrent edit, PayOS unreachable) must not stop the others; it is retried next run. */
    private Outcome closeSafely(UUID visitId) {
        try {
            Optional<Set<UUID>> closedLinks = closePayOSLinks(visitId);
            if (closedLinks.isEmpty()) {
                log.warn("End-of-day job deferred a visit with an open PayOS link [visitId={}]", visitId);
                return Outcome.DEFERRED;
            }
            return transactionTemplate.execute(status -> close(visitId, closedLinks.get()));
        } catch (RuntimeException ex) {
            log.warn("End-of-day job could not close a visit [visitId={}]", visitId, ex);
            return Outcome.DEFERRED;
        }
    }

    /**
     * Closes the PayOS links of the visit's unpaid receipts before any receipt is cancelled, outside the database
     * transaction. Returns the receipts whose link is closed, or empty when a link may still be paid.
     */
    private Optional<Set<UUID>> closePayOSLinks(UUID visitId) {
        Set<UUID> closed = new HashSet<>();
        for (Payment payment : paymentRepository.findByVisitId(visitId)) {
            if (payment.getStatus() != PaymentStatus.PENDING || payment.getPayosOrderCode() == null) {
                continue;
            }
            if (!payOSPaymentService.closeLinkBeforeCancel(payment)) {
                return Optional.empty();
            }
            closed.add(payment.getId());
        }
        return Optional.of(closed);
    }

    private Outcome close(UUID visitId, Set<UUID> closedLinks) {
        Visit visit = visitRepository.findById(visitId).orElse(null);
        if (visit == null || VisitRepository.FINISHED_STATUSES.contains(visit.getStatus())) {
            return Outcome.ALREADY_FINISHED;
        }
        Instant now = clock.instant();
        List<Payment> payments = paymentRepository.findByVisitId(visitId);
        for (Payment payment : payments) {
            if (payment.getStatus() == PaymentStatus.PENDING && payment.getPayosOrderCode() != null
                    && !closedLinks.contains(payment.getId())) {
                // A PayOS link was attached after the links were closed; retry next run.
                throw new IllegalStateException("Payment link opened during the end-of-day job");
            }
        }

        VisitStatus from = visit.getStatus();
        Outcome outcome;
        if (from == VisitStatus.WAITING_FEE_PAYMENT) {
            visit.cancelAtEndOfDay(now);
            outcome = Outcome.CANCELLED;
        } else {
            visit.abandonAtEndOfDay(now);
            outcome = Outcome.ABANDONED;
        }
        audit(outcome == Outcome.CANCELLED ? AuditLogAction.CANCEL : AuditLogAction.UPDATE,
                VISIT_AUDIT_ENTITY, visit.getId(), visit, from, visit.getStatus(), now);

        for (Payment payment : payments) {
            if (payment.getStatus() == PaymentStatus.PENDING) {
                payment.cancelAtEndOfDay(now);
                audit(AuditLogAction.CANCEL, PAYMENT_AUDIT_ENTITY, payment.getId(), visit,
                        PaymentStatus.PENDING, payment.getStatus(), now);
            }
        }

        medicalRecordRepository.findByVisitId(visitId).ifPresent(record -> closeRecord(record, visit, now));
        return outcome;
    }

    private void closeRecord(MedicalRecord record, Visit visit, Instant now) {
        for (ParaclinicalOrder order : paraclinicalOrderRepository.findAllByMedicalRecordId(record.getId())) {
            closeOrder(order, visit, now);
        }
        if (record.getStatus() == MedicalRecordStatus.OPEN) {
            record.lockAtEndOfDay(now);
            audit(AuditLogAction.LOCK, RECORD_AUDIT_ENTITY, record.getId(), visit,
                    MedicalRecordStatus.OPEN, record.getStatus(), now);
        }
    }

    /** Unperformed services are abandoned; an order whose services were not all performed is cancelled (O11). */
    private void closeOrder(ParaclinicalOrder order, Visit visit, Instant now) {
        boolean allPerformed = !order.getDetails().isEmpty();
        for (ParaclinicalOrderDetail detail : order.getDetails()) {
            if (!detail.isFinished()) {
                ParaclinicalOrderDetailStatus from = detail.getStatus();
                detail.abandonAtEndOfDay(now);
                audit(AuditLogAction.UPDATE, ORDER_DETAIL_AUDIT_ENTITY, detail.getId(), visit,
                        from, detail.getStatus(), now);
            }
            allPerformed &= detail.getStatus() == ParaclinicalOrderDetailStatus.COMPLETED;
        }
        ParaclinicalOrderStatus from = order.getStatus();
        boolean open = from == ParaclinicalOrderStatus.PENDING_PAYMENT || from == ParaclinicalOrderStatus.RELEASED;
        if (open && !allPerformed) {
            order.cancelAtEndOfDay(now);
            audit(AuditLogAction.CANCEL, ORDER_AUDIT_ENTITY, order.getId(), visit, from, order.getStatus(), now);
        }
    }

    private void audit(
            AuditLogAction action, String entityType, UUID entityId, Visit visit,
            Enum<?> from, Enum<?> to, Instant now) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("fromStatus", from.name());
        values.put("status", to.name());
        values.put("reason", END_OF_DAY_REASON);
        auditLogService.recordSystemChange(action, entityType, entityId, visit.getSovaovien(), values, now);
    }
}
