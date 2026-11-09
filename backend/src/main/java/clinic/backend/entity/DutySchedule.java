package clinic.backend.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import clinic.backend.enums.DutyShift;
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
        name = "duty_schedules",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_duty_schedules_staff_date_shift",
                        columnNames = {"work_date", "shift", "staff_account_id"}
                )
        }
)
public class DutySchedule extends VersionedEntity {

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "shift", nullable = false, length = 20)
    private DutyShift shift;

    @Column(name = "staff_account_id", nullable = false)
    private UUID staffAccountId;

    @Column(name = "room_id")
    private UUID roomId;

    @Column(name = "notes", length = 255)
    private String notes;

    @Column(name = "assigned_by_account_id")
    private UUID assignedByAccountId;

    public DutySchedule(
            UUID id,
            Instant createdAt,
            LocalDate workDate,
            DutyShift shift,
            UUID staffAccountId,
            UUID roomId,
            String notes,
            UUID assignedByAccountId
    ) {
        super(id, createdAt);
        this.workDate = Objects.requireNonNull(workDate, "workDate");
        this.shift = Objects.requireNonNull(shift, "shift");
        this.staffAccountId = Objects.requireNonNull(staffAccountId, "staffAccountId");
        this.roomId = roomId;
        this.notes = notes;
        this.assignedByAccountId = assignedByAccountId;
    }

    public void update(UUID staffAccountId, UUID roomId, String notes, UUID assignedByAccountId, Instant now) {
        if (staffAccountId != null) {
            this.staffAccountId = staffAccountId;
        }
        this.roomId = roomId;
        this.notes = notes;
        this.assignedByAccountId = assignedByAccountId;
        touch(now);
    }

    public void update(UUID roomId, String notes, UUID assignedByAccountId, Instant now) {
        update(null, roomId, notes, assignedByAccountId, now);
    }
}
