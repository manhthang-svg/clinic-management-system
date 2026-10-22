package clinic.backend.security;

import java.security.SecureRandom;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Generates the 12-character one-time passwords required by the security policy. */
@Component
@RequiredArgsConstructor
public class TemporaryPasswordGenerator {

    public static final int PASSWORD_LENGTH = 12;

    private static final char[] ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();

    private final SecureRandom secureRandom;

    public String generate() {
        char[] password = new char[PASSWORD_LENGTH];
        for (int index = 0; index < password.length; index++) {
            password[index] = ALPHABET[secureRandom.nextInt(ALPHABET.length)];
        }
        return new String(password);
    }
}
