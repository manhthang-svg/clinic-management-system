package clinic.backend.dto.request;

import clinic.backend.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ConfirmPaymentRequest(

        @NotNull(message = "Phương thức thanh toán không được để trống")
        PaymentMethod paymentMethod,

        @Size(max = 500, message = "Ghi chú không được vượt quá 500 ký tự")
        String note

) {
}
