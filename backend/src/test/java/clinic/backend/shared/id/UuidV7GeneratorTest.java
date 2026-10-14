package clinic.backend.shared.id;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UuidV7GeneratorTest {

    @Test
    void generatesVersion7UuidsCarryingTheClockTimestamp() {
        Instant now = Instant.parse("2026-10-14T08:15:30.123Z");
        UuidV7Generator generator = new UuidV7Generator(Clock.fixed(now, ZoneOffset.UTC), new SecureRandom());

        UUID id = generator.next();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
        assertThat(id.getMostSignificantBits() >>> 16).isEqualTo(now.toEpochMilli());
    }

    @Test
    void laterTimestampsSortAfterEarlierOnes() {
        UuidV7Generator earlier = new UuidV7Generator(
                Clock.fixed(Instant.parse("2026-10-14T08:00:00Z"), ZoneOffset.UTC), new SecureRandom());
        UuidV7Generator later = new UuidV7Generator(
                Clock.fixed(Instant.parse("2026-10-14T08:00:00.001Z"), ZoneOffset.UTC), new SecureRandom());

        assertThat(Long.compareUnsigned(
                earlier.next().getMostSignificantBits(),
                later.next().getMostSignificantBits())).isNegative();
    }

    @Test
    void generatesDistinctValuesWithinTheSameMillisecond() {
        UuidV7Generator generator = new UuidV7Generator(
                Clock.fixed(Instant.parse("2026-10-14T08:00:00Z"), ZoneOffset.UTC), new SecureRandom());

        assertThat(generator.next()).isNotEqualTo(generator.next());
    }
}
