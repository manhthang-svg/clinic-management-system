package clinic.backend.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
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

    @Column(name = "tenchidinh", nullable = false, length = 255, updatable = false)
    private String tenchidinh;

    @Column(name = "soluong", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal soluong;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_type", nullable = false, length = 20, updatable = false)
    private PriceType priceType;

    @Column(name = "dongia", nullable = false, precision = 15, scale = 0, updatable = false)
    private BigDecimal dongia;

    /** Applied coverage rate of an insurance line; changes with the visit's total in scope until paid (BR-27). */
    @Column(name = "coverage_rate")
    private Short coverageRate;

    @Column(name = "thanhtien", nullable = false, precision = 15, scale = 0, updatable = false)
    private BigDecimal thanhtien;

    /** BR-27: min(insurance price, clinic price) at creation time (BR-09); 0 for a clinic-price line. */
    @Column(name = "insurance_scope_amount", nullable = false, precision = 15, scale = 0, updatable = false)
    private BigDecimal insuranceScopeAmount;

    @Column(name = "bhyt_chitra", nullable = false, precision = 15, scale = 0)
    private BigDecimal bhytChitra;

    @Column(name = "benhnhanphaitra", nullable = false, precision = 15, scale = 0)
    private BigDecimal benhnhanphaitra;

    @Column(name = "is_cancelled", nullable = false)
    private boolean isCancelled;

    public PaymentItem(UUID id, Instant createdAt) {
        super(id, createdAt);
    }

    public static PaymentItem createClinicItem(
            UUID id,
            Instant createdAt,
            Payment payment,
            MedicalService service,
            BigDecimal quantity
    ) {
        if (id == null || createdAt == null
                || payment == null || service == null || quantity == null
                || quantity.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Thông tin chi tiết phiếu thu không hợp lệ"
            );
        }

        BigDecimal unitPrice = service.getClinicPrice();

        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new IllegalArgumentException(
                    "Giá dịch vụ phòng khám không hợp lệ"
            );
        }

        PaymentItem item = new PaymentItem(id, createdAt);
        item.payment = payment;
        item.serviceId = service.getId();
        item.serviceCodeSnapshot = service.getCode();
        item.tenchidinh = service.getTenchidinh();
        item.soluong = quantity;
        item.priceType = PriceType.CLINIC;
        item.dongia = unitPrice;
        item.coverageRate = null;
        item.thanhtien = unitPrice.multiply(quantity);
        item.insuranceScopeAmount = BigDecimal.ZERO;
        item.bhytChitra = BigDecimal.ZERO;
        item.benhnhanphaitra = item.thanhtien;
        item.isCancelled = false;

        return item;
    }

    /** UC-40 examination fee of a valid-card insurance visit, priced under BR-27 at the given coverage rate. */
    public static PaymentItem createInsuranceItem(
            UUID id,
            Instant createdAt,
            Payment payment,
            MedicalService service,
            short coverageRate
    ) {
        return insuranceLine(id, createdAt, payment, service, coverageRate, null);
    }

    public static PaymentItem createParaclinicalItem(
            UUID id,
            Instant createdAt,
            Payment payment,
            MedicalService service,
            BigDecimal quantity,
            UUID orderDetailId
    ) {
        if (id == null || createdAt == null
                || payment == null || service == null || quantity == null
                || quantity.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Thông tin chi tiết phiếu thu không hợp lệ"
            );
        }

        BigDecimal unitPrice = service.getClinicPrice();

        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new IllegalArgumentException(
                    "Giá dịch vụ phòng khám không hợp lệ"
            );
        }

        PaymentItem item = new PaymentItem(id, createdAt);
        item.payment = payment;
        item.serviceId = service.getId();
        item.orderDetailId = orderDetailId;
        item.serviceCodeSnapshot = service.getCode();
        item.tenchidinh = service.getTenchidinh();
        item.soluong = quantity;
        item.priceType = PriceType.CLINIC;
        item.dongia = unitPrice;
        item.coverageRate = null;
        item.thanhtien = unitPrice.multiply(quantity);
        item.insuranceScopeAmount = BigDecimal.ZERO;
        item.bhytChitra = BigDecimal.ZERO;
        item.benhnhanphaitra = item.thanhtien;
        item.isCancelled = false;

        return item;
    }

    /** UC-49 line of a valid-card insurance visit for a service with an insurance price (BR-27). */
    public static PaymentItem createParaclinicalInsuranceItem(
            UUID id,
            Instant createdAt,
            Payment payment,
            MedicalService service,
            short coverageRate,
            UUID orderDetailId
    ) {
        return insuranceLine(id, createdAt, payment, service, coverageRate, orderDetailId);
    }

    /**
     * BR-27: the line is charged at the clinic price; insurance pays the cost within scope,
     * min(insurance price, clinic price), times the applied coverage rate, and the patient pays the rest
     * (co-payment plus the difference between the clinic price and the insurance price).
     */
    private static PaymentItem insuranceLine(
            UUID id, Instant createdAt, Payment payment, MedicalService service, short coverageRate,
            UUID orderDetailId) {
        if (id == null || createdAt == null || payment == null || service == null
                || !isValidRate(coverageRate) || service.getInsurancePrice() == null) {
            throw new IllegalArgumentException("Thông tin chi tiết BHYT không hợp lệ");
        }
        BigDecimal clinicPrice = service.getClinicPrice();
        if (clinicPrice == null || clinicPrice.signum() < 0) {
            throw new IllegalArgumentException("Giá dịch vụ phòng khám không hợp lệ");
        }

        PaymentItem item = new PaymentItem(id, createdAt);
        item.payment = payment;
        item.serviceId = service.getId();
        item.orderDetailId = orderDetailId;
        item.serviceCodeSnapshot = service.getCode();
        item.tenchidinh = service.getTenchidinh();
        item.soluong = BigDecimal.ONE;
        item.priceType = PriceType.INSURANCE;
        item.dongia = clinicPrice;
        item.thanhtien = clinicPrice;
        item.insuranceScopeAmount = insuranceScope(service);
        item.isCancelled = false;
        item.applyCoverage(coverageRate);
        return item;
    }

    /** BR-27: cost of a service within insurance scope, or 0 when it has no insurance price. */
    public static BigDecimal insuranceScope(MedicalService service) {
        if (service.getInsurancePrice() == null) {
            return BigDecimal.ZERO;
        }
        return service.getInsurancePrice().min(service.getClinicPrice());
    }

    /** BR-27: insurance-covered amount of a cost within scope, rounded half up to whole VND. */
    public static BigDecimal insuranceAmount(BigDecimal scopeAmount, short coverageRate) {
        return scopeAmount.multiply(BigDecimal.valueOf(coverageRate))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
    }

    /**
     * BR-27: reprices an insurance line at the visit's new applied coverage rate. Only the owning receipt calls
     * this, while it is unpaid; a clinic-price line never changes.
     */
    void reprice(short appliedCoverageRate) {
        if (priceType != PriceType.INSURANCE) {
            return;
        }
        if (!isValidRate(appliedCoverageRate)) {
            throw new IllegalArgumentException("Mức hưởng BHYT không hợp lệ");
        }
        applyCoverage(appliedCoverageRate);
    }

    /** BR-25: the line of a cancelled service no longer counts in the receipt. Only the owning receipt calls this. */
    void cancel() {
        this.isCancelled = true;
    }

    private void applyCoverage(short rate) {
        this.coverageRate = rate;
        this.bhytChitra = insuranceAmount(insuranceScopeAmount, rate);
        this.benhnhanphaitra = thanhtien.subtract(bhytChitra);
    }

    private static boolean isValidRate(short rate) {
        return rate >= 1 && rate <= 100;
    }
}
