package clinic.backend.entity;

import java.time.Instant;
import java.util.UUID;

import clinic.backend.enums.InstrumentResultType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParaclinicalInstrumentTest {

    private static final Instant NOW = Instant.parse("2026-11-09T02:00:00Z");
    private static final UUID ROOM = UUID.randomUUID();

    @Test
    void newInstrumentIsActiveAndTrimmed() {
        ParaclinicalInstrument instrument = new ParaclinicalInstrument(
                UUID.randomUUID(), NOW, " HEMA-01 ", " Máy huyết học ", InstrumentResultType.LAB, ROOM);

        assertThat(instrument.getCode()).isEqualTo("HEMA-01");
        assertThat(instrument.getName()).isEqualTo("Máy huyết học");
        assertThat(instrument.isActive()).isTrue();
    }

    @Test
    void isUsableOnlyForItsOwnRoomAndResultType() {
        ParaclinicalInstrument instrument = new ParaclinicalInstrument(
                UUID.randomUUID(), NOW, "SA-01", "Máy siêu âm", InstrumentResultType.IMAGING, ROOM);

        assertThat(instrument.usableFor(ROOM, InstrumentResultType.IMAGING)).isTrue();
        assertThat(instrument.usableFor(ROOM, InstrumentResultType.LAB)).isFalse();
        assertThat(instrument.usableFor(UUID.randomUUID(), InstrumentResultType.IMAGING)).isFalse();
    }

    @Test
    void deactivatedInstrumentIsNotUsableUntilActivatedAgain() {
        ParaclinicalInstrument instrument = new ParaclinicalInstrument(
                UUID.randomUUID(), NOW, "XQ-01", "Máy X-quang", InstrumentResultType.IMAGING, ROOM);
        Instant later = NOW.plusSeconds(60);

        instrument.deactivate(later);
        assertThat(instrument.usableFor(ROOM, InstrumentResultType.IMAGING)).isFalse();
        assertThat(instrument.getUpdatedAt()).isEqualTo(later);

        instrument.activate(later.plusSeconds(60));
        assertThat(instrument.usableFor(ROOM, InstrumentResultType.IMAGING)).isTrue();
    }
}
