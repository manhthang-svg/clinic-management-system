
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
 * A receipt to collect for a visit.
 * Not an electronic invoice.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "payments",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_payments_payment_code",
                        columnNames = "payment_code"
                ),
                @UniqueConstraint(
                        name = "uq_payments_receipt_number",
                        columnNames = "receipt_number"
                ),
                @UniqueConstraint(
                        name = "uq_payments_payos_order_code",
                        columnNames = "payos_order_code"
                )
        }
)
public class Payment extends VersionedEntity {

    @Column(
            name = "payment_code",
            nullable = false,
            updatable = false,
            length = 20
    )
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

    @Column(
            name = "insurance_total_amount",
            nullable = false,
            precision = 15,
            scale = 0
    )
    private BigDecimal insuranceTotalAmount;

    @Column(
            name = "patient_total_amount",
            nullable = false,
            precision = 15,
            scale = 0
    )
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

    // PayOS information
    @Column(name = "payos_order_code", unique = true)
    private Long payosOrderCode;

    @Column(name = "payos_checkout_url", length = 1000)
    private String payosCheckoutUrl;

    @Column(name = "payos_qr_code", length = 2000)
    private String payosQrCode;

    @Column(name = "payos_payment_link_id", length = 100)
    private String payosPaymentLinkId;

    @Column(name = "payos_expired_at")
    private Instant payosExpiredAt;

