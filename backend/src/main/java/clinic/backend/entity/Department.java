
package clinic.backend.entity;

import java.time.Instant;
import java.util.UUID;

import clinic.backend.enums.DepartmentType;
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
@Table(name = "departments", uniqueConstraints = {
        @UniqueConstraint(name = "uq_departments_makhoa", columnNames = "makhoa")
})
public class Department extends VersionedEntity {

    @Column(name = "makhoa", nullable = false, length = 50)
    private String departmentCode;

    @Column(name = "tenkhoa", nullable = false, length = 255)
    private String departmentName;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private DepartmentType type;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    public Department(
            UUID id,
            String departmentCode,
            String departmentName,
            DepartmentType type,
            String description,
            boolean active,
            Instant now) {
        super(id, now);
        this.departmentCode = departmentCode;
        this.departmentName = departmentName;
        this.type = type;
        this.description = description;
        this.active = active;
    }

    public void update(
            String departmentName,
            DepartmentType type,
            String description,
            boolean active,
            Instant now) {
        this.departmentName = departmentName;
        this.type = type;
        this.description = description;
        this.active = active;
        touch(now);
    }

    public void setActive(boolean active, Instant now) {
        this.active = active;
        touch(now);
    }
}
