package clinic.backend.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import clinic.backend.enums.InstrumentResultType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * An instrument of a paraclinical room (V54). Results are entered by hand; the instrument only records which
 * machine ran a lab value or an imaging result (UC-67, ASSUMPTION until the SRS is updated).
 * An instrument that was used is never deleted, only deactivated.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "paraclinical_instruments")
public class ParaclinicalInstrument extends VersionedEntity {

    @Column(name = "code", nullable = false, updatable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_type", nullable = false, updatable = false, length = 20)
    private InstrumentResultType resultType;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    public ParaclinicalInstrument(
            UUID id, Instant createdAt, String code, String name, InstrumentResultType resultType, UUID roomId) {
        super(id, createdAt);
        this.code = Objects.requireNonNull(code, "code").strip();
        this.name = Objects.requireNonNull(name, "name").strip();
        this.resultType = Objects.requireNonNull(resultType, "resultType");
        this.roomId = Objects.requireNonNull(roomId, "roomId");
        this.active = true;
    }

    /** True when the instrument may be picked for a result of this room and type. */
    public boolean usableFor(UUID resultRoomId, InstrumentResultType type) {
        return active && resultType == type && roomId.equals(resultRoomId);
    }

    public void deactivate(Instant now) {
        active = false;
        touch(now);
    }

    public void activate(Instant now) {
        active = true;
        touch(now);
    }
}
