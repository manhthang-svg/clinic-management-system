package clinic.backend.service.impl;

import clinic.backend.dto.request.ConfirmPayOSResultRequest;
import clinic.backend.dto.response.PayOSResultResponse;
import clinic.backend.dto.response.PaymentQrResponse;
import clinic.backend.entity.Payment;
import clinic.backend.entity.Visit;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.PaymentStatus;
import clinic.backend.enums.PaymentType;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.PaymentRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.service.PayOSPaymentService;
import clinic.backend.service.PaymentCompletionService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;
import vn.payos.model.webhooks.Webhook;
import vn.payos.model.webhooks.WebhookData;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@Service
public class PayOSPaymentServiceImpl implements PayOSPaymentService {

    private static final Logger log = LoggerFactory.getLogger(PayOSPaymentServiceImpl.class);
    private static final String END_OF_DAY_CANCELLATION_REASON = "Hết ngày khám";

    private final PayOS payOS;
    private final PaymentRepository paymentRepository;
    private final VisitRepository visitRepository;
    private final PaymentCompletionService paymentCompletionService;
    private final String returnUrl;
    private final String cancelUrl;

    public PayOSPaymentServiceImpl(
            PayOS payOS,
            PaymentRepository paymentRepository,
            VisitRepository visitRepository,
            PaymentCompletionService paymentCompletionService,
            @Value("${PAYOS_RETURN_URL:http://localhost:5173/payment/result}")
            String returnUrl,
            @Value("${PAYOS_CANCEL_URL:http://localhost:5173/payment/cancel}")
            String cancelUrl
    ) {
        this.payOS = payOS;
        this.paymentRepository = paymentRepository;
        this.visitRepository = visitRepository;
        this.paymentCompletionService = paymentCompletionService;
        this.returnUrl = returnUrl;
        this.cancelUrl = cancelUrl;
    }



