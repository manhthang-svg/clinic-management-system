package clinic.backend.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import clinic.backend.dto.request.ConfirmPaymentRequest;
import clinic.backend.dto.response.ReceiptPrintResponse;
import clinic.backend.entity.ClinicFacility;
import clinic.backend.entity.StaffAccount;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.repository.ClinicFacilityRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.service.AuditLogService;
import clinic.backend.dto.response.PaymentItemResponse;
import clinic.backend.dto.response.PaymentResponse;
import clinic.backend.entity.ParaclinicalOrderDetail;
import clinic.backend.entity.Patient;
import clinic.backend.entity.Payment;
import clinic.backend.entity.PaymentItem;
import clinic.backend.entity.Room;
import clinic.backend.entity.Visit;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.PaymentStatus;
import clinic.backend.enums.PaymentType;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.ParaclinicalOrderDetailRepository;
import clinic.backend.repository.PatientRepository;
import clinic.backend.repository.PaymentRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.service.PaymentCompletionService;
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

    private static final String PAYMENT_AUDIT_ENTITY = "payments";

    private final PaymentRepository paymentRepository;
    private final VisitRepository visitRepository;
    private final PatientRepository patientRepository;
    private final RoomRepository roomRepository;
    private final ParaclinicalOrderDetailRepository paraclinicalOrderDetailRepository;
    private final PaymentCompletionService paymentCompletionService;
    private final AuditLogService auditLogService;
    private final StaffAccountRepository staffAccountRepository;
    private final ClinicFacilityRepository clinicFacilityRepository;
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
    @Transactional
    public ReceiptPrintResponse prepareReceiptPrint(UUID cashierAccountId, UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));

        // EF-01: chỉ in phiếu đã thu (hoặc miễn phí) – MSG62 (BR-37)
        if (payment.getStatus() != PaymentStatus.PAID && payment.getStatus() != PaymentStatus.FEE_EXEMPT) {
            throw new AppException(ErrorCode.PAYMENT_NOT_PAID, Map.of("status", payment.getStatus().name()));
        }

        // AF-01: từ lần in thứ hai là bản sao; mọi lần in đều ghi log
        boolean reprint = auditLogService.hasAction(AuditLogAction.PRINT, PAYMENT_AUDIT_ENTITY, paymentId);
        Visit visit = visitRepository.findById(payment.getVisitId()).orElse(null);
        auditLogService.recordBusinessChange(
                cashierAccountId, AuditLogAction.PRINT, PAYMENT_AUDIT_ENTITY, paymentId,
                visit != null ? visit.getSovaovien() : null,
                Map.of("receiptNumber", String.valueOf(payment.getReceiptNumber()), "reprint", reprint),
                clock.instant());

        String cashierName = payment.getCashierAccountId() == null
                ? null
                : staffAccountRepository.findById(payment.getCashierAccountId())
                        .map(StaffAccount::getFullName)
                        .orElse(null);
        String facilityName = clinicFacilityRepository.findCurrent()
                .map(ClinicFacility::getTenCskcb)
                .orElse(null);

        return new ReceiptPrintResponse(
                reprint,
                facilityName,
                cashierName,
                visit != null ? visit.getDoituongbnLoai() : null,
                visit != null ? visit.getInsuranceCoverageRate() : null,
                visit != null && payment.getType() == PaymentType.EXAMINATION_FEE ? visit.getQueueNumber() : null,
                toResponse(payment));
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

        // 3. Ghi nhận đã thu với phương thức, thu ngân và số phiếu (bước 7).
        String receiptNumber = "PT"
                + LocalDate.now(clock).format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-"
                + payment.getPaymentCode();
        payment.markAsPaid(
                request.paymentMethod(),
                cashierAccountId,
                receiptNumber
        );
        paymentRepository.save(payment);

        // 4. Bước 8 và audit: dùng chung với PayOS (lượt khám vào hàng chờ, hoặc chia phòng CLS – BR-28).
        paymentCompletionService.completePaidReceipt(payment, cashierAccountId);

        return toResponse(payment);
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
