package clinic.backend.entity;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class BaseEntityTest {

    private static final Instant NOW = Instant.parse("2026-10-22T03:00:00Z");

    @Test
    void anUnversionedEntityIsNewUntilPersistedOrLoaded() {
        CreatedOnly entity = new CreatedOnly(UUID.randomUUID(), NOW);
        assertThat(entity.isNew()).isTrue();

        entity.markNotNew();

        assertThat(entity.isNew()).isFalse();
    }

    @Test
    void aVersionedEntityStartsWithTheCreationTimeAndIsNewWithoutAVersion() {
        Versioned entity = new Versioned(UUID.randomUUID(), NOW);

        assertThat(entity.isNew()).isTrue();
        assertThat(entity.getCreatedAt()).isEqualTo(NOW);
        assertThat(entity.getUpdatedAt()).isEqualTo(NOW);
        assertThat(entity.getVersion()).isNull();
    }

    @Test
    void touchMovesOnlyTheUpdateTime() {
        Versioned entity = new Versioned(UUID.randomUUID(), NOW);

        entity.touch(NOW.plusSeconds(60));

        assertThat(entity.getCreatedAt()).isEqualTo(NOW);
        assertThat(entity.getUpdatedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void requiresAnIdAndACreationTime() {
        assertThatNullPointerException().isThrownBy(() -> new CreatedOnly(null, NOW));
        assertThatNullPointerException().isThrownBy(() -> new CreatedOnly(UUID.randomUUID(), null));
    }

    private static final class CreatedOnly extends CreatedOnlyEntity {
        CreatedOnly(UUID id, Instant createdAt) {
            super(id, createdAt);
        }
    }

    private static final class Versioned extends VersionedEntity {
        Versioned(UUID id, Instant createdAt) {
            super(id, createdAt);
        }
    }
}
