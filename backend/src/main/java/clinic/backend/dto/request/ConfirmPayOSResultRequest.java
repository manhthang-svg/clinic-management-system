package clinic.backend.dto.request;

import jakarta.validation.constraints.NotNull;

public record ConfirmPayOSResultRequest(
        @NotNull(message = "Mã đơn hàng không được để trống")
        Long orderCode,

        String code,

        String status,

        Boolean cancel
) {
}
