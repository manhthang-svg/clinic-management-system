package clinic.backend.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.MedicalServiceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A billable service. Named MedicalService to avoid confusion with Spring's {@code @Service}.
 */
@Entity
@Table(name = "services")
public class MedicalServiceEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, updatable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private MedicalServiceType type;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "service_group_id")
    private UUID serviceGroupId;

    @Column(name = "clinic_price", nullable = false, precision = 15, scale = 0)
    private BigDecimal clinicPrice;

    @Column(name = "insurance_price", precision = 15, scale = 0)
    private BigDecimal insurancePrice;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ActivationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // A null version marks a new entity for Spring Data.
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected MedicalServiceEntity() {
    }

    public MedicalServiceEntity(
            UUID id,
            String code,
            String name,
            MedicalServiceType type,
            UUID departmentId,
            UUID serviceGroupId,
            BigDecimal clinicPrice,
            BigDecimal insurancePrice,
            String description,
            ActivationStatus status,
            Instant createdAt,
            Instant updatedAt,
            Long version) {

        this.id = id;
        this.code = code;
        this.name = name;
        this.type = type;
        this.departmentId = departmentId;
        this.serviceGroupId = serviceGroupId;
        this.clinicPrice = clinicPrice;
        this.insurancePrice = insurancePrice;
        this.description = description;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public MedicalServiceType getType() {
        return type;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public UUID getServiceGroupId() {
        return serviceGroupId;
    }

    public BigDecimal getClinicPrice() {
        return clinicPrice;
    }

    public BigDecimal getInsurancePrice() {
        return insurancePrice;
    }

    public String getDescription() {
        return description;
    }

    public ActivationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
