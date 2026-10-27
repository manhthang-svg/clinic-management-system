package clinic.backend.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

import clinic.backend.enums.MedicalServiceType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateMedicalServiceRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 255) String name,
        @NotNull MedicalServiceType type,
        UUID serviceGroupId,
        UUID departmentId,
        @NotNull @Digits(integer = 9, fraction = 0)
        @DecimalMin("1") @DecimalMax("100000000") BigDecimal clinicPrice,
        @Digits(integer = 9, fraction = 0)
        @DecimalMin("0") @DecimalMax("100000000") BigDecimal insurancePrice,
        String description) {
}
