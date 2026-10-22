package clinic.backend.entity;

import java.math.BigDecimal;
import java.util.UUID;

import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.MedicalServiceType;
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
 * A billable service. Named MedicalService to avoid confusion with Spring's {@code @Service}.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "services", uniqueConstraints = {
        @UniqueConstraint(name = "uq_services_code", columnNames = "code")
})
public class MedicalService extends VersionedEntity {

    /** Technical-service code from the Ministry of Health list (I1-D40). */
    @Column(name = "code", nullable = false, updatable = false, length = 50)
    private String code;

    @Column(name = "tenchidinh", nullable = false, length = 255)
    private String tenchidinh;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private MedicalServiceType type;

    /** Null for the shared examination service (I1-D06). */
    @Column(name = "department_id")
    private UUID departmentId;

    /** Null for the shared examination service (I1-D06). */
    @Column(name = "service_group_id")
    private UUID serviceGroupId;

    @Column(name = "clinic_price", nullable = false, precision = 15, scale = 0)
    private BigDecimal clinicPrice;

    /** Null when the service is not covered by insurance (I1-D22). */
    @Column(name = "insurance_price", precision = 15, scale = 0)
    private BigDecimal insurancePrice;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ActivationStatus status;
}
