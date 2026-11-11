package clinic.backend.dto.response;

import java.math.BigDecimal;

public record VisitFeePreviewResponse(
        String serviceName,
        BigDecimal clinicPrice,
        /** Catalogue insurance price of the examination service; null when the service is not covered. */
        BigDecimal insurancePrice,
        BigDecimal appliedPrice,
        BigDecimal insuranceAmount,
        BigDecimal patientAmount) {
}
