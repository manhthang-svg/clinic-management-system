package clinic.backend.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.ParaclinicalOrderDetailStatus;
import clinic.backend.enums.PaymentType;
import clinic.backend.enums.VisitStatus;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.MedicalServiceRepository;
import clinic.backend.repository.ParaclinicalOrderDetailRepository;
import clinic.backend.repository.ParaclinicalOrderRepository;
import clinic.backend.repository.RoomQueueCounterRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.VisitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-71 steps 7–8 after a receipt becomes Paid, shared by the counter confirmation and PayOS: the examination
 * fee puts the visit in its room's waiting list (BR-15); the paraclinical fee releases the orders of UC-49 to the
 * rooms the Doctor chose (BR-26, I1-D47). Both write the audit entry (BR-37). UC-49 uses the same routing to release
 * orders that do not wait for payment.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCompletionService {

    private final VisitRepository visitRepository;
    private final ParaclinicalOrderRepository paraclinicalOrderRepository;
    private final ParaclinicalOrderDetailRepository paraclinicalOrderDetailRepository;
    private final MedicalServiceRepository medicalServiceRepository;
    private final RoomRepository roomRepository;
    private final RoomQueueCounterRepository queueCounterRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;

    /**
     * Runs in the caller's transaction so the receipt, the visit and the orders change together (all or nothing).
     * {@code actorAccountId} is the cashier, or {@code null} when PayOS confirmed the payment.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void completePaidReceipt(Payment payment, UUID actorAccountId) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock);
        Visit visit = visitRepository.findById(payment.getVisitId())
                .orElseThrow(() -> new AppException(ErrorCode.VISIT_NOT_FOUND));

        List<String> unroutedServices = new ArrayList<>();
        if (payment.getType() == PaymentType.EXAMINATION_FEE) {
            if (visit.getStatus() == VisitStatus.WAITING_FEE_PAYMENT) {
                visit.markAsWaiting();
                visitRepository.save(visit);
            }
        } else if (payment.getType() == PaymentType.PARACLINICAL_FEE) {
            // An insurance visit's orders were already released at order time (BR-26); only pending ones move.
            paraclinicalOrderRepository.findByPaymentId(payment.getId())
                    .ifPresent(order -> unroutedServices.addAll(release(order, today, now)));
        }

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("receiptNumber", payment.getReceiptNumber());
        audit.put("type", payment.getType().name());
        audit.put("paymentMethod", payment.getPaymentMethod() != null ? payment.getPaymentMethod().name() : null);
        audit.put("patientAmount", payment.getPatientTotalAmount());
        if (!unroutedServices.isEmpty()) {
            audit.put("unroutedServices", unroutedServices);
        }
        if (actorAccountId == null) {
            // Confirmed by PayOS, not by a cashier: the audit row has no actor.
            auditLogService.recordSystemChange(
                    AuditLogAction.UPDATE, "payments", payment.getId(), visit.getSovaovien(), audit, now);
        } else {
            auditLogService.recordBusinessChange(
                    actorAccountId, AuditLogAction.UPDATE, "payments", payment.getId(), visit.getSovaovien(), audit,
                    now);
        }
    }

    /**
     * UC-49: releases an order without waiting for payment, for an insurance visit or a receipt the patient pays
     * nothing for (BR-26, UC-49 AF-01). Runs in the caller's transaction. Returns the services no Active room
     * performs.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<String> releaseOrder(ParaclinicalOrder order) {
        return release(order, LocalDate.now(clock), clock.instant());
    }

    private List<String> release(ParaclinicalOrder order, LocalDate today, Instant now) {
        order.release();
        paraclinicalOrderRepository.save(order);
        List<String> unrouted = new ArrayList<>();
        for (ParaclinicalOrderDetail detail : order.getDetails()) {
            if (detail.getStatus() == ParaclinicalOrderDetailStatus.PENDING_PAYMENT
                    && !routeToRoom(detail, today, now)) {
                unrouted.add(detail.getTenchidinh());
            }
        }
        return unrouted;
    }

    /**
     * The Active room with the fewest waiting services among the rooms of a service group; ties go to the lower room
     * code. It is the room preselected for the Doctor in UC-49 (I1-D47, BR-50-01) and the fallback at release.
     */
    public Optional<Room> leastBusyRoom(UUID serviceGroupId) {
        if (serviceGroupId == null) {
            return Optional.empty();
        }
        return roomRepository
                .findAllByServiceGroupIdAndStatusOrderByRoomCodeAsc(serviceGroupId, ActivationStatus.ACTIVE)
                .stream()
                .min(Comparator.comparingLong(candidate -> paraclinicalOrderDetailRepository
                        .countByRoomIdAndStatus(candidate.getId(), ParaclinicalOrderDetailStatus.WAITING)));
    }

    /**
     * Sends one paid service to the room the Doctor chose (I1-D47, BR-50-02). An order line without a chosen room
     * (created before I1-D47) goes to the least busy Active room of its group (BR-28). A chosen room that is no longer
     * Active should not happen (BR-50-03); it also falls back to the least busy room. Returns false when no Active
     * room performs the service (UC-71 AF-02).
     */
    private boolean routeToRoom(ParaclinicalOrderDetail detail, LocalDate today, Instant now) {
        Room room = Optional.ofNullable(detail.getRoomId())
                .flatMap(roomRepository::findById)
                .filter(chosen -> chosen.getStatus() == ActivationStatus.ACTIVE)
                .orElse(null);
        if (room == null) {
            if (detail.getRoomId() != null) {
                log.warn("Chosen room {} of order line {} is not active; routing to the least busy room",
                        detail.getRoomId(), detail.getId());
            }
            room = medicalServiceRepository.findById(detail.getServiceId())
                    .map(MedicalService::getServiceGroupId)
                    .flatMap(this::leastBusyRoom)
                    .orElse(null);
        }

        if (room == null) {
            detail.releaseTo(null, null, now);
        } else {
            detail.releaseTo(room.getId(), queueCounterRepository.takeNext(room.getId(), today), now);
        }
        paraclinicalOrderDetailRepository.save(detail);
        return room != null;
    }
}
