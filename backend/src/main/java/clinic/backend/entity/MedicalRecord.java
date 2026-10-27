package clinic.backend.entity;

import java.time.Instant;
import java.util.UUID;

import clinic.backend.enums.MedicalRecordStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The medical record of one visit; the root of one export package. Locked when closed (BR-32).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "medical_records", uniqueConstraints = {
        @UniqueConstraint(name = "uq_medical_records_visit_id", columnNames = "visit_id"),
        @UniqueConstraint(name = "uq_medical_records_soba", columnNames = "soba"),
        @UniqueConstraint(name = "uq_medical_records_soluutru", columnNames = "soluutru")
})
public class MedicalRecord extends VersionedEntity {

    @Column(name = "visit_id", nullable = false)
    private UUID visitId;

    /** CV 365 I.3. */
    @Column(name = "soba", nullable = false, updatable = false, length = 100)
    private String soba;

    /** CV 365 I.1. */
    @Column(name = "loaiba", nullable = false, length = 20)
    private String loaiba;

    /** CV 365 I.4. */
    @Column(name = "soluutru", length = 200)
    private String soluutru;

    /** CV 365 I.46. */
    @Column(name = "tiensubenhtatcuagiadinh")
    private String tiensubenhtatcuagiadinh;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MedicalRecordStatus status;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "opened_by_account_id", nullable = false)
    private UUID openedByAccountId;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by_account_id")
    private UUID closedByAccountId;

    @Column(name = "unlocked_at")
    private Instant unlockedAt;

    @Column(name = "unlocked_by_account_id")
    private UUID unlockedByAccountId;

    @Column(name = "unlock_reason")
    private String unlockReason;

    public MedicalRecord(
            UUID id,
            Instant createdAt,
            UUID visitId,
            String soba,
            String loaiba,
            MedicalRecordStatus status,
            Instant openedAt,
            UUID openedByAccountId
    ) {
        super(id, createdAt);
        this.visitId = visitId;
        this.soba = soba;
        this.loaiba = loaiba != null ? loaiba : "NGOAITRU";
        this.status = status != null ? status : MedicalRecordStatus.OPEN;
        this.openedAt = openedAt != null ? openedAt : Instant.now();
        this.openedByAccountId = openedByAccountId;
    }
}
