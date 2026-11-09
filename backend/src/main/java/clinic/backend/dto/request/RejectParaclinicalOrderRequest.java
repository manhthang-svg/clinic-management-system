package clinic.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectParaclinicalOrderRequest(
        @NotBlank(message = "Lý do từ chối không được để trống")
        @Size(max = 1000, message = "Lý do từ chối không vượt quá 1000 ký tự")
        String reason
) {}

