package clinic.backend.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A conclusion diagnosis copied onto a prescription when it is sent; thu_tu 1 is the main diagnosis.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "prescription_diagnoses", uniqueConstraints = {
        @UniqueConstraint(name = "uq_prescription_diagnoses_code", columnNames = {"prescription_id", "maicd"}),
        @UniqueConstraint(name = "uq_prescription_diagnoses_order", columnNames = {"prescription_id", "thu_tu"})
})
public class PrescriptionDiagnosis extends CreatedOnlyEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prescription_id", nullable = false, updatable = false)
    private Prescription prescription;

    @Column(name = "maicd", nullable = false, updatable = false, length = 10)
    private String maicd;

    @Column(name = "tenicd", nullable = false, updatable = false, length = 255)
    private String tenicd;

    @Column(name = "thu_tu", nullable = false, updatable = false)
    private short thuTu;

    /** BR-59-08: one conclusion diagnosis as sent; {@code thuTu} 1 is the main diagnosis. */
    public PrescriptionDiagnosis(
            UUID id, Instant createdAt, Prescription prescription, String maicd, String tenicd, short thuTu) {
        super(id, createdAt);
        this.prescription = prescription;
        this.maicd = maicd;
        this.tenicd = tenicd;
        this.thuTu = thuTu;
    }
}
