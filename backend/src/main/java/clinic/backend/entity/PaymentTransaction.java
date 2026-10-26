
package clinic.backend.entity;

import java.math.BigDecimal;
import java.time.Instant;

import clinic.backend.enums.PaymentTransactionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "payment_transactions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_payment_transactions_order_code",
                        columnNames = "provider_order_code"
                )
        }
)
public class PaymentTransaction extends VersionedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "payment_id",
            nullable = false,
            updatable = false
    )
    private Payment payment;

    // Mã đơn hàng gửi sang PayOS.
    @Column(
            name = "provider_order_code",
            nullable = false,
            updatable = false
    )
    private Long providerOrderCode;

    // Số tiền của lần giao dịch này.
    @Column(name = "amount", nullable = false, precision = 15, scale = 0)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentTransactionStatus status;

    // URL thanh toán do PayOS trả về.
    @Column(name = "checkout_url", length = 2048)
    private String checkoutUrl;

    // Mã tham chiếu giao dịch do PayOS cung cấp.
    @Column(name = "provider_reference", length = 255)
    private String providerReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    public static PaymentTransaction create(
            Payment payment,
            Long providerOrderCode,
            BigDecimal amount,
            String checkoutUrl
    ) {
        if (payment == null
                || providerOrderCode == null
                || providerOrderCode <= 0
                || amount == null
                || amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Invalid payment transaction data"
            );
        }

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.payment = payment;
        transaction.providerOrderCode = providerOrderCode;
        transaction.amount = amount;
        transaction.checkoutUrl = checkoutUrl;
        transaction.status = PaymentTransactionStatus.PENDING;

        return transaction;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void markPaid(String providerReference) {
        if (status == PaymentTransactionStatus.PAID) {
            return;
        }

        if (status != PaymentTransactionStatus.PENDING) {
            throw new IllegalStateException(
                    "Chỉ giao dịch đang chờ thanh toán mới được chuyển sang đã thanh toán"
            );
        }

        this.status = PaymentTransactionStatus.PAID;
        this.providerReference = providerReference;
        this.paidAt = Instant.now();
        this.lastError = null;
    }

    public void markFailed(String reason) {
        if (status != PaymentTransactionStatus.PENDING) {
            return;
        }

        this.status = PaymentTransactionStatus.FAILED;
        this.lastError = reason;
    }

    public void markCancelled() {
        if (status != PaymentTransactionStatus.PENDING) {
            throw new IllegalStateException(
                    "Chỉ giao dịch đang chờ thanh toán mới được phép hủy"
            );
        }

        this.status = PaymentTransactionStatus.CANCELLED;
    }

    public void markExpired() {
        if (status != PaymentTransactionStatus.PENDING) {
            return;
        }

        this.status = PaymentTransactionStatus.EXPIRED;
    }
}
