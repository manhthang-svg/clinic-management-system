package clinic.backend.entity;

import java.time.Instant;
import java.util.UUID;

import clinic.backend.enums.RecordSignatureDocumentType;
import clinic.backend.enums.SigningRequestStatus;
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
 * A request to the remote-signing CA to sign one document version (BR-59-10 step 3; states K01-K06 of
 * docs/uc-59-e-prescription.md). Only a {@link SigningRequestStatus#PENDING} request changes; every other state is
 * final.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "signing_requests")
public class SigningRequest extends VersionedEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, updatable = false, length = 40)
    private RecordSignatureDocumentType documentType;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Column(name = "document_version", nullable = false, updatable = false)
    private long documentVersion;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "document_sha256", nullable = false, updatable = false, length = 64)
    private String documentSha256;

    /** The exact bytes to sign (BR-59-10 step 2). */
    @Getter(AccessLevel.NONE)
    @Column(name = "payload", nullable = false, updatable = false)
    private byte[] payload;

    @Column(name = "requested_by_account_id", nullable = false, updatable = false)
    private UUID requestedByAccountId;

    @Column(name = "certificate_id", nullable = false, updatable = false)
    private UUID certificateId;

    @Column(name = "provider_transaction_id", nullable = false, updatable = false, length = 100)
    private String providerTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SigningRequestStatus status;

    @Column(name = "failure_reason", length = 100)
    private String failureReason;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    /** K01: the pre-check passed and the CA accepted the request. */
    public SigningRequest(
            UUID id, Instant now, RecordSignatureDocumentType documentType, UUID documentId, long documentVersion,
            String documentSha256, byte[] payload, UUID requestedByAccountId, UUID certificateId,
            String providerTransactionId, Instant expiresAt) {
        super(id, now);
        this.documentType = documentType;
        this.documentId = documentId;
        this.documentVersion = documentVersion;
        this.documentSha256 = documentSha256;
        this.payload = payload.clone();
        this.requestedByAccountId = requestedByAccountId;
        this.certificateId = certificateId;
        this.providerTransactionId = providerTransactionId;
        this.status = SigningRequestStatus.PENDING;
        this.requestedAt = now;
        this.expiresAt = expiresAt;
    }

    public byte[] getPayload() {
        return payload.clone();
    }

    public boolean isPending() {
        return status == SigningRequestStatus.PENDING;
    }

    /** The approval window has run out (BR-59-10 step 3). */
    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /** K02: the returned signature was verified. */
    public void markSigned(Instant now) {
        finish(SigningRequestStatus.SIGNED, null, now);
    }

    /** K03: verification of the returned signature failed (EF-16). */
    public void markFailed(String reason, Instant now) {
        finish(SigningRequestStatus.FAILED, reason, now);
    }

    /** K04: the Doctor declined with the CA (AF-04). */
    public void markDeclined(Instant now) {
        finish(SigningRequestStatus.DECLINED, null, now);
    }

    /** K05: no approval in time (AF-05). */
    public void markExpired(Instant now) {
        finish(SigningRequestStatus.EXPIRED, null, now);
    }

    /** K06: the Doctor cancelled, or a save created a new document version (AF-03, UC-59). */
    public void markCancelled(Instant now) {
        finish(SigningRequestStatus.CANCELLED, null, now);
    }

    private void finish(SigningRequestStatus next, String reason, Instant now) {
        if (!isPending()) {
            throw new IllegalStateException("Signing request " + getId() + " is already " + status);
        }
        status = next;
        failureReason = reason;
        finishedAt = now;
        touch(now);
    }
}
