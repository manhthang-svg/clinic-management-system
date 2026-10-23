
package clinic.backend.entity;

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
            java.util.UUID id,
            String makhoa,
            String tenkhoa,
            DepartmentType type,
            String description,
            boolean active,
            java.time.Instant now) {
        super(id, now);
        this.makhoa = makhoa;
        this.tenkhoa = tenkhoa;
        this.type = type;
        this.description = description;
        this.active = active;
    }


    public void update(String tenkhoa, DepartmentType type, String description, boolean active, java.time.Instant now) {
        this.tenkhoa = tenkhoa;
        this.type = type;
        this.description = description;
        this.active = active;
        touch(now);
    }

    public void setActive(boolean active, java.time.Instant now) {
        this.active = active;
        touch(now);
    }
}
