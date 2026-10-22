package clinic.backend.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import clinic.backend.enums.ParaclinicalOrderDetailStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One ordered service on a paraclinical order (V.2.6).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "paraclinical_order_details", uniqueConstraints = {
        @UniqueConstraint(name = "uq_paraclinical_order_details_machidinh", columnNames = "machidinh")
})
public class ParaclinicalOrderDetail extends VersionedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private ParaclinicalOrder order;

    @Column(name = "service_id", nullable = false)
    private UUID serviceId;

    /** CV 365 V.2.6.1, VI.2.2, VII.2.13.1. */
    @Column(name = "machidinh", nullable = false, updatable = false, length = 255)
    private String machidinh;

    /** CV 365 V.2.6.2. */
    @Column(name = "ngaychidinh", nullable = false)
    private Instant ngaychidinh;

    /** CV 365 V.2.6.10. */
    @Column(name = "tenchidinh", nullable = false, length = 255)
    private String tenchidinh;

    /** CV 365 V.2.6.5. */
    @Column(name = "loaichidinh", length = 255)
    private String loaichidinh;

    /** CV 365 V.2.6.6. */
    @Column(name = "loaimachidinh", length = 255)
    private String loaimachidinh;

    /** CV 365 V.2.6.9. */
    @Column(name = "soluong", nullable = false, precision = 15, scale = 2)
    private BigDecimal soluong;

    /** CV 365 V.2.6.4. */
    @Column(name = "dongia", nullable = false, precision = 15, scale = 0)
    private BigDecimal dongia;

    /** CV 365 V.2.6.11. */
    @Column(name = "thanhtien", nullable = false, precision = 15, scale = 0)
    private BigDecimal thanhtien;

    @Column(name = "bhyt_chitra", nullable = false, precision = 15, scale = 0)
    private BigDecimal bhytChitra;

    /** CV 365 V.2.6.3. */
    @Column(name = "benhnhanphaitra", nullable = false, precision = 15, scale = 0)
    private BigDecimal benhnhanphaitra;

    @Column(name = "room_id")
    private UUID roomId;

    @Column(name = "queue_number")
    private Integer queueNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ParaclinicalOrderDetailStatus status;

    @Column(name = "is_skipped", nullable = false)
    private boolean isSkipped;

    @Column(name = "skipped_at")
    private Instant skippedAt;

    @Column(name = "performer_account_id")
    private UUID performerAccountId;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "performed_at")
    private Instant performedAt;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "cancelled_by_account_id")
    private UUID cancelledByAccountId;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;
}
