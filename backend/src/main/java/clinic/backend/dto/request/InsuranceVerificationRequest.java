package clinic.backend.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record InsuranceVerificationRequest(
        @NotNull UUID patientId,
        @NotBlank(message = "Vui lòng nhập mã BHYT hoặc mã BHXH")
        @Pattern(regexp = "^(?:[0-9]{10}|[A-Z]{2}[0-9]{13})$", message = "Mã BHYT hoặc mã BHXH không đúng định dạng")
        String insuranceCode) {
}