    @Getter(AccessLevel.NONE)
    @OneToMany(
            mappedBy = "payment",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<PaymentItem> items = new ArrayList<>();

    /**
     * Khởi tạo Payment với ID và thời gian tạo.
     */
    public Payment(UUID id, Instant createdAt) {
        super(id, createdAt);
    }

    public List<PaymentItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public static Payment createExaminationFee(
            UUID id,
            Instant createdAt,
            String paymentCode,
            UUID visitId,
            BigDecimal totalAmount,
            BigDecimal insuranceTotalAmount,
            BigDecimal patientTotalAmount
    ) {
        if (id == null) {
            throw new IllegalArgumentException("Payment ID is required");
        }

        if (createdAt == null) {
            throw new IllegalArgumentException("Creation time is required");
        }

        if (paymentCode == null || paymentCode.isBlank()) {
            throw new IllegalArgumentException(
                    "Payment code is required"
            );
        }

        if (visitId == null) {
            throw new IllegalArgumentException(
                    "Visit ID is required"
            );
        }

        if (totalAmount == null
                || insuranceTotalAmount == null
                || patientTotalAmount == null) {
            throw new IllegalArgumentException(
                    "Payment amounts are required"
            );
        }

        if (totalAmount.signum() < 0
                || insuranceTotalAmount.signum() < 0
                || patientTotalAmount.signum() < 0) {
            throw new IllegalArgumentException(
                    "Payment amounts cannot be negative"
            );
        }

        if (insuranceTotalAmount.add(patientTotalAmount)
                .compareTo(totalAmount) != 0) {
            throw new IllegalArgumentException(
                    "Total amount must equal insurance amount plus patient amount"
            );
        }

        Payment payment = new Payment(id, createdAt);

        payment.paymentCode = paymentCode;
        payment.visitId = visitId;
        payment.type = PaymentType.EXAMINATION_FEE;
        payment.totalAmount = totalAmount;
        payment.insuranceTotalAmount = insuranceTotalAmount;
        payment.patientTotalAmount = patientTotalAmount;

        payment.status = patientTotalAmount.signum() == 0
                ? PaymentStatus.FEE_EXEMPT
                : PaymentStatus.PENDING;

        return payment;
    }

    public static Payment createParaclinicalFee(
            UUID id,
            Instant createdAt,
            String paymentCode,
            UUID visitId,
            BigDecimal totalAmount,
            BigDecimal insuranceTotalAmount,
            BigDecimal patientTotalAmount
    ) {
        if (id == null) {
            throw new IllegalArgumentException("Payment ID is required");
        }

        if (createdAt == null) {
            throw new IllegalArgumentException("Creation time is required");
        }

        if (paymentCode == null || paymentCode.isBlank()) {
            throw new IllegalArgumentException("Payment code is required");
        }

        if (visitId == null) {
            throw new IllegalArgumentException("Visit ID is required");
        }

        if (totalAmount == null
                || insuranceTotalAmount == null
                || patientTotalAmount == null) {
            throw new IllegalArgumentException("Payment amounts are required");
        }

        if (totalAmount.signum() < 0
                || insuranceTotalAmount.signum() < 0
                || patientTotalAmount.signum() < 0) {
            throw new IllegalArgumentException("Payment amounts cannot be negative");
        }

        if (insuranceTotalAmount.add(patientTotalAmount).compareTo(totalAmount) != 0) {
            throw new IllegalArgumentException(
                    "Total amount must equal insurance amount plus patient amount"
            );
        }

        Payment payment = new Payment(id, createdAt);

        payment.paymentCode = paymentCode;
        payment.visitId = visitId;
        payment.type = PaymentType.PARACLINICAL_FEE;
        payment.totalAmount = totalAmount;
        payment.insuranceTotalAmount = insuranceTotalAmount;
        payment.patientTotalAmount = patientTotalAmount;

        payment.status = patientTotalAmount.signum() == 0
                ? PaymentStatus.FEE_EXEMPT
                : PaymentStatus.PENDING;

        return payment;
    }

    public void addItem(PaymentItem item) {
        if (item == null) {
            throw new IllegalArgumentException(
                    "Chi tiết phiếu thu không được để trống"
            );
        }

        if (item.getPayment() != this) {
            throw new IllegalArgumentException(
                    "Chi tiết phiếu thu không thuộc phiếu thu này"
            );
        }

        this.items.add(item);
    }

    // PayOS
    public void attachPayOSPayment(
            Long orderCode,
            String checkoutUrl,
            String qrCode,
            String paymentLinkId,
            Instant expiredAt
    ) {
        if (this.status != PaymentStatus.PENDING) {
            throw new IllegalStateException(
                    "Chỉ phiếu thu đang chờ thanh toán mới được tạo QR"
            );
        }

        if (orderCode == null
                || checkoutUrl == null
                || checkoutUrl.isBlank()
                || qrCode == null
                || qrCode.isBlank()) {
            throw new IllegalArgumentException(
                    "Thông tin PayOS không hợp lệ"
            );
        }

        this.payosOrderCode = orderCode;
        this.payosCheckoutUrl = checkoutUrl;
        this.payosQrCode = qrCode;
        this.payosPaymentLinkId = paymentLinkId;
        this.payosExpiredAt = expiredAt;
        this.paymentMethod = PaymentMethod.BANK_TRANSFER;
    }

    public void markAsPaid(
            PaymentMethod method,
            UUID cashierAccountId,
            String receiptNumber
    ) {
        if (this.status != PaymentStatus.PENDING) {
            throw new IllegalStateException(
                    "Phiếu thu không ở trạng thái chờ thanh toán"
            );
        }

        if (method == null) {
            throw new IllegalArgumentException(
                    "Phương thức thanh toán không hợp lệ"
            );
        }

        if (cashierAccountId == null) {
            throw new IllegalArgumentException(
                    "Không xác định được tài khoản thu ngân"
            );
        }

        if (receiptNumber == null || receiptNumber.isBlank()) {
            throw new IllegalArgumentException(
                    "Số biên lai không được để trống"
            );
        }

        this.status = PaymentStatus.PAID;
        this.paymentMethod = method;
        this.cashierAccountId = cashierAccountId;
        this.receiptNumber = receiptNumber;
        this.paidAt = Instant.now();
    }


    /** End-of-day job: an unpaid receipt is cancelled; paid receipts are never cancelled or refunded (BR-22, Q-10). */
    public void cancelAtEndOfDay(Instant now) {
        if (this.status != PaymentStatus.PENDING) {
            throw new IllegalStateException("Phiếu thu không ở trạng thái chờ thanh toán");
        }
        this.status = PaymentStatus.CANCELLED;
        this.cancelledAt = now;
        touch(now);
    }

    public void markAsPaidByPayOS(String receiptNumber) {
        if (this.status != PaymentStatus.PENDING) {
            throw new IllegalStateException(
                    "Phiếu thu không ở trạng thái chờ thanh toán"
            );
        }

        if (receiptNumber == null || receiptNumber.isBlank()) {
            throw new IllegalArgumentException(
                    "Số phiếu thu không được để trống"
            );
        }

        this.status = PaymentStatus.PAID;
        this.paymentMethod = PaymentMethod.BANK_TRANSFER;
        this.receiptNumber = receiptNumber;
        this.paidAt = Instant.now();
    }
}
