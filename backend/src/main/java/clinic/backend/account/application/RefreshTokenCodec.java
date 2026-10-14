package clinic.backend.account.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

/**
 * Creates opaque refresh tokens (32 random bytes, base64url) and their SHA-256 digests.
 */
@Component
public class RefreshTokenCodec {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random;

    public RefreshTokenCodec(SecureRandom random) {
        this.random = random;
    }

    public GeneratedToken generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new GeneratedToken(raw, digest(raw));
    }

    public byte[] digest(String rawToken) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public record GeneratedToken(String raw, byte[] digest) {

        @Override
        public String toString() {
            return "GeneratedToken[redacted]";
        }
    }
}
