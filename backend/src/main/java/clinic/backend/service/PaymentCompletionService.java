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

// xu ly sau khi phieu thu da thanh toan (dung chung thu tai quay va PayOS)
// phi kham: cap so thu tu, vao hang cho phong kham; phi CLS: chia tung dich vu vao phong CLS it nguoi cho nhat
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

    // chay chung transaction voi ben goi (thanh cong het hoac khong gi ca); PayOS thi khong co thu ngan
    @Transactional(propagation = Propagation.MANDATORY)
    public void completePaidReceipt(Payment payment, UUID actorAccountId) {
        Instant now = clock.instant();
        Visit visit = visitRepository.findById(payment.getVisitId())
                .orElseThrow(() -> new AppException(ErrorCode.VISIT_NOT_FOUND));

        List<String> unroutedServices = new ArrayList<>();
        Integer issuedQueueNumber = null;
        if (payment.getType() == PaymentType.EXAMINATION_FEE) {
            if (visit.getStatus() == VisitStatus.WAITING_FEE_PAYMENT) {
                // cap so thu tu luc nay, dung phong + ngay dang ky
                visit.markAsWaiting(queueCounterRepository.takeNext(visit.getRoomId(), visit.getQueueDate()));
                visitRepository.save(visit);
                issuedQueueNumber = visit.getQueueNumber();
            }
        } else if (payment.getType() == PaymentType.PARACLINICAL_FEE) {
            // luot BHYT da chuyen phong luc chi dinh (BR-26), chi chuyen cac dich vu con cho
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
            // PayOS xac nhan nen audit khong co nguoi thuc hien
            auditLogService.recordSystemChange(
                    AuditLogAction.UPDATE, "payments", payment.getId(), visit.getSovaovien(), audit, now);
        } else {
            auditLogService.recordBusinessChange(
                    actorAccountId, AuditLogAction.UPDATE, "payments", payment.getId(), visit.getSovaovien(), audit,
                    now);
        }
    }

    // UC-49: chuyen phieu CLS vao phong ngay (luot BHYT hoac phieu 0 dong); tra ve dich vu khong co phong lam
    @Transactional(propagation = Propagation.MANDATORY)
    public List<String> releaseOrder(ParaclinicalOrder order) {
        return release(order, clock.instant(), false);
    }

    // chuyen cac dich vu vao phong; atPayment = vua thanh toan thi chon lai phong luc nay
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

    // phong dang hoat dong it dich vu cho nhat trong nhom; bang nhau thi lay ma phong nho hon
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

    // chon phong luc thanh toan: phong co nguoi truc ca nay, it dich vu cho nhat (BR-28)
    // khong phong nao co nguoi truc thi xet tat ca phong dang hoat dong
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

    // chuyen 1 dich vu vao phong: luc thanh toan chon lai phong; khong qua thanh toan thi dung phong bac si chon
    // (phong do ngung thi lay phong it nguoi nhat); khong co phong nao thi tra false
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
            // chi chia phong, chua cap so thu tu; KTV xac nhan co mat thi moi cap
            detail.releaseTo(room.getId(), null, now);
        }
        paraclinicalOrderDetailRepository.save(detail);
        return room != null;
    }
}
