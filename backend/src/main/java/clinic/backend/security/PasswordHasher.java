package clinic.backend.security;

import java.util.Map;

import clinic.backend.config.properties.ClinicSecurityProperties;
import clinic.backend.entity.StaffAccount;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * BCrypt behind a {@link DelegatingPasswordEncoder} so hashes carry the {@code {bcrypt}} prefix
 * (docs/security.md, Password Handling).
 */
@Component
public class PasswordHasher {

    private static final String BCRYPT_ID = "bcrypt";
    private static final String TIMING_COMPARISON_PASSWORD = "timing-comparison-only";

    private final PasswordEncoder encoder;
    private final String comparisonHash;

    public PasswordHasher(ClinicSecurityProperties properties) {
        this.encoder = new DelegatingPasswordEncoder(
                BCRYPT_ID,
                Map.of(BCRYPT_ID, new BCryptPasswordEncoder(properties.password().bcryptStrength())));
        this.comparisonHash = encoder.encode(TIMING_COMPARISON_PASSWORD);
    }

    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String passwordHash) {
        if (rawPassword == null || StaffAccount.exceedsPasswordByteLimit(rawPassword)) {
            // BCrypt rejects inputs over 72 bytes, so such a password can never have been set.
            encoder.matches(TIMING_COMPARISON_PASSWORD, comparisonHash);
            return false;
        }
        return encoder.matches(rawPassword, passwordHash);
    }

    /**
     * Performs a comparison with the same cost as {@link #matches} against a fixed hash, so that
     * a login for an unknown or blocked account takes as long as a real password check.
     */
    public void simulateMatch(String rawPassword) {
        String candidate = rawPassword == null || StaffAccount.exceedsPasswordByteLimit(rawPassword)
                ? TIMING_COMPARISON_PASSWORD
                : rawPassword;
        encoder.matches(candidate, comparisonHash);
    }
}
