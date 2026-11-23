package clinic.backend.service;

import java.util.UUID;

import clinic.backend.dto.request.ConfirmPayOSResultRequest;
import clinic.backend.dto.response.PayOSResultResponse;
import clinic.backend.dto.response.PaymentQrResponse;
import clinic.backend.entity.Payment;
import vn.payos.model.webhooks.Webhook;

/** UC-71: payment of a receipt by bank transfer through PayOS. */
public interface PayOSPaymentService {

    /** Creates (or reuses) the PayOS payment link and QR code of the receipt. */
    PaymentQrResponse createQr(UUID paymentId);

    /** PayOS reports a payment: checks the signature, then marks the receipt paid. */
    void handleWebhook(Webhook webhook);

    /** Asks PayOS for the status and marks the receipt paid when it was. */
    PaymentQrResponse syncPayOSStatus(UUID paymentId);

    /** Confirms the result when the browser comes back from the PayOS page. */
    UUID confirmPayOSResult(ConfirmPayOSResultRequest request);

    /**
     * End-of-day job (BR-22): closes the PayOS link before an unpaid receipt is cancelled.
     * {@code true} when there is no link or it is closed; {@code false} when it may be paid or PayOS did not answer.
     */
    boolean closeLinkBeforeCancel(Payment payment);

    UUID getPaymentIdByOrderCode(Long orderCode);

    PayOSResultResponse getResult(UUID paymentId);
}
