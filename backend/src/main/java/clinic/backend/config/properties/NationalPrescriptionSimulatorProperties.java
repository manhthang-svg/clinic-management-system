package clinic.backend.config.properties;

import java.time.Duration;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The simulated national prescription system (BR-59-12, BR-59-17). Its rules are configuration for demonstrations,
 * not business rules.
 *
 * @param responseDelay      how long the simulator takes to answer
 * @param timeout            no answer after this long is {@code NO_RESPONSE} (ASSUMPTION: 10 s)
 * @param failureMode        {@code NONE} accepts every send; {@code NO_RESPONSE} never answers (UC-90 AF-02);
 *                           {@code REJECT} rejects the first send of each prescription code (UC-91, UC-92)
 * @param facilityPassword   the facility's password (QĐ 808 III); empty accepts any non-blank one (ASSUMPTION)
 * @param prescriberPassword the password of every Doctor (QĐ 808 VI); empty accepts any non-blank one (ASSUMPTION)
 */
@Validated
@ConfigurationProperties(prefix = "clinic.national-prescription-simulator")
public record NationalPrescriptionSimulatorProperties(
        @NotNull Duration responseDelay,
        @NotNull Duration timeout,
        @NotNull FailureMode failureMode,
        String facilityPassword,
        String prescriberPassword) {

    public NationalPrescriptionSimulatorProperties {
        facilityPassword = facilityPassword == null ? "" : facilityPassword;
        prescriberPassword = prescriberPassword == null ? "" : prescriberPassword;
    }

    @Override
    public String toString() {
        return "NationalPrescriptionSimulatorProperties[responseDelay=" + responseDelay + ", timeout=" + timeout
                + ", failureMode=" + failureMode + "]"; // never the passwords
    }

    public enum FailureMode {
        NONE,
        NO_RESPONSE,
        REJECT
    }
}
