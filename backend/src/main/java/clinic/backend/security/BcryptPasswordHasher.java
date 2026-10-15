package clinic.backend.security;

import java.util.Map;

import clinic.backend.config.ClinicSecurityProperties;
import clinic.backend.model.PasswordPolicy;
import clinic.backend.security.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * BCrypt behind a {@link DelegatingPasswordEncoder} so hashes carry the {@code {bcrypt}} prefix
 * (docs/security.md, Password Handling).
 */
@Component
class BcryptPasswordHasher implements PasswordHasher {

    private static final String BCRYPT_ID = "bcrypt";

    private final PasswordEncoder encoder;
    private final String comparisonHash;

    BcryptPasswordHasher(ClinicSecurityProperties properties) {
        this.encoder = new DelegatingPasswordEncoder(
                BCRYPT_ID,
                Map.of(BCRYPT_ID, new BCryptPasswordEncoder(properties.password().bcryptStrength())));
        this.comparisonHash = encoder.encode("timing-comparison-only");
    }

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        if (rawPassword == null || PasswordPolicy.exceedsByteLimit(rawPassword)) {
            // BCrypt rejects inputs over 72 bytes; such a password can never have been set.
            encoder.matches("timing-comparison-only", comparisonHash);
            return false;
        }
        return encoder.matches(rawPassword, passwordHash);
    }

    @Override
    public void simulateMatch(String rawPassword) {
        String candidate = rawPassword == null || PasswordPolicy.exceedsByteLimit(rawPassword)
                ? "timing-comparison-only"
                : rawPassword;
        encoder.matches(candidate, comparisonHash);
    }
}
