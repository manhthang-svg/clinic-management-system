package clinic.backend.config;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Security settings decided in docs/security.md. Missing or invalid values fail startup.
 */
@Validated
@ConfigurationProperties(prefix = "clinic.security")
public record ClinicSecurityProperties(
        @NotBlank String frontendOrigin,
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Session session,
        @Valid @NotNull Password password) {

    public record Jwt(
            @NotBlank String signingKey,
            @NotBlank String issuer,
            @NotBlank String audience,
            @NotNull Duration accessTokenTtl,
            @NotNull Duration clockSkew) {
    }

    public record Session(
            @NotNull Duration idleTimeout,
            @NotNull Duration absoluteLifetime,
            @NotNull Duration retentionAfterEnd,
            @NotBlank String cookieName,
            boolean cookieSecure) {
    }

    public record Password(@Min(4) @Max(31) int bcryptStrength) {
    }
}
