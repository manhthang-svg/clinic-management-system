package clinic.backend.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import clinic.backend.enums.RecordArchiveStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The frozen archive package of a locked medical record (TT 33/2025/TT-BYT).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "record_archives", uniqueConstraints = {
        @UniqueConstraint(name = "uq_record_archives_medical_record_id", columnNames = "medical_record_id"),
        @UniqueConstraint(name = "uq_record_archives_soluutru", columnNames = "soluutru")
})
public class RecordArchive extends UuidEntity {

    @Column(name = "medical_record_id", nullable = false)
    private UUID medicalRecordId;

    /** CV 365 I.4. */
    @Column(name = "soluutru", nullable = false, length = 200)
    private String soluutru;

    @Column(name = "export_log_id", nullable = false)
    private UUID exportLogId;

    @Column(name = "package_uri", nullable = false, length = 1024)
    private String packageUri;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "package_sha256", nullable = false, length = 64)
    private String packageSha256;

    @Column(name = "retention_years", nullable = false)
    private short retentionYears;

    @Column(name = "expires_on", nullable = false)
    private LocalDate expiresOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RecordArchiveStatus status;

    @Column(name = "archived_at", nullable = false)
    private Instant archivedAt;
}
