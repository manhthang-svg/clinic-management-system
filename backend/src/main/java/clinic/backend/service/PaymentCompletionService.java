package clinic.backend.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import clinic.backend.service.impl.ParaclinicalRoomDuty;
import clinic.backend.repository.VisitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-71 steps 7–8 after a receipt becomes Paid, shared by the counter confirmation and PayOS: the examination
 * fee issues the visit's queue number and puts it in its room's waiting list (BR-15, I1-D48); the paraclinical fee
 * releases the orders of UC-49, each service to the staffed room with the fewest waiting services (BR-28, BA decision
 * 12/11/2026). Both write the audit entry (BR-37). UC-49 releases orders that do not wait for payment to the rooms the
 * Doctor chose (BR-26, I1-D47).
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
    private final ParaclinicalRoomDuty paraclinicalRoomDuty;
    private final Clock clock;

    /**
     * Runs in the caller's transaction so the receipt, the visit and the orders change together (all or nothing).
     * {@code actorAccountId} is the cashier, or {@code null} when PayOS confirmed the payment.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void completePaidReceipt(Payment payment, UUID actorAccountId) {
        Instant now = clock.instant();
        Visit visit = visitRepository.findById(payment.getVisitId())
                .orElseThrow(() -> new AppException(ErrorCode.VISIT_NOT_FOUND));

        List<String> unroutedServices = new ArrayList<>();
        Integer issuedQueueNumber = null;
        if (payment.getType() == PaymentType.EXAMINATION_FEE) {
            if (visit.getStatus() == VisitStatus.WAITING_FEE_PAYMENT) {
                // I1-D48: the queue number is issued only now, in the room and on the date of registration.
                visit.markAsWaiting(queueCounterRepository.takeNext(visit.getRoomId(), visit.getQueueDate()));
                visitRepository.save(visit);
                issuedQueueNumber = visit.getQueueNumber();
            }
        } else if (payment.getType() == PaymentType.PARACLINICAL_FEE) {
            // An insurance visit's orders were already released at order time (BR-26); only pending ones move.
            paraclinicalOrderRepository.findByPaymentId(payment.getId())
                    .ifPresent(order -> unroutedServices.addAll(release(order, now, true)));
        }

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("receiptNumber", payment.getReceiptNumber());
        audit.put("type", payment.getType().name());
        audit.put("paymentMethod", payment.getPaymentMethod() != null ? payment.getPaymentMethod().name() : null);
        audit.put("patientAmount", payment.getPatientTotalAmount());
        if (issuedQueueNumber != null) {
            audit.put("queueNumber", issuedQueueNumber);
        }
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
        return release(order, clock.instant(), false);
    }

    /**
     * {@code atPayment}: the patient paid the paraclinical fee (self-pay visit), so each service goes to the room
     * picked now by {@link #roomAtPayment} instead of the room chosen when ordering, which may have been minutes or
     * hours earlier (BA decision 12/11/2026, replaces the "keep the chosen room" part of I1-D47 / BR-50-02).
     */
    private List<String> release(ParaclinicalOrder order, Instant now, boolean atPayment) {
        Set<UUID> staffed = atPayment ? paraclinicalRoomDuty.staffedRooms(now) : Set.of();
        order.release();
        paraclinicalOrderRepository.save(order);
        List<String> unrouted = new ArrayList<>();
        for (ParaclinicalOrderDetail detail : order.getDetails()) {
            if (detail.getStatus() == ParaclinicalOrderDetailStatus.PENDING_PAYMENT
                    && !routeToRoom(detail, now, atPayment, staffed)) {
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
     * Room of a service paid now (BR-28 as clarified by the BA on 12/11/2026): among the Active rooms of the
     * service group, the rooms with a staff member on duty in the current shift; among them the room with the
     * fewest waiting services; ties go to the lower room code. ASSUMPTION: when no room of the group is staffed
     * now, every Active room of the group is a candidate, so the paid service still gets a room.
     */
    Optional<Room> roomAtPayment(UUID serviceGroupId, Set<UUID> staffedRooms) {
        if (serviceGroupId == null) {
            return Optional.empty();
        }
        List<Room> active = roomRepository
                .findAllByServiceGroupIdAndStatusOrderByRoomCodeAsc(serviceGroupId, ActivationStatus.ACTIVE);
        List<Room> onDuty = active.stream().filter(room -> staffedRooms.contains(room.getId())).toList();
        return (onDuty.isEmpty() ? active : onDuty).stream()
                .sorted(Comparator.comparing(Room::getRoomCode))
                .min(Comparator.comparingLong(candidate -> paraclinicalOrderDetailRepository
                        .countByRoomIdAndStatus(candidate.getId(), ParaclinicalOrderDetailStatus.WAITING)));
    }

    /**
     * Sends one service to its room. At payment the room is picked again by {@link #roomAtPayment}; the room chosen
     * when ordering is only used when the service group is unknown. Without payment (UC-49, insurance visit or
     * fee-exempt receipt) the service goes to the room the Doctor chose (I1-D47), or to the least busy Active room
     * when none was chosen or the chosen room is no longer Active (BR-50-03). Returns false when no Active room
     * performs the service (UC-71 AF-02).
     */
    private boolean routeToRoom(ParaclinicalOrderDetail detail, Instant now, boolean atPayment, Set<UUID> staffed) {
        Optional<UUID> groupId = medicalServiceRepository.findById(detail.getServiceId())
                .map(MedicalService::getServiceGroupId);
        Room chosen = Optional.ofNullable(detail.getRoomId())
                .flatMap(roomRepository::findById)
                .filter(room -> room.getStatus() == ActivationStatus.ACTIVE)
                .orElse(null);
        Room room;
        if (atPayment) {
            room = groupId.flatMap(group -> roomAtPayment(group, staffed)).orElse(chosen);
        } else {
            room = chosen;
            if (room == null) {
                if (detail.getRoomId() != null) {
                    log.warn("Chosen room {} of order line {} is not active; routing to the least busy room",
                            detail.getRoomId(), detail.getId());
                }
                room = groupId.flatMap(this::leastBusyRoom).orElse(null);
            }
        }

        if (room == null) {
            detail.releaseTo(null, null, now);
        } else {
            // Case 1 (Check-in tại phòng): Chỉ phân bổ phòng, không cấp số thứ tự ngay.
            // Bệnh nhân sẽ được cấp STT khi KTV bấm xác nhận có mặt tại phòng.
            detail.releaseTo(room.getId(), null, now);
        }
        paraclinicalOrderDetailRepository.save(detail);
        return room != null;
    }
}
