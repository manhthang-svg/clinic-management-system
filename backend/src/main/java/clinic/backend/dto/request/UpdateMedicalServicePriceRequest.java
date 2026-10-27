package clinic.backend.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateMedicalServicePriceRequest(
        @NotNull @Digits(integer = 9, fraction = 0)
        @DecimalMin("1") @DecimalMax("100000000") BigDecimal clinicPrice,
        @Digits(integer = 9, fraction = 0)
        @DecimalMin("0") @DecimalMax("100000000") BigDecimal insurancePrice,
        @NotNull @PositiveOrZero Long version) {
}
