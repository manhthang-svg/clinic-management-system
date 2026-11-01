package clinic.backend.dto.response;

import java.math.BigDecimal;

public record VisitFeePreviewResponse(
        String serviceName,
        BigDecimal clinicPrice,
        BigDecimal appliedPrice,
        BigDecimal insuranceAmount,
        BigDecimal patientAmount) {
}
