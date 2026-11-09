package clinic.backend.entity;

import java.time.Instant;
import java.util.UUID;

import clinic.backend.enums.PrescriptionSubmissionResult;
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
 * One attempt to send a prescription to the national prescription system (BR-47, UC-90..92).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "prescription_submissions", uniqueConstraints = {
        @UniqueConstraint(name = "uq_prescription_submissions_attempt", columnNames = {"prescription_id", "attempt_no"})
})
public class PrescriptionSubmission extends CreatedOnlyEntity {

    @Column(name = "prescription_id", nullable = false, updatable = false)
    private UUID prescriptionId;

    @Column(name = "attempt_no", nullable = false, updatable = false)
    private short attemptNo;

    @Column(name = "submitted_by_account_id", nullable = false, updatable = false)
    private UUID submittedByAccountId;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, updatable = false, length = 20)
    private PrescriptionSubmissionResult result;

    /** Error list of a rejection, as a JSON array; null otherwise. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "errors", updatable = false)
    private String errors;

    /** Null when the system did not answer. */
    @Column(name = "responded_at", updatable = false)
    private Instant respondedAt;
}
