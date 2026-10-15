package clinic.backend.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import clinic.backend.enums.PaymentStatus;
import clinic.backend.enums.PaymentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "payments")
public class PaymentEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "invoice_number", nullable = false, updatable = false, length = 20)
    private String invoiceNumber;

    @Column(name = "visit_id", nullable = false, updatable = false)
    private UUID visitId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 20)
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

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // A null version marks a new entity for Spring Data.
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected PaymentEntity() {
    }

    public PaymentEntity(
            UUID id,
            String invoiceNumber,
            UUID visitId,
            PaymentType type,
            PaymentStatus status,
            BigDecimal totalAmount,
            BigDecimal insuranceTotalAmount,
            BigDecimal patientTotalAmount,
            Instant paidAt,
            Instant createdAt,
            Instant updatedAt,
            Long version) {

        this.id = id;
        this.invoiceNumber = invoiceNumber;
        this.visitId = visitId;
        this.type = type;
        this.status = status;
        this.totalAmount = totalAmount;
        this.insuranceTotalAmount = insuranceTotalAmount;
        this.patientTotalAmount = patientTotalAmount;
        this.paidAt = paidAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public UUID getId() {
        return id;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public UUID getVisitId() {
        return visitId;
    }

    public PaymentType getType() {
        return type;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public BigDecimal getInsuranceTotalAmount() {
        return insuranceTotalAmount;
    }

    public BigDecimal getPatientTotalAmount() {
        return patientTotalAmount;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
