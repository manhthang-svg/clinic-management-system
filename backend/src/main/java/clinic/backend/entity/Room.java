package clinic.backend.entity;

import java.time.Instant;
import java.util.Objects;
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
                @UniqueConstraint(name = "uq_rooms_maphongkham", columnNames = "maphongkham")
        }
)
public class Room extends VersionedEntity {

    @Column(name = "department_id", nullable = false, updatable = false)
    private UUID departmentId;

    @Column(name = "service_group_id")
    private UUID serviceGroupId;

    @Column(name = "maphongkham", nullable = false, updatable = false, length = 255)
    private String roomCode;

    @Column(name = "phongkham", nullable = false, length = 255)
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
            ActivationStatus status) {
        super(id, createdAt);
        this.departmentId = departmentId;
        this.serviceGroupId = serviceGroupId;
        this.roomCode = roomCode;
        this.roomName = roomName;
        this.location = location;
        this.type = type;
        this.status = status;
    }

    public void update(
            UUID departmentId,
            UUID serviceGroupId,
            String roomName,
            String location,
            RoomType type,
            ActivationStatus status,
            Instant now) {
        if (departmentId != null && !Objects.equals(this.departmentId, departmentId)) {
            throw new IllegalArgumentException("Không thể thay đổi chuyên khoa trực thuộc của phòng");
        }
        if (type != null && this.type != type) {
            throw new IllegalArgumentException("Không thể thay đổi loại phòng của phòng");
        }
        this.serviceGroupId = serviceGroupId;
        this.roomName = roomName;
        this.location = location;
        this.status = status;
        touch(now);
    }

    public void update(
            UUID departmentId,
            String roomName,
            String location,
            RoomType type,
            ActivationStatus status,
            Instant now) {
        update(departmentId, this.serviceGroupId, roomName, location, type, status, now);
    }

    public void setStatus(ActivationStatus status, Instant now) {
        this.status = status;
        touch(now);
    }
}
