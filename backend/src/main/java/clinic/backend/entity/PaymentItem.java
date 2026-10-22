package clinic.backend.entity;

import java.math.BigDecimal;
import java.util.UUID;

import clinic.backend.enums.PriceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/**
 * One line of a payment; immutable once created (I1-D27).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "payment_items")
public class PaymentItem implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "service_id", nullable = false, updatable = false)
    private UUID serviceId;

    @Column(name = "service_code_snapshot", nullable = false, updatable = false, length = 50)
    private String serviceCodeSnapshot;

    @Column(name = "service_name_snapshot", nullable = false, updatable = false, length = 200)
    private String serviceNameSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_type", nullable = false, updatable = false, length = 20)
    private PriceType priceType;

    @Column(name = "applied_price", nullable = false, updatable = false, precision = 15, scale = 0)
    private BigDecimal appliedPrice;

    @Column(name = "coverage_rate", updatable = false)
    private Short coverageRate;

    @Column(name = "insurance_amount", nullable = false, updatable = false, precision = 15, scale = 0)
    private BigDecimal insuranceAmount;

    @Column(name = "patient_amount", nullable = false, updatable = false, precision = 15, scale = 0)
    private BigDecimal patientAmount;

    @Transient
    private boolean isNew = true;

    public PaymentItem(
            UUID id,
            UUID paymentId,
            UUID serviceId,
            String serviceCodeSnapshot,
            String serviceNameSnapshot,
            PriceType priceType,
            BigDecimal appliedPrice,
            Short coverageRate,
            BigDecimal insuranceAmount,
            BigDecimal patientAmount) {

        this.id = id;
        this.paymentId = paymentId;
        this.serviceId = serviceId;
        this.serviceCodeSnapshot = serviceCodeSnapshot;
        this.serviceNameSnapshot = serviceNameSnapshot;
        this.priceType = priceType;
        this.appliedPrice = appliedPrice;
        this.coverageRate = coverageRate;
        this.insuranceAmount = insuranceAmount;
        this.patientAmount = patientAmount;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    public void markNotNew() {
        isNew = false;
    }
}
