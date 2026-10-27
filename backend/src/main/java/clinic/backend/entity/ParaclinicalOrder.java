package clinic.backend.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import clinic.backend.enums.ParaclinicalOrderStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A paraclinical order form (export group V).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "paraclinical_orders", uniqueConstraints = {
        @UniqueConstraint(name = "uq_paraclinical_orders_sophieu", columnNames = "sophieu"),
        @UniqueConstraint(name = "uq_paraclinical_orders_payment_id", columnNames = "payment_id")
})
public class ParaclinicalOrder extends VersionedEntity {

    /** CV 365 V.2.1. */
    @Column(name = "sophieu", nullable = false, updatable = false, length = 100)
    private String sophieu;

    @Column(name = "medical_record_id", nullable = false)
    private UUID medicalRecordId;

    @Column(name = "doctor_account_id", nullable = false)
    private UUID doctorAccountId;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    /** CV 365 V.2.4. */
    @Column(name = "chandoansobo")
    private String chandoansobo;

    /** CV 365 V.2.5; 1 insurance, 2 self-paid. */
    @Column(name = "doituongbn", nullable = false)
    private short doituongbn;

    /** CV 365 V.2.11. */
    @Column(name = "mucdochidinh", nullable = false, length = 255)
    private String mucdochidinh;

    /** CV 365 V.2.13. */
    @Column(name = "noichidinh", nullable = false, length = 255)
    private String noichidinh;

    /** CV 365 V.2.15. */
    @Column(name = "tongcong", nullable = false, precision = 15, scale = 0)
    private BigDecimal tongcong;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ParaclinicalOrderStatus status;

    @Getter(AccessLevel.NONE)
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ParaclinicalOrderDetail> details = new ArrayList<>();

    public ParaclinicalOrder(
            UUID id,
            Instant createdAt,
            String sophieu,
            UUID medicalRecordId,
            UUID doctorAccountId,
            UUID paymentId,
            String chandoansobo,
            short doituongbn,
            String mucdochidinh,
            String noichidinh,
            BigDecimal tongcong,
            ParaclinicalOrderStatus status
    ) {
        super(id, createdAt);
        this.sophieu = sophieu;
        this.medicalRecordId = medicalRecordId;
        this.doctorAccountId = doctorAccountId;
        this.paymentId = paymentId;
        this.chandoansobo = chandoansobo;
        this.doituongbn = doituongbn;
        this.mucdochidinh = mucdochidinh != null ? mucdochidinh : "Thường";
        this.noichidinh = noichidinh;
        this.tongcong = tongcong;
        this.status = status;
    }

    public List<ParaclinicalOrderDetail> getDetails() {
        return Collections.unmodifiableList(details);
    }

    public void addDetail(ParaclinicalOrderDetail detail) {
        if (detail != null) {
            this.details.add(detail);
        }
    }

    public void release() {
        if (this.status == ParaclinicalOrderStatus.PENDING_PAYMENT) {
            this.status = ParaclinicalOrderStatus.RELEASED;
        }
    }
}
