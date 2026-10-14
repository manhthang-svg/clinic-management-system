package clinic.backend.shared.id;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

/**
 * Generates time-ordered RFC 9562 version 7 UUIDs for technical identifiers.
 */
public final class UuidV7Generator {

    private static final long VERSION_7 = 0x7L << 12;
    private static final long VARIANT_RFC_9562 = 0x8000000000000000L;
    private static final long RANDOM_B_MASK = 0x3FFFFFFFFFFFFFFFL;

    private final Clock clock;
    private final SecureRandom random;

    public UuidV7Generator(Clock clock, SecureRandom random) {
        this.clock = clock;
        this.random = random;
    }

    public UUID next() {
        long unixMillis = clock.millis();
        byte[] randomBytes = new byte[10];
        random.nextBytes(randomBytes);

        long randomA = ((randomBytes[0] & 0x0FL) << 8) | (randomBytes[1] & 0xFFL);
        long mostSignificantBits = (unixMillis << 16) | VERSION_7 | randomA;

        long randomB = 0;
        for (int index = 2; index < randomBytes.length; index++) {
            randomB = (randomB << 8) | (randomBytes[index] & 0xFFL);
        }
        long leastSignificantBits = VARIANT_RFC_9562 | (randomB & RANDOM_B_MASK);

        return new UUID(mostSignificantBits, leastSignificantBits);
    }
}
