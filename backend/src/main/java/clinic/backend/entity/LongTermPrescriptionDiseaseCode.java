package clinic.backend.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One ICD-10 code covered by a disease of the long-term prescription list; ranges and child codes are stored
 * code by code, so prescribing looks up a diagnosis code directly.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "long_term_prescription_disease_codes", uniqueConstraints = {
        @UniqueConstraint(name = "uq_long_term_prescription_disease_codes", columnNames = {"disease_id", "maicd"})
})
public class LongTermPrescriptionDiseaseCode extends CreatedOnlyEntity {

    @Column(name = "disease_id", nullable = false, updatable = false)
    private UUID diseaseId;

    @Column(name = "maicd", nullable = false, updatable = false, length = 10)
    private String maicd;

    public LongTermPrescriptionDiseaseCode(UUID id, Instant createdAt, UUID diseaseId, String maicd) {
        super(id, createdAt);
        this.diseaseId = Objects.requireNonNull(diseaseId, "diseaseId");
        this.maicd = Objects.requireNonNull(maicd, "maicd");
    }
}
