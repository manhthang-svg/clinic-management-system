package clinic.backend.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** UC-48 Transfer Patient to Another Room. The 5-character minimum is checked after trimming (BR-97). */
public record TransferVisitRequest(
        @NotNull(message = "Vui lòng chọn phòng chuyển đến.")
        UUID toRoomId,
        @NotBlank(message = "Vui lòng nhập lý do.")
        @Size(max = 500, message = "Lý do chuyển phải từ 5 đến 500 ký tự.")
        String reason,
        @Size(max = 500, message = "Ghi chú không được vượt quá 500 ký tự.")
        String notes) {
}
