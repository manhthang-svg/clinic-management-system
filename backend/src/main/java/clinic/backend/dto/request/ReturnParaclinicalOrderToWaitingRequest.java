package clinic.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * UC-66 AF-02: why an accepted service cannot be performed now (patient not prepared, equipment problem).
 * ASSUMPTION: the 500-character limit follows the other free-text notes (BR-99).
 */
public record ReturnParaclinicalOrderToWaitingRequest(
        @NotBlank(message = "Vui lòng nhập lý do trả về hàng chờ")
        @Size(max = 500, message = "Lý do trả về hàng chờ không được vượt quá 500 ký tự")
        String reason
) {
}
