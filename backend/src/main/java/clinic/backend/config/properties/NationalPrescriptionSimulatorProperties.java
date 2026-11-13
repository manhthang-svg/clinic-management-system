package clinic.backend.config.properties;

import java.time.Duration;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The simulated national prescription system (BR-59-11, BR-59-12). Its rejection rules are configuration for
 * demonstrations, not business rules.
 *
 * @param responseDelay             how long the simulator takes to answer
 * @param timeout                   no answer after this long is {@code NO_RESPONSE} (ASSUMPTION: 10 s)
 * @param failureMode               {@code NO_RESPONSE} never answers (UC-90 AF-02)
 * @param registeredPrescriberCodes prescriber link codes the system knows; empty accepts every code (ASSUMPTION)
 */
@Validated
@ConfigurationProperties(prefix = "clinic.national-prescription-simulator")
public record NationalPrescriptionSimulatorProperties(
        @NotNull Duration responseDelay,
        @NotNull Duration timeout,
        @NotNull FailureMode failureMode,
        List<String> registeredPrescriberCodes) {

    public NationalPrescriptionSimulatorProperties {
        registeredPrescriberCodes = registeredPrescriberCodes == null ? List.of() : List.copyOf(registeredPrescriberCodes);
    }

    public enum FailureMode {
        NONE,
        NO_RESPONSE
    }
}
