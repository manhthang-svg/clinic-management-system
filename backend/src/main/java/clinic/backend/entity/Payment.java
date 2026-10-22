package clinic.backend.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import clinic.backend.enums.PaymentMethod;
import clinic.backend.enums.PaymentStatus;
import clinic.backend.enums.PaymentType;
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
 * A receipt to collect for a visit. Not an electronic invoice.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "payments", uniqueConstraints = {
        @UniqueConstraint(name = "uq_payments_payment_code", columnNames = "payment_code"),
        @UniqueConstraint(name = "uq_payments_receipt_number", columnNames = "receipt_number")
})
public class Payment extends VersionedEntity {

    @Column(name = "payment_code", nullable = false, updatable = false, length = 20)
    private String paymentCode;

    @Column(name = "visit_id", nullable = false)
    private UUID visitId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private PaymentType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 0)
    private BigDecimal totalAmount;

    @Column(name = "insurance_total_amount", nullable = false, precision = 15, scale = 0)
    private BigDecimal insuranceTotalAmount;

    @Column(name = "patient_total_amount", nullable = false, precision = 15, scale = 0)
    private BigDecimal patientTotalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "receipt_number", length = 100)
    private String receiptNumber;

    @Column(name = "cashier_account_id")
    private UUID cashierAccountId;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "note")
    private String note;

    @Getter(AccessLevel.NONE)
    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PaymentItem> items = new ArrayList<>();

    public List<PaymentItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
