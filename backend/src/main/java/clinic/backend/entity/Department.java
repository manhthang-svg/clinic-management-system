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

/**
 * A department from the department code list (I1-D40). Created by the demo seed or an approved data
 * migration (I1-D01); its behavior methods arrive with UC-14.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "departments", uniqueConstraints = {
        @UniqueConstraint(name = "uq_departments_makhoa", columnNames = "makhoa")
})
public class Department extends VersionedEntity {

    @Column(name = "makhoa", nullable = false, length = 50)
    private String makhoa;

    @Column(name = "tenkhoa", nullable = false, length = 255)
    private String tenkhoa;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private DepartmentType type;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean active;
}
