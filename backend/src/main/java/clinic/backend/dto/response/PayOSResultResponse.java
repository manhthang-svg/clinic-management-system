package clinic.backend.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import clinic.backend.enums.PaymentStatus;
import clinic.backend.enums.PaymentType;

/**
 * Result of a PayOS payment for the public return page. It carries no patient data or ordered services, because
 * the page is opened without signing in (security: personal data of patients).
 */
public record PayOSResultResponse(
        UUID id,
        String paymentCode,
        String receiptNumber,
        PaymentType type,
        PaymentStatus status,
        BigDecimal totalAmount,
        BigDecimal patientTotalAmount,
        Instant createdAt,
        Instant paidAt
) {
}
