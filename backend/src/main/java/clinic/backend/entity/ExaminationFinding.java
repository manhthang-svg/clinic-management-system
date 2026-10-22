package clinic.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Signs found in one organ system during an examination (II.25).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "examination_findings")
public class ExaminationFinding extends CreatedOnlyEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "examination_id", nullable = false, updatable = false)
    private ClinicalExamination examination;

    /** CV 365 II.25.x. */
    @Column(name = "coquan", nullable = false, length = 50)
    private String coquan;

    /** CV 365 II.25.x.1.1. */
    @Column(name = "vncode", length = 25)
    private String vncode;

    /** CV 365 II.25.x.1.2. */
    @Column(name = "snomed", length = 25)
    private String snomed;

    /** CV 365 II.25.x.2. */
    @Column(name = "dauchung", nullable = false)
    private String dauchung;

    /** CV 365 II.25.x.3. */
    @Column(name = "dauchung_ghichu")
    private String dauchungGhichu;
}
