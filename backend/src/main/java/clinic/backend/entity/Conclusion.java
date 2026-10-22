package clinic.backend.entity;

import java.time.Instant;
import java.time.LocalDate;
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
 * The conclusion of a medical record (export group III).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "conclusions", uniqueConstraints = {
        @UniqueConstraint(name = "uq_conclusions_medical_record_id", columnNames = "medical_record_id")
})
public class Conclusion extends VersionedEntity {

    @Column(name = "medical_record_id", nullable = false)
    private UUID medicalRecordId;

    @Column(name = "doctor_account_id", nullable = false)
    private UUID doctorAccountId;

    /** CV 365 III.13. */
    @Column(name = "chandoan", nullable = false)
    private String chandoan;

    /** CV 365 III.14. */
    @Column(name = "chandoan_ma", length = 255)
    private String chandoanMa;

    /** CV 365 III.19. */
    @Column(name = "maicd", nullable = false, length = 10)
    private String maicd;

    /** CV 365 III.20. */
    @Column(name = "tenicd", nullable = false, length = 255)
    private String tenicd;

    /** CV 365 III.28. */
    @Column(name = "huongdieutri", nullable = false)
    private String huongdieutri;

    /** CV 365 III.25. */
    @Column(name = "tomtat")
    private String tomtat;

    /** CV 365 III.31.1; 1 cured, 2 improved, 3 unchanged, 4 worse. */
    @Column(name = "ketquadieutri", nullable = false)
    private short ketquadieutri;

    /** CV 365 III.31.2. */
    @Column(name = "loidanbacsi", nullable = false)
    private String loidanbacsi;

    /** CV 365 III.11, III.31.4. */
    @Column(name = "ngayravien")
    private LocalDate ngayravien;

    /** CV 365 III.23. */
    @Column(name = "ghichu")
    private String ghichu;

    @Column(name = "concluded_at", nullable = false)
    private Instant concludedAt;

    @Getter(AccessLevel.NONE)
    @OneToMany(mappedBy = "conclusion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ConclusionSecondaryDiagnosis> secondaryDiagnoses = new ArrayList<>();

    public List<ConclusionSecondaryDiagnosis> getSecondaryDiagnoses() {
        return Collections.unmodifiableList(secondaryDiagnoses);
    }
}
