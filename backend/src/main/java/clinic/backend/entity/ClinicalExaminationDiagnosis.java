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

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "clinical_examination_diagnoses", uniqueConstraints = {
        @UniqueConstraint(name = "uq_clinical_examination_diagnoses_code", columnNames = {"examination_id", "maicd"}),
        @UniqueConstraint(name = "uq_clinical_examination_diagnoses_order", columnNames = {"examination_id", "thu_tu"})
})
public class ClinicalExaminationDiagnosis extends CreatedOnlyEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "examination_id", nullable = false, updatable = false)
    private ClinicalExamination examination;

    @Column(name = "maicd", nullable = false, length = 10)
    private String maicd;

    @Column(name = "tenicd", nullable = false, length = 255)
    private String tenicd;

    @Column(name = "thu_tu", nullable = false)
    private short thuTu;

    public ClinicalExaminationDiagnosis(
            UUID id, Instant createdAt, ClinicalExamination examination, String maicd, String tenicd, short thuTu) {
        super(id, createdAt);
        this.examination = examination;
        this.maicd = maicd;
        this.tenicd = tenicd;
        this.thuTu = thuTu;
    }
}
