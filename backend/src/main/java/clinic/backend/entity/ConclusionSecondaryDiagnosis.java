package clinic.backend.entity;

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
 * One secondary diagnosis of a conclusion (III.21, III.22).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "conclusion_secondary_diagnoses", uniqueConstraints = {
        @UniqueConstraint(name = "uq_conclusion_secondary_diagnoses_conclusion_maicd",
                columnNames = {"conclusion_id", "maicd"})
})
public class ConclusionSecondaryDiagnosis extends CreatedOnlyEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conclusion_id", nullable = false, updatable = false)
    private Conclusion conclusion;

    /** CV 365 III.21. */
    @Column(name = "maicd", nullable = false, length = 10)
    private String maicd;

    /** CV 365 III.22. */
    @Column(name = "tenicd", nullable = false, length = 255)
    private String tenicd;

    @Column(name = "thu_tu", nullable = false)
    private short thuTu;
}
