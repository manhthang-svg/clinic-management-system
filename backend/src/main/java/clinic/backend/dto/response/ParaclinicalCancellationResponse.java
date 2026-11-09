package clinic.backend.dto.response;

import java.math.BigDecimal;

import clinic.backend.enums.PaymentStatus;
import clinic.backend.enums.VisitStatus;

/**
 * Result of a cancelled paraclinical service (MSG70): the recalculated receipt of its order, and the visit status,
 * which becomes Waiting for Conclusion when no service is left to perform (BR-41).
 */
public record ParaclinicalCancellationResponse(
        String serviceName,
        String paymentCode,
        PaymentStatus paymentStatus,
        BigDecimal patientAmount,
        VisitStatus visitStatus
) {
}
