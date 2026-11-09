package clinic.backend.config.properties;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * BR-27 insurance billing. An insurance visit whose total cost within insurance scope is below
 * {@code coPaymentExemptionPercent} % of {@code baseSalary} (mức lương cơ sở) is covered at 100%.
 */
@Validated
@ConfigurationProperties(prefix = "clinic.insurance-billing")
public record InsuranceBillingProperties(@NotNull @Positive BigDecimal baseSalary,
                                         @NotNull @Positive BigDecimal coPaymentExemptionPercent) {
}
