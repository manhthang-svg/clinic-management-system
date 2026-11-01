package clinic.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CancelFollowUpAppointmentRequest(
        @NotBlank(message = "Vui lòng nhập lý do hủy")
        @Size(max = 500, message = "Lý do hủy không được vượt quá 500 ký tự")
        String reason,
        @NotNull(message = "Phiên bản lịch hẹn không được để trống")
        Long version) {
}
