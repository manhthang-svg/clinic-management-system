package clinic.backend.entity;

import java.time.Instant;
import java.util.UUID;

import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.RoomType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "rooms",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_rooms_room_code",
                        columnNames = "room_code"
                )
        }
)
public class Room extends VersionedEntity {

    @Column(name = "department_id", nullable = false, updatable = false)
    private UUID departmentId;

    @Column(name = "service_group_id")
    private UUID serviceGroupId;

    @Column(name = "room_code", nullable = false, updatable = false, length = 20)
    private String roomCode;

    @Column(name = "room_name", nullable = false, length = 100)
    private String roomName;

    @Column(name = "location", nullable = false, length = 255)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 20)
    private RoomType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ActivationStatus status;

    public Room(
            UUID id,
            Instant createdAt,
            UUID departmentId,
            UUID serviceGroupId,
            String roomCode,
            String roomName,
            String location,
            RoomType type,
            ActivationStatus status
    ) {
        super(id, createdAt);

        this.departmentId = departmentId;
        this.serviceGroupId = serviceGroupId;
        this.roomCode = roomCode;
        this.roomName = roomName;
        this.location = location;
        this.type = type;
        this.status = status;
    }
}