package clinic.backend.config.properties;

import java.time.Duration;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The simulated signing gateway, remote-signing CA, certificate-status service, national trust service and
 * time-stamp service (docs/uc-59-e-prescription.md, Known limitations). Its test keys have no legal value.
 *
 * @param keystore         PKCS#12 file holding the simulator's keys; created on first use, never committed
 * @param keystorePassword password of that file; without it the simulator answers as unavailable
 * @param responseDelay    added to every simulated call, to demonstrate waiting
 * @param failureMode      makes the simulator fail on purpose, to demonstrate EF-15 and EF-16
 * @param consoleEnabled   exposes the simulated CA app and certificate console at {@code /dev/ca-sim}
 */
@Validated
@ConfigurationProperties(prefix = "clinic.signing-simulator")
public record SigningSimulatorProperties(
        @NotNull String keystore,
        String keystorePassword,
        @NotNull Duration responseDelay,
        @NotNull FailureMode failureMode,
        boolean consoleEnabled) {

    public enum FailureMode {
        NONE,
        /** No simulated service answers (UC-90 EF-15). */
        UNAVAILABLE,
        /** The CA returns a signature made with the wrong key (UC-90 EF-16). */
        BAD_SIGNATURE
    }
}
