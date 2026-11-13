package clinic.backend.config.properties;

import java.time.Duration;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Digital signing of clinical documents (docs/uc-59-e-prescription.md BR-59-10, BR-59-16).
 *
 * @param approvalTimeout how long the signer has to approve a request with the CA (ASSUMPTION: 5 minutes)
 * @param serviceTimeout  how long the signing gateway, status and time-stamp services may take (ASSUMPTION: 10 s)
 * @param trustedCaFiles  PEM files with the certificates of the trusted CAs (BR-59-16: deployment configuration
 *                        until the Admin use case exists); a missing file trusts nothing
 */
@Validated
@ConfigurationProperties(prefix = "clinic.signing")
public record SigningProperties(
        @NotNull Duration approvalTimeout,
        @NotNull Duration serviceTimeout,
        List<String> trustedCaFiles) {

    public SigningProperties {
        trustedCaFiles = trustedCaFiles == null ? List.of() : List.copyOf(trustedCaFiles);
    }
}
