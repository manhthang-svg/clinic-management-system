package clinic.backend.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A file or image attached to a result (VI.2.9).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "result_files")
public class ResultFile extends UuidEntity {

    @Column(name = "imaging_result_id")
    private UUID imagingResultId;

    @Column(name = "lab_result_id")
    private UUID labResultId;

    /** CV 365 VI.2.9. */
    @Column(name = "hinhanh", nullable = false, length = 1024)
    private String hinhanh;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "file_sha256", nullable = false, length = 64)
    private String fileSha256;

    @Column(name = "uploaded_by_account_id", nullable = false)
    private UUID uploadedByAccountId;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;
}
