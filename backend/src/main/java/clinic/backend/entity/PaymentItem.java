package clinic.backend.entity;

import java.math.BigDecimal;
import java.util.UUID;

import clinic.backend.enums.PriceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One line of a payment; its prices are snapshotted when created and never change (BR-09). Cancelling
 * the service only marks the line.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "payment_items")
public class PaymentItem extends CreatedOnlyEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, updatable = false)
    private Payment payment;

    @Column(name = "service_id", nullable = false, updatable = false)
    private UUID serviceId;

    @Column(name = "order_detail_id", updatable = false)
    private UUID orderDetailId;

    @Column(name = "service_code_snapshot", nullable = false, length = 50, updatable = false)
    private String serviceCodeSnapshot;

    /** CV 365 V.2.6.10. */
    @Column(name = "tenchidinh", nullable = false, length = 255, updatable = false)
    private String tenchidinh;

    /** CV 365 V.2.6.9. */
    @Column(name = "soluong", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal soluong;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_type", nullable = false, length = 20, updatable = false)
    private PriceType priceType;

    /** CV 365 V.2.6.4. */
    @Column(name = "dongia", nullable = false, precision = 15, scale = 0, updatable = false)
    private BigDecimal dongia;

    @Column(name = "coverage_rate", updatable = false)
    private Short coverageRate;

    /** CV 365 V.2.6.11. */
    @Column(name = "thanhtien", nullable = false, precision = 15, scale = 0, updatable = false)
    private BigDecimal thanhtien;

    @Column(name = "bhyt_chitra", nullable = false, precision = 15, scale = 0, updatable = false)
    private BigDecimal bhytChitra;

    /** CV 365 V.2.6.3. */
    @Column(name = "benhnhanphaitra", nullable = false, precision = 15, scale = 0, updatable = false)
    private BigDecimal benhnhanphaitra;

    @Column(name = "is_cancelled", nullable = false)
    private boolean isCancelled;
}
