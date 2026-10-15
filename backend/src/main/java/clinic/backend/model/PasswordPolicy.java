package clinic.backend.model;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Password rules decided in AQ-03: 8–64 characters and at most 72 bytes in UTF-8 (BCrypt input limit).
 */
public final class PasswordPolicy {

    public static final int MINIMUM_LENGTH = 8;
    public static final int MAXIMUM_LENGTH = 64;
    public static final int MAXIMUM_UTF8_BYTES = 72;

    public enum Violation {
        TOO_SHORT,
        TOO_LONG
    }

    private PasswordPolicy() {
    }

    public static Optional<Violation> check(String rawPassword) {
        if (rawPassword == null || rawPassword.codePointCount(0, rawPassword.length()) < MINIMUM_LENGTH) {
            return Optional.of(Violation.TOO_SHORT);
        }
        if (rawPassword.codePointCount(0, rawPassword.length()) > MAXIMUM_LENGTH || exceedsByteLimit(rawPassword)) {
            return Optional.of(Violation.TOO_LONG);
        }
        return Optional.empty();
    }

    public static boolean exceedsByteLimit(String rawPassword) {
        return rawPassword.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_UTF8_BYTES;
    }
}
