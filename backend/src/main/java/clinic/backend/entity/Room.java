package clinic.backend.entity;

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

/**
 * An examination or paraclinical room (BR-06, I1-D05, I1-D40). Its behavior methods arrive with UC-17 to UC-19.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "rooms", uniqueConstraints = {
        @UniqueConstraint(name = "uq_rooms_maphongkham", columnNames = "maphongkham")
})
public class Room extends VersionedEntity {

    @Column(name = "department_id", nullable = false, updatable = false)
    private UUID departmentId;

    /** The service group a paraclinical room performs; null for an examination room (BR-06). */
    @Column(name = "service_group_id")
    private UUID serviceGroupId;

    @Column(name = "maphongkham", nullable = false, updatable = false, length = 255)
    private String maphongkham;

    @Column(name = "phongkham", nullable = false, length = 255)
    private String phongkham;

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
            UUID departmentId,
            UUID serviceGroupId,
            String maphongkham,
            String phongkham,
            String location,
            RoomType type,
            ActivationStatus status,
            java.time.Instant now) {
        super(id, now);
        this.departmentId = departmentId;
        this.serviceGroupId = serviceGroupId;
        this.maphongkham = maphongkham;
        this.phongkham = phongkham;
        this.location = location;
        this.type = type;
        this.status = status;
    }

    public void update(
            UUID departmentId,
            String phongkham,
            String location,
            RoomType type,
            ActivationStatus status,
            java.time.Instant now) {
        this.departmentId = departmentId;
        this.phongkham = phongkham;
        this.location = location;
        this.type = type;
        this.status = status;
        touch(now);
    }


    public void setStatus(ActivationStatus status, java.time.Instant now) {
        this.status = status;
        touch(now);
    }
}
