package clinic.backend.dto.response;

import java.math.BigDecimal;
import java.util.List;

import clinic.backend.enums.PaymentMethod;

/**
 * UC-73 Payment History: the receipts on the requested page plus the patient amount collected per payment method
 * over all matching Paid receipts (step 4).
 */
public record PaymentHistoryResponse(
        PageResponse<PaymentResponse> receipts,
        List<MethodTotal> totalsByMethod,
        BigDecimal totalPatientAmount
) {
    public record MethodTotal(PaymentMethod method, BigDecimal total, long count) {
    }
}
