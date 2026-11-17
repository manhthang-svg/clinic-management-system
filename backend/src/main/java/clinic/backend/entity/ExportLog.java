package clinic.backend.entity;

import java.time.Instant;
import java.util.UUID;

import clinic.backend.enums.ExportFormat;
import clinic.backend.enums.ExportLogPurpose;
import clinic.backend.enums.ExportLogStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One export of a medical record as XML, JSON or PDF.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "export_logs")
public class ExportLog extends UuidEntity {

    @Column(name = "medical_record_id", nullable = false)
    private UUID medicalRecordId;

    @Column(name = "sovaovien", nullable = false, updatable = false, length = 100)
    private String sovaovien;

    @Enumerated(EnumType.STRING)
    @Column(name = "export_format", nullable = false, length = 10)
    private ExportFormat exportFormat;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private ExportLogPurpose purpose;

    @Column(name = "recipient", length = 255)
    private String recipient;

    @Column(name = "file_uri", length = 1024)
    private String fileUri;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "file_sha256", length = 64)
    private String fileSha256;

    @Column(name = "spec_version", nullable = false, length = 50)
    private String specVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ExportLogStatus status;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "exported_by_account_id")
    private UUID exportedByAccountId;

    @Column(name = "exported_at", nullable = false)
    private Instant exportedAt;

    /** UC-58 BR-58-06: the archive package written when the record is closed (BR-43 purpose "Lưu trữ"). */
    public static ExportLog archivePackage(
            UUID id, UUID medicalRecordId, String sovaovien, String fileUri, String fileSha256, String specVersion,
            UUID exportedByAccountId, Instant exportedAt) {
        ExportLog log = new ExportLog(id);
        log.medicalRecordId = medicalRecordId;
        log.sovaovien = sovaovien;
        log.exportFormat = ExportFormat.JSON;
        log.purpose = ExportLogPurpose.LUU_TRU;
        log.fileUri = fileUri;
        log.fileSha256 = fileSha256;
        log.specVersion = specVersion;
        log.status = ExportLogStatus.SUCCESS;
        log.exportedByAccountId = exportedByAccountId;
        log.exportedAt = exportedAt;
        return log;
    }

    /** UC-53 BR-53-07: a PDF in form 15/BV1, written and stored. */
    public static ExportLog pdfExport(
            UUID id, UUID medicalRecordId, String sovaovien, ExportLogPurpose purpose, String recipient,
            String fileUri, String fileSha256, String specVersion, UUID exportedByAccountId, Instant exportedAt) {
        ExportLog log = new ExportLog(id);
        log.medicalRecordId = medicalRecordId;
        log.sovaovien = sovaovien;
        log.exportFormat = ExportFormat.PDF;
        log.purpose = purpose;
        log.recipient = recipient;
        log.fileUri = fileUri;
        log.fileSha256 = fileSha256;
        log.specVersion = specVersion;
        log.status = ExportLogStatus.SUCCESS;
        log.exportedByAccountId = exportedByAccountId;
        log.exportedAt = exportedAt;
        return log;
    }

    /** UC-53 EF-03: an export that failed; {@code errorMessage} is a reason code, never medical data. */
    public static ExportLog failed(
            UUID id, UUID medicalRecordId, String sovaovien, ExportFormat format, ExportLogPurpose purpose,
            String recipient, String specVersion, String errorMessage, UUID exportedByAccountId, Instant exportedAt) {
        ExportLog log = new ExportLog(id);
        log.medicalRecordId = medicalRecordId;
        log.sovaovien = sovaovien;
        log.exportFormat = format;
        log.purpose = purpose;
        log.recipient = recipient;
        log.specVersion = specVersion;
        log.status = ExportLogStatus.FAILED;
        log.errorMessage = errorMessage;
        log.exportedByAccountId = exportedByAccountId;
        log.exportedAt = exportedAt;
        return log;
    }

    private ExportLog(UUID id) {
        super(id);
    }
}
