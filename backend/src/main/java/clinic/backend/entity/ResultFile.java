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
 * A file or image attached to an imaging or functional result (VI.2.9). Lab results have no files (V33).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "result_files")
public class ResultFile extends UuidEntity {

    @Column(name = "imaging_result_id", nullable = false)
    private UUID imagingResultId;

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

    /** Position on the printed slip, unique within a result (V32). */
    @Column(name = "display_order")
    private Integer displayOrder;

    /** Caption under the printed image, for example the endoscopy site (V32). */
    @Column(name = "caption", length = 255)
    private String caption;

    /** Whether the image is printed on the slip (V32). */
    @Column(name = "print_on_slip", nullable = false)
    private boolean printOnSlip = true;
}
