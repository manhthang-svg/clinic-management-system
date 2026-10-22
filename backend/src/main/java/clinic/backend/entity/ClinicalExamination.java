package clinic.backend.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The clinical examination of a medical record (export group II).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "clinical_examinations", uniqueConstraints = {
        @UniqueConstraint(name = "uq_clinical_examinations_medical_record_id", columnNames = "medical_record_id"),
        @UniqueConstraint(name = "uq_clinical_examinations_sophieu", columnNames = "sophieu")
})
public class ClinicalExamination extends VersionedEntity {

    @Column(name = "medical_record_id", nullable = false)
    private UUID medicalRecordId;

    /** CV 365 II.2. */
    @Column(name = "sophieu", nullable = false, updatable = false, length = 100)
    private String sophieu;

    @Column(name = "examined_by_account_id", nullable = false)
    private UUID examinedByAccountId;

    /** CV 365 II.7, III.17. */
    @Column(name = "chandoancuanoigioithieu")
    private String chandoancuanoigioithieu;

    /** CV 365 II.17. */
    @Column(name = "quatrinhbenhly")
    private String quatrinhbenhly;

    /** CV 365 II.24. */
    @Column(name = "khambenh_toanthan")
    private String khambenhToanthan;

    /** CV 365 II.8. */
    @Column(name = "chandoansobo")
    private String chandoansobo;

    /** CV 365 II.9, III.15. */
    @Column(name = "chandoanvaovien")
    private String chandoanvaovien;

    /** CV 365 II.10, III.16. */
    @Column(name = "khambenh_chandoanvaovienmaicd", length = 10)
    private String khambenhChandoanvaovienmaicd;

    @Getter(AccessLevel.NONE)
    @OneToMany(mappedBy = "examination", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExaminationFinding> findings = new ArrayList<>();

    public List<ExaminationFinding> getFindings() {
        return Collections.unmodifiableList(findings);
    }
}
