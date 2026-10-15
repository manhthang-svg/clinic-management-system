package clinic.backend.model;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Login identifier: case-insensitive, stored in lowercase, 3–50 characters of {@code a–z 0–9 . _ -} (AQ-05).
 */
public record Username(String value) {

    private static final Pattern FORMAT = Pattern.compile("[a-z0-9._-]{3,50}");

    public Username {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Username does not match the required format");
        }
    }

    /**
     * Normalizes user input; returns empty when the input can never be a valid username.
     */
    public static Optional<Username> parse(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String normalized = input.strip().toLowerCase(Locale.ROOT);
        if (!FORMAT.matcher(normalized).matches()) {
            return Optional.empty();
        }
        return Optional.of(new Username(normalized));
    }

    @Override
    public String toString() {
        return value;
    }
}
