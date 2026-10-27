package clinic.backend.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
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

    private static final BigDecimal MAX_PRICE = new BigDecimal("100000000");

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

    private MedicalService(
            UUID id,
            Instant now,
            String code,
            String name,
            MedicalServiceType type,
            UUID departmentId,
            UUID serviceGroupId,
            BigDecimal clinicPrice,
            BigDecimal insurancePrice,
            String description) {
        super(id, now);
        this.code = requiredText(code, 50, "code");
        this.tenchidinh = requiredText(name, 255, "name");
        this.type = Objects.requireNonNull(type, "type");
        requireOwnership(type, departmentId, serviceGroupId);
        requirePrice(clinicPrice, false, "clinicPrice");
        requirePrice(insurancePrice, true, "insurancePrice");
        this.departmentId = departmentId;
        this.serviceGroupId = serviceGroupId;
        this.clinicPrice = clinicPrice;
        this.insurancePrice = insurancePrice;
        this.description = description;
        this.status = ActivationStatus.ACTIVE;
    }

    public static MedicalService create(
            UUID id,
            Instant now,
            String code,
            String name,
            MedicalServiceType type,
            UUID departmentId,
            UUID serviceGroupId,
            BigDecimal clinicPrice,
            BigDecimal insurancePrice,
            String description) {
        return new MedicalService(
                id, now, code, name, type, departmentId, serviceGroupId,
                clinicPrice, insurancePrice, description);
    }

    public void updateInformation(String name, String description, Instant now) {
        this.tenchidinh = requiredText(name, 255, "name");
        this.description = description;
        touch(now);
    }

    public void updatePrices(BigDecimal clinicPrice, BigDecimal insurancePrice, Instant now) {
        requirePrice(clinicPrice, false, "clinicPrice");
        requirePrice(insurancePrice, true, "insurancePrice");
        this.clinicPrice = clinicPrice;
        this.insurancePrice = insurancePrice;
        touch(now);
    }

    public void changeStatus(ActivationStatus status, Instant now) {
        this.status = Objects.requireNonNull(status, "status");
        touch(now);
    }

    private static void requireOwnership(MedicalServiceType type, UUID departmentId, UUID serviceGroupId) {
        boolean valid = type == MedicalServiceType.EXAMINATION
                ? departmentId == null && serviceGroupId == null
                : departmentId != null && serviceGroupId != null;
        if (!valid) {
            throw new IllegalArgumentException("Service ownership does not match its type");
        }
    }

    private static void requirePrice(BigDecimal price, boolean nullable, String field) {
        if (price == null) {
            if (nullable) {
                return;
            }
            throw new IllegalArgumentException(field + " is required");
        }
        BigDecimal minimum = nullable ? BigDecimal.ZERO : BigDecimal.ONE;
        if (price.stripTrailingZeros().scale() > 0
                || price.compareTo(minimum) < 0
                || price.compareTo(MAX_PRICE) > 0) {
            throw new IllegalArgumentException(field + " is outside the allowed whole-VND range");
        }
    }

    private static String requiredText(String value, int maximumLength, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        if (value.length() > maximumLength) {
            throw new IllegalArgumentException(field + " is too long");
        }
        return value;
    }
}
