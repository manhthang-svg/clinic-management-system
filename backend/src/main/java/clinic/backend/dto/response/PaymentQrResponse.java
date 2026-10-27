
package clinic.backend.dto.response;

import java.util.UUID;

public record PaymentQrResponse(
        UUID paymentId,
        String paymentCode,
        Long orderCode,
        String checkoutUrl,
        String qrCode,
        String status
) {
}
