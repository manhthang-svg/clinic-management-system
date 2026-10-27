package clinic.backend.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import clinic.backend.dto.request.ConfirmPaymentRequest;
import clinic.backend.dto.response.PaymentItemResponse;
import clinic.backend.dto.response.PaymentResponse;
import clinic.backend.entity.ParaclinicalOrderDetail;
import clinic.backend.entity.Patient;
import clinic.backend.entity.Payment;
import clinic.backend.entity.PaymentItem;
import clinic.backend.entity.Room;
import clinic.backend.entity.Visit;
import clinic.backend.enums.PaymentStatus;
import clinic.backend.enums.PaymentType;
import clinic.backend.enums.VisitStatus;
import clinic.backend.repository.ParaclinicalOrderDetailRepository;
import clinic.backend.repository.ParaclinicalOrderRepository;
import clinic.backend.repository.PatientRepository;
import clinic.backend.repository.PaymentRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
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

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentResponse> getPayments(
            PaymentStatus status,
            PaymentType type,
            String search,
            Pageable pageable
    ) {
        Page<Payment> page;

        if (status != null && type != null) {
            page = paymentRepository.findByStatusAndType(status, type, pageable);
        } else if (status != null) {
            page = paymentRepository.findByStatus(status, pageable);
        } else if (type != null) {
            page = paymentRepository.findByType(type, pageable);
        } else {
            page = paymentRepository.findAll(pageable);
        }

        return page.map(this::toResponse);
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
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy phiếu thu"
                ));

        return toResponse(payment);
    }

    @Override
    @Transactional
    public PaymentResponse confirmPayment(
            UUID paymentId,
            ConfirmPaymentRequest request
    ) {
        // 1. Khóa phiếu thu để tránh hai yêu cầu xác nhận cùng lúc.
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy phiếu thu"
                ));

        // 2. Chỉ cho phép xác nhận phiếu đang chờ thanh toán.
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Phiếu thu không ở trạng thái chờ thanh toán"
            );
        }

        // 3. Lấy ID tài khoản đang đăng nhập từ JWT.
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Không xác định được tài khoản thu ngân"
            );
        }

        UUID cashierAccountId;
        try {
            cashierAccountId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "ID tài khoản đăng nhập không hợp lệ"
            );
        }

        // 4. Tìm lượt khám liên quan đến phiếu thu.
        Visit visit = visitRepository.findById(payment.getVisitId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy lượt khám"
                ));

        // 5. Tạo số phiếu thu.
        String receiptNumber = "PT"
                + LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))
                .format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-"
                + payment.getPaymentCode();

        // 6. Cập nhật phiếu thu thành đã thanh toán.
        payment.markAsPaid(
                request.paymentMethod(),
                cashierAccountId,
                receiptNumber
        );
        paymentRepository.save(payment);

        // 7. Đồng bộ trạng thái nghiệp vụ theo loại thanh toán:
        if (payment.getType() == PaymentType.EXAMINATION_FEE) {
            // Thanh toán lần 1: Phí khám -> chuyển lượt khám sang chờ khám
            if (visit.getStatus() == VisitStatus.WAITING_FEE_PAYMENT) {
                visit.markAsWaiting();
                visitRepository.save(visit);
            }
        } else if (payment.getType() == PaymentType.PARACLINICAL_FEE) {
            // Thanh toán lần 2: Phí CLS -> giải phóng chỉ định CLS để vào hàng đợi phòng thực hiện
            paraclinicalOrderRepository.findByPaymentId(payment.getId())
                    .ifPresent(order -> {
                        order.release();
                        paraclinicalOrderRepository.save(order);
                        if (order.getDetails() != null) {
                            for (ParaclinicalOrderDetail detail : order.getDetails()) {
                                detail.release(Instant.now());
                                paraclinicalOrderDetailRepository.save(detail);
                            }
                        }
                    });
        }

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

        List<PaymentItemResponse> itemResponses = new ArrayList<>();
        if (payment.getItems() != null) {
            for (PaymentItem item : payment.getItems()) {
                itemResponses.add(new PaymentItemResponse(
                        item.getId(),
                        item.getServiceId(),
                        item.getServiceCodeSnapshot(),
                        item.getTenchidinh(),
                        item.getSoluong(),
                        item.getDongia(),
                        item.getThanhtien(),
                        item.getBhytChitra(),
                        item.getBenhnhanphaitra()
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
