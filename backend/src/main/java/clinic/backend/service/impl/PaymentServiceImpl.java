package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import clinic.backend.dto.request.ConfirmPaymentRequest;
import clinic.backend.dto.response.PaymentItemResponse;
import clinic.backend.dto.response.PaymentResponse;
import clinic.backend.entity.MedicalService;
import clinic.backend.entity.ParaclinicalOrderDetail;
import clinic.backend.entity.Patient;
import clinic.backend.entity.Payment;
import clinic.backend.entity.PaymentItem;
import clinic.backend.entity.Room;
import clinic.backend.entity.Visit;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.ParaclinicalOrderDetailStatus;
import clinic.backend.enums.PaymentStatus;
import clinic.backend.enums.PaymentType;
import clinic.backend.enums.VisitStatus;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.MedicalServiceRepository;
import clinic.backend.repository.ParaclinicalOrderDetailRepository;
import clinic.backend.repository.ParaclinicalOrderRepository;
import clinic.backend.repository.PatientRepository;
import clinic.backend.repository.PaymentRepository;
import clinic.backend.repository.RoomQueueCounterRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final VisitRepository visitRepository;
    private final PatientRepository patientRepository;
    private final RoomRepository roomRepository;
    private final ParaclinicalOrderRepository paraclinicalOrderRepository;
    private final ParaclinicalOrderDetailRepository paraclinicalOrderDetailRepository;
    private final MedicalServiceRepository medicalServiceRepository;
    private final RoomQueueCounterRepository queueCounterRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentResponse> getPayments(
            PaymentStatus status,
            PaymentType type,
            String search,
            Pageable pageable
    ) {
        String normalizedSearch = search == null || search.isBlank()
                ? null
                : search.trim();
        return paymentRepository.search(status, type, normalizedSearch, pageable)
                .map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentResponse> getPendingExaminationPayments(
            Pageable pageable
    ) {
        return paymentRepository.findByStatusAndType(
                PaymentStatus.PENDING,
                PaymentType.EXAMINATION_FEE,
                pageable
        ).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));

        return toResponse(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPrintablePayment(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy phiếu thu"
                ));

        if (payment.getStatus() != PaymentStatus.PAID
                && payment.getStatus() != PaymentStatus.FEE_EXEMPT) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Chỉ được in phiếu đã thanh toán hoặc miễn phí"
            );
        }

        return toResponse(payment);
    }

    @Override
    @Transactional
    public PaymentResponse confirmPayment(
            UUID cashierAccountId,
            UUID paymentId,
            ConfirmPaymentRequest request
    ) {
        // 1. Khóa phiếu thu để hai thu ngân không xác nhận cùng lúc (UC-71 bước 4).
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));

        // 2. Phiếu đã thu hoặc đã hủy thì báo MSG64 (EF-02).
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new AppException(ErrorCode.PAYMENT_NOT_PENDING, Map.of(
                    "receiptNumber", payment.getReceiptNumber() != null
                            ? payment.getReceiptNumber()
                            : payment.getPaymentCode(),
                    "status", payment.getStatus().name()));
        }

        Visit visit = visitRepository.findById(payment.getVisitId())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));

        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock);

        // 3. Ghi nhận đã thu với phương thức, thu ngân và số phiếu (bước 7).
        String receiptNumber = "PT"
                + today.format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-"
                + payment.getPaymentCode();
        payment.markAsPaid(
                request.paymentMethod(),
                cashierAccountId,
                receiptNumber
        );
        paymentRepository.save(payment);

        // 4. Tiếp tục lượt khám theo loại phiếu (bước 8):
        List<String> unroutedServices = new ArrayList<>();
        if (payment.getType() == PaymentType.EXAMINATION_FEE) {
            // Phí khám: lượt khám vào danh sách chờ khám của phòng (BR-15).
            if (visit.getStatus() == VisitStatus.WAITING_FEE_PAYMENT) {
                visit.markAsWaiting();
                visitRepository.save(visit);
            }
        } else if (payment.getType() == PaymentType.PARACLINICAL_FEE) {
            // Phí CLS: mỗi chỉ định vào phòng đang hoạt động ít người chờ nhất (BR-26, BR-28).
            paraclinicalOrderRepository.findByPaymentId(payment.getId())
                    .ifPresent(order -> {
                        order.release();
                        paraclinicalOrderRepository.save(order);
                        if (order.getDetails() != null) {
                            for (ParaclinicalOrderDetail detail : order.getDetails()) {
                                if (!routeToLeastBusyRoom(detail, today, now)) {
                                    unroutedServices.add(detail.getTenchidinh());
                                }
                            }
                        }
                    });
        }

        // 5. Ghi audit việc thu tiền (BR-37).
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("receiptNumber", receiptNumber);
        audit.put("type", payment.getType().name());
        audit.put("paymentMethod", request.paymentMethod().name());
        audit.put("patientAmount", payment.getPatientTotalAmount());
        if (!unroutedServices.isEmpty()) {
            audit.put("unroutedServices", unroutedServices);
        }
        auditLogService.recordBusinessChange(
                cashierAccountId,
                AuditLogAction.UPDATE,
                "payments",
                payment.getId(),
                visit.getSovaovien(),
                audit,
                now);

        return toResponse(payment);
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

    private PaymentResponse toResponse(Payment payment) {
        Visit visit = visitRepository.findById(payment.getVisitId()).orElse(null);
        Patient patient = null;
        Room room = null;

        if (visit != null) {
            patient = patientRepository.findById(visit.getPatientId()).orElse(null);
            room = roomRepository.findById(visit.getRoomId()).orElse(null);
        }

        Map<UUID, ParaclinicalOrderDetail> details = new HashMap<>();
        Map<UUID, Room> performingRooms = new HashMap<>();
        if (payment.getType() == PaymentType.PARACLINICAL_FEE && payment.getItems() != null) {
            List<UUID> detailIds = payment.getItems().stream()
                    .map(PaymentItem::getOrderDetailId)
                    .filter(Objects::nonNull)
                    .toList();
            for (ParaclinicalOrderDetail detail : paraclinicalOrderDetailRepository.findAllById(detailIds)) {
                details.put(detail.getId(), detail);
            }
            List<UUID> roomIds = details.values().stream()
                    .map(ParaclinicalOrderDetail::getRoomId)
                    .filter(Objects::nonNull)
                    .toList();
            for (Room performingRoom : roomRepository.findAllById(roomIds)) {
                performingRooms.put(performingRoom.getId(), performingRoom);
            }
        }

        List<PaymentItemResponse> itemResponses = new ArrayList<>();
        if (payment.getItems() != null) {
            for (PaymentItem item : payment.getItems()) {
                ParaclinicalOrderDetail detail = item.getOrderDetailId() != null
                        ? details.get(item.getOrderDetailId())
                        : null;
                Room performingRoom = detail != null && detail.getRoomId() != null
                        ? performingRooms.get(detail.getRoomId())
                        : null;
                itemResponses.add(new PaymentItemResponse(
                        item.getId(),
                        item.getServiceId(),
                        item.getServiceCodeSnapshot(),
                        item.getTenchidinh(),
                        item.getSoluong(),
                        item.getDongia(),
                        item.getThanhtien(),
                        item.getBhytChitra(),
                        item.getBenhnhanphaitra(),
                        performingRoom != null ? performingRoom.getRoomName() : null,
                        performingRoom != null ? performingRoom.getLocation() : null,
                        detail != null ? detail.getQueueNumber() : null
                ));
            }
        }

        String genderText = null;
        if (patient != null) {
            genderText = switch (patient.getGioitinh()) {
                case 1 -> "Nam";
                case 2 -> "Nữ";
                default -> "Khác";
            };
        }

        return new PaymentResponse(
                payment.getId(),
                payment.getPaymentCode(),
                payment.getVisitId(),
                visit != null ? visit.getSovaovien() : null,
                patient != null ? patient.getId() : null,
                patient != null ? patient.getHoten() : "—",
                patient != null ? patient.getMabenhnhan() : "—",
                patient != null && patient.getNgaysinh() != null ? patient.getNgaysinh().toString() : null,
                genderText,
                patient != null ? patient.getSodienthoai() : null,
                room != null ? room.getRoomName() : null,
                payment.getType(),
                payment.getStatus(),
                payment.getTotalAmount(),
                payment.getInsuranceTotalAmount(),
                payment.getPatientTotalAmount(),
                payment.getPaymentMethod(),
                payment.getReceiptNumber(),
                payment.getPaidAt(),
                payment.getCreatedAt(),
                payment.getNote(),
                payment.getPayosOrderCode(),
                payment.getPayosCheckoutUrl(),
                payment.getPayosQrCode(),
                itemResponses
        );
    }
}
