package clinic.backend.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import clinic.backend.enums.PriorityReason;
import clinic.backend.enums.VisitStatus;
import clinic.backend.enums.VisitType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "visits")
public class VisitEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "registration_code", nullable = false, updatable = false, length = 20)
    private String registrationCode;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "registered_by_account_id", nullable = false, updatable = false)
    private UUID registeredByAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "visit_type", nullable = false, length = 20)
    private VisitType visitType;

    @Column(name = "priority", nullable = false)
    private boolean priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority_reason", length = 20)
    private PriorityReason priorityReason;

    @Column(name = "reason_for_visit", nullable = false, length = 500)
    private String reasonForVisit;

    @Column(name = "initial_symptoms", length = 1000)
    private String initialSymptoms;

    @Column(name = "insurance_card_number", length = 15)
    private String insuranceCardNumber;

    @Column(name = "insurance_benefit_code")
    private Short insuranceBenefitCode;

    @Column(name = "insurance_expiry_date")
    private LocalDate insuranceExpiryDate;

    @Column(name = "insurance_coverage_rate")
    private Short insuranceCoverageRate;

    @Column(name = "insurance_verified_at")
    private Instant insuranceVerifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private VisitStatus status;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // A null version marks a new entity for Spring Data.
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected VisitEntity() {
    }

    public VisitEntity(
            UUID id,
            String registrationCode,
            UUID patientId,
            UUID registeredByAccountId,
            VisitType visitType,
            boolean priority,
            PriorityReason priorityReason,
            String reasonForVisit,
            String initialSymptoms,
            String insuranceCardNumber,
            Short insuranceBenefitCode,
            LocalDate insuranceExpiryDate,
            Short insuranceCoverageRate,
            Instant insuranceVerifiedAt,
            VisitStatus status,
            Instant registeredAt,
            Instant completedAt,
            Instant createdAt,
            Instant updatedAt,
            Long version) {

        this.id = id;
        this.registrationCode = registrationCode;
        this.patientId = patientId;
        this.registeredByAccountId = registeredByAccountId;
        this.visitType = visitType;
        this.priority = priority;
        this.priorityReason = priorityReason;
        this.reasonForVisit = reasonForVisit;
        this.initialSymptoms = initialSymptoms;
        this.insuranceCardNumber = insuranceCardNumber;
        this.insuranceBenefitCode = insuranceBenefitCode;
        this.insuranceExpiryDate = insuranceExpiryDate;
        this.insuranceCoverageRate = insuranceCoverageRate;
        this.insuranceVerifiedAt = insuranceVerifiedAt;
        this.status = status;
        this.registeredAt = registeredAt;
        this.completedAt = completedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public UUID getId() {
        return id;
    }

    public String getRegistrationCode() {
        return registrationCode;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public UUID getRegisteredByAccountId() {
        return registeredByAccountId;
    }

    public VisitType getVisitType() {
        return visitType;
    }

    public boolean isPriority() {
        return priority;
    }

    public PriorityReason getPriorityReason() {
        return priorityReason;
    }

    public String getReasonForVisit() {
        return reasonForVisit;
    }

    public String getInitialSymptoms() {
        return initialSymptoms;
    }

    public String getInsuranceCardNumber() {
        return insuranceCardNumber;
    }

    public Short getInsuranceBenefitCode() {
        return insuranceBenefitCode;
    }

    public LocalDate getInsuranceExpiryDate() {
        return insuranceExpiryDate;
    }

    public Short getInsuranceCoverageRate() {
        return insuranceCoverageRate;
    }

    public Instant getInsuranceVerifiedAt() {
        return insuranceVerifiedAt;
    }

    public VisitStatus getStatus() {
        return status;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
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
