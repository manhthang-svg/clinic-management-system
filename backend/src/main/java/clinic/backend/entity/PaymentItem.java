package clinic.backend.entity;

import java.math.BigDecimal;
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

    @Column(name = "coverage_rate", updatable = false)
    private Short coverageRate;

    @Column(name = "thanhtien", nullable = false, precision = 15, scale = 0, updatable = false)
    private BigDecimal thanhtien;

    @Column(name = "bhyt_chitra", nullable = false, precision = 15, scale = 0, updatable = false)
    private BigDecimal bhytChitra;

    @Column(name = "benhnhanphaitra", nullable = false, precision = 15, scale = 0, updatable = false)
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
        item.bhytChitra = BigDecimal.ZERO;
        item.benhnhanphaitra = item.thanhtien;
        item.isCancelled = false;

        return item;
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
        item.bhytChitra = BigDecimal.ZERO;
        item.benhnhanphaitra = item.thanhtien;
        item.isCancelled = false;

        return item;
    }
}