    @Transactional
    // UC-71: tao link / ma QR PayOS cho phieu thu
    @Override
    public PaymentQrResponse createQr(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy phiếu thu"
                ));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Phiếu thu không ở trạng thái chờ thanh toán"
            );
        }

        // luot BHYT chi thu sau khi bac si ket luan (BR-15, BR-26, BR-27)
        visitRepository.findById(payment.getVisitId())
                .filter(Visit::awaitsConclusionBeforePayment)
                .ifPresent(visit -> {
                    throw new AppException(ErrorCode.PAYMENT_AWAITS_CONCLUSION,
                            Map.of("paymentCode", payment.getPaymentCode()));
                });

        if (payment.getPayosOrderCode() != null
                && payment.getPayosCheckoutUrl() != null
                && payment.getPayosQrCode() != null
                && (payment.getPayosExpiredAt() == null
                    || Instant.now().isBefore(payment.getPayosExpiredAt()))) {
            return toResponse(payment);
        }

        BigDecimal amount = payment.getPatientTotalAmount();

        if (amount == null
                || amount.signum() <= 0
                || amount.stripTrailingZeros().scale() > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Số tiền thanh toán phải là số nguyên VND lớn hơn 0"
            );
        }

        long orderCode = System.currentTimeMillis();

        while (paymentRepository.existsByPayosOrderCode(orderCode)) {
            orderCode++;
        }

        String description = (payment.getType() == PaymentType.EXAMINATION_FEE ? "PK " : "CLS ")
                + payment.getPaymentCode();
        if (description.length() > 25) {
            description = description.substring(0, 25);
        }

        long expiredAt = Instant.now()
                .plus(Duration.ofMinutes(15))
                .getEpochSecond();

        CreatePaymentLinkRequest request =
                CreatePaymentLinkRequest.builder()
                        .orderCode(orderCode)
                        .amount(amount.longValueExact())
                        .description(description)
                        .returnUrl(returnUrl)
                        .cancelUrl(cancelUrl)
                        .expiredAt(expiredAt)
                        .build();

        final CreatePaymentLinkResponse result;

        try {
            result = payOS.paymentRequests().create(request);
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Không thể tạo link thanh toán PayOS: " + ex.getMessage(),
                    ex
            );
        }

        payment.attachPayOSPayment(
                orderCode,
                result.getCheckoutUrl(),
                result.getQrCode(),
                result.getPaymentLinkId(),
                Instant.ofEpochSecond(expiredAt)
        );

        Payment saved = paymentRepository.save(payment);
        return toResponse(saved);
    }

    @Transactional
    // PayOS bao da thanh toan: kiem tra chu ky roi danh dau da thu
    @Override
    public void handleWebhook(Webhook webhook) {
        final WebhookData data;

        try {
            data = payOS.webhooks().verify(webhook);
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Webhook PayOS không hợp lệ",
                    ex
            );
        }

        Payment payment = paymentRepository
                .findByPayosOrderCode(data.getOrderCode())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy phiếu thu theo mã đơn hàng PayOS"
                ));

        if (!"00".equals(data.getCode())) {
            return;
        }

        BigDecimal expectedAmount = payment.getPatientTotalAmount();

        if (expectedAmount == null
                || expectedAmount.stripTrailingZeros().scale() > 0
                || expectedAmount.longValueExact() != data.getAmount()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Số tiền thanh toán không khớp"
            );
        }

        if (payment.getStatus() == PaymentStatus.PAID) {
            return;
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Phiếu thu không ở trạng thái chờ thanh toán"
            );
        }

        completePayment(payment);
    }

    @Transactional
    // hoi PayOS trang thai, da tra thi danh dau da thu
    @Override
    public PaymentQrResponse syncPayOSStatus(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy phiếu thu"
                ));

        if (payment.getStatus() == PaymentStatus.PAID) {
            return toResponse(payment);
        }

        if (payment.getPayosOrderCode() == null) {
            return toResponse(payment);
        }

        try {
            PaymentLink paymentLink = payOS.paymentRequests().get(payment.getPayosOrderCode());
            if (paymentLink != null && (
                    paymentLink.getStatus() == PaymentLinkStatus.PAID
                            || (paymentLink.getAmountPaid() != null
                                && paymentLink.getAmountPaid() >= payment.getPatientTotalAmount().longValue())
            )) {
                completePayment(payment);
            }
        } catch (Exception ex) {
            log.warn("Lỗi khi kiểm tra trạng thái từ PayOS SDK cho đơn hàng {}: {}", payment.getPayosOrderCode(), ex.getMessage());
        }

        return toResponse(payment);
    }

    @Transactional
    // xac nhan ket qua khi quay ve tu trang PayOS
    @Override
    public UUID confirmPayOSResult(ConfirmPayOSResultRequest request) {
        if (request.orderCode() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mã đơn hàng không được để trống"
            );
        }

        Payment payment = paymentRepository
                .findByPayosOrderCode(request.orderCode())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy phiếu thu theo mã đơn hàng PayOS: " + request.orderCode()
                ));

        if (payment.getStatus() == PaymentStatus.PAID) {
            return payment.getId();
        }

        if (Boolean.TRUE.equals(request.cancel())) {
            return payment.getId();
        }

        // chi tin ket qua hoi truc tiep PayOS, khong tin code/status trinh duyet gui len (co the gia mao)
        boolean isSuccess = false;
        try {
            PaymentLink paymentLink = payOS.paymentRequests().get(request.orderCode());
            isSuccess = paymentLink != null && (
                    paymentLink.getStatus() == PaymentLinkStatus.PAID
                            || (paymentLink.getAmountPaid() != null
                                && paymentLink.getAmountPaid() >= payment.getPatientTotalAmount().longValue()));
        } catch (Exception ex) {
            log.warn("Không thể kiểm tra PayOS SDK cho đơn hàng {}: {}", request.orderCode(), ex.getMessage());
        }

        if (isSuccess && payment.getStatus() == PaymentStatus.PENDING) {
            completePayment(payment);
        }

        return payment.getId();
    }

    // job cuoi ngay (BR-22): dong link PayOS truoc khi huy phieu chua thu
    // true = khong co link / link da dong; false = co the da tra hoac khong goi duoc PayOS (de lan sau)
    @Override
    public boolean closeLinkBeforeCancel(Payment payment) {
        Long orderCode = payment.getPayosOrderCode();
        if (orderCode == null) {
            return true;
        }
        try {
            PaymentLink cancelled = payOS.paymentRequests().cancel(orderCode, END_OF_DAY_CANCELLATION_REASON);
            if (cancelled != null && isClosed(cancelled.getStatus())) {
                return true;
            }
        } catch (Exception ex) {
            log.warn("Không thể hủy link PayOS cho đơn hàng {}: {}", orderCode, ex.getMessage());
        }
        // link co the da huy / het han tu truoc
        try {
            PaymentLink link = payOS.paymentRequests().get(orderCode);
            return link != null && isClosed(link.getStatus());
        } catch (Exception ex) {
            log.warn("Không thể kiểm tra link PayOS cho đơn hàng {}: {}", orderCode, ex.getMessage());
            return false;
        }
    }

    private static boolean isClosed(PaymentLinkStatus status) {
        return status == PaymentLinkStatus.CANCELLED || status == PaymentLinkStatus.EXPIRED;
    }

    // tim phieu thu theo ma don PayOS
    @Override
    public UUID getPaymentIdByOrderCode(Long orderCode) {
        return paymentRepository.findByPayosOrderCode(orderCode)
                .map(Payment::getId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy phiếu thu theo mã đơn hàng PayOS: " + orderCode
                ));
    }

    // danh dau da thu qua chuyen khoan
    private void completePayment(Payment payment) {
        String receiptNumber = "PT"
                + LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))
                .format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + payment.getPaymentCode();

        payment.markAsPaidByPayOS(receiptNumber);
        paymentRepository.save(payment);

        // xu ly sau thu giong thu tai quay, khong co thu ngan
        paymentCompletionService.completePaidReceipt(payment, null);
    }

    @Override
    @Transactional(readOnly = true)
    public PayOSResultResponse getResult(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phiếu thu"));
        return new PayOSResultResponse(
                payment.getId(), payment.getPaymentCode(), payment.getReceiptNumber(), payment.getType(),
                payment.getStatus(), payment.getTotalAmount(), payment.getPatientTotalAmount(),
                payment.getCreatedAt(), payment.getPaidAt());
    }

    private PaymentQrResponse toResponse(Payment payment) {
        return new PaymentQrResponse(
                payment.getId(),
                payment.getPaymentCode(),
                payment.getPayosOrderCode(),
                payment.getPayosCheckoutUrl(),
                payment.getPayosQrCode(),
                payment.getStatus().name(),
                payment.getPayosExpiredAt()
        );
    }
}
