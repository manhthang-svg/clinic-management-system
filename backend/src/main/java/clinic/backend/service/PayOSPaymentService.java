package clinic.backend.service;

import clinic.backend.dto.request.ConfirmPayOSResultRequest;
import clinic.backend.dto.response.PaymentQrResponse;
import clinic.backend.entity.Payment;
import clinic.backend.enums.PaymentStatus;
import clinic.backend.enums.PaymentType;
import clinic.backend.repository.PaymentRepository;

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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class PayOSPaymentService {

    private static final Logger log = LoggerFactory.getLogger(PayOSPaymentService.class);

    private final PayOS payOS;
    private final PaymentRepository paymentRepository;
    private final PaymentCompletionService paymentCompletionService;
    private final String returnUrl;
    private final String cancelUrl;

    public PayOSPaymentService(
            PayOS payOS,
            PaymentRepository paymentRepository,
            PaymentCompletionService paymentCompletionService,
            @Value("${PAYOS_RETURN_URL:http://localhost:5173/payment/result}")
            String returnUrl,
            @Value("${PAYOS_CANCEL_URL:http://localhost:5173/payment/cancel}")
            String cancelUrl
    ) {
        this.payOS = payOS;
        this.paymentRepository = paymentRepository;
        this.paymentCompletionService = paymentCompletionService;
        this.returnUrl = returnUrl;
        this.cancelUrl = cancelUrl;
    }

    @Transactional
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

        if (payment.getPayosOrderCode() != null
                && payment.getPayosCheckoutUrl() != null
                && payment.getPayosQrCode() != null) {
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

        CreatePaymentLinkRequest request =
                CreatePaymentLinkRequest.builder()
                        .orderCode(orderCode)
                        .amount(amount.longValueExact())
                        .description(description)
                        .returnUrl(returnUrl)
                        .cancelUrl(cancelUrl)
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
                result.getPaymentLinkId()
        );

        Payment saved = paymentRepository.save(payment);
        return toResponse(saved);
    }

    @Transactional
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

        boolean isSuccess = "00".equals(request.code())
                || "PAID".equalsIgnoreCase(request.status());

        // Kiểm tra thêm qua SDK PayOS nếu có thể
        try {
            PaymentLink paymentLink = payOS.paymentRequests().get(request.orderCode());
            if (paymentLink != null && (
                    paymentLink.getStatus() == PaymentLinkStatus.PAID
                            || (paymentLink.getAmountPaid() != null
                                && paymentLink.getAmountPaid() >= payment.getPatientTotalAmount().longValue())
            )) {
                isSuccess = true;
            }
        } catch (Exception ex) {
            log.warn("Không thể kiểm tra PayOS SDK cho đơn hàng {}: {}", request.orderCode(), ex.getMessage());
        }

        if (isSuccess && payment.getStatus() == PaymentStatus.PENDING) {
            completePayment(payment);
        }

        return payment.getId();
    }

    public UUID getPaymentIdByOrderCode(Long orderCode) {
        return paymentRepository.findByPayosOrderCode(orderCode)
                .map(Payment::getId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy phiếu thu theo mã đơn hàng PayOS: " + orderCode
                ));
    }

    private void completePayment(Payment payment) {
        String receiptNumber = "PT"
                + LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))
                .format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + payment.getPaymentCode();

        payment.markAsPaidByPayOS(receiptNumber);
        paymentRepository.save(payment);

        // Bước 8 và audit như thu tại quầy (UC-71); xác nhận tự động nên không có thu ngân.
        paymentCompletionService.completePaidReceipt(payment, null);
    }

    private PaymentQrResponse toResponse(Payment payment) {
        return new PaymentQrResponse(
                payment.getId(),
                payment.getPaymentCode(),
                payment.getPayosOrderCode(),
                payment.getPayosCheckoutUrl(),
                payment.getPayosQrCode(),
                payment.getStatus().name()
        );
    }
}
