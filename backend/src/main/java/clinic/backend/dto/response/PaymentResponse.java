package clinic.backend.dto.response;

import clinic.backend.enums.PaymentMethod;
import clinic.backend.enums.PaymentStatus;
import clinic.backend.enums.PaymentType;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        String paymentCode,
        UUID visitId,
        String sovaovien,
        UUID patientId,
        String patientName,
        String patientCode,
        String patientDob,
        String patientGender,
        String patientPhone,
        String roomName,
        PaymentType type,
        PaymentStatus status,
        BigDecimal totalAmount,
        BigDecimal insuranceTotalAmount,
        BigDecimal patientTotalAmount,
        PaymentMethod paymentMethod,
        String receiptNumber,
        Instant paidAt,
        Instant createdAt,
        String note,
        Long payosOrderCode,
        String payosCheckoutUrl,
        String payosQrCode,
        List<PaymentItemResponse> items,
        /** True when a pending receipt of an insurance visit cannot be paid until the conclusion (BR-15, BR-26). */
        boolean awaitingConclusion,
        /** Examination fee only: the visit's queue number, issued once the fee is paid (I1-D48); otherwise null. */
        Integer visitQueueNumber
) {
    @JsonProperty("paymentType")
    public PaymentType paymentType() {
        return type;
    }

    @JsonProperty("amount")
    public BigDecimal amount() {
        return patientTotalAmount;
    }

    @JsonProperty("soVaoVien")
    public String soVaoVien() {
        return sovaovien;
    }
}

