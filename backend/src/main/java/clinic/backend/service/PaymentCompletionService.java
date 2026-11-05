package clinic.backend.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import clinic.backend.entity.MedicalService;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-71 steps 7–8 after a receipt becomes Paid, shared by the counter confirmation and PayOS: the examination
 * fee puts the visit in its room's waiting list (BR-15); the paraclinical fee releases the orders of UC-49 to the
 * Active room with the fewest waiting orders (BR-26, BR-28). Both write the audit entry (BR-37).
 */
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
            paraclinicalOrderRepository.findByPaymentId(payment.getId())
                    .ifPresent(order -> {
                        order.release();
                        paraclinicalOrderRepository.save(order);
                        for (ParaclinicalOrderDetail detail : order.getDetails()) {
                            if (!routeToLeastBusyRoom(detail, today, now)) {
                                unroutedServices.add(detail.getTenchidinh());
                            }
                        }
                    });
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
     * Sends one paid service to the Active room with the fewest waiting orders among the rooms of its service
     * group; ties go to the lower room code (BR-28). Returns false when no Active room performs it (UC-71 AF-02).
     */
    private boolean routeToLeastBusyRoom(ParaclinicalOrderDetail detail, LocalDate today, Instant now) {
        Room room = medicalServiceRepository.findById(detail.getServiceId())
                .map(MedicalService::getServiceGroupId)
                .flatMap(groupId -> roomRepository
                        .findAllByServiceGroupIdAndStatusOrderByRoomCodeAsc(groupId, ActivationStatus.ACTIVE)
                        .stream()
                        .min(Comparator.comparingLong(candidate -> paraclinicalOrderDetailRepository
                                .countByRoomIdAndStatus(candidate.getId(), ParaclinicalOrderDetailStatus.WAITING))))
                .orElse(null);

        if (room == null) {
            detail.releaseTo(null, null, now);
        } else {
            detail.releaseTo(room.getId(), queueCounterRepository.takeNext(room.getId(), today), now);
        }
        paraclinicalOrderDetailRepository.save(detail);
        return room != null;
    }
}
