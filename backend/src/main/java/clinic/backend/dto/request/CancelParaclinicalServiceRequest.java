package clinic.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** BR-25, BR-41, BR-97 (5-500 characters): why the Doctor cancels a paraclinical service, usually because the patient refused it. */
public record CancelParaclinicalServiceRequest(

        @NotBlank(message = "Vui lòng nhập lý do hủy dịch vụ")
        @Size(min = 5, max = 500, message = "Lý do hủy từ 5 đến 500 ký tự")
        String reason
) {
}
