package clinic.backend.config.properties;

import java.time.Duration;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "clinic.insurance-simulator")
public record InsuranceSimulatorProperties(@NotNull Duration responseDelay) {
}
