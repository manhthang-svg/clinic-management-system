
package clinic.backend.entity;

import java.util.UUID;

import clinic.backend.enums.ServiceGroupType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "service_groups", uniqueConstraints = {
        @UniqueConstraint(name = "uq_service_groups_loaimachidinh", columnNames = "loaimachidinh")
})
public class ServiceGroup extends VersionedEntity {

    @Column(name = "loaimachidinh", nullable = false, updatable = false, length = 255)
    private String serviceGroupCode;

    @Column(name = "loaichidinh", nullable = false, length = 255)
    private String serviceGroupName;


    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private ServiceGroupType type;

    @Column(name = "department_id")
    private UUID departmentId;
}
