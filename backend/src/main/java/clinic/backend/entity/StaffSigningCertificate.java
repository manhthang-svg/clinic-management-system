package clinic.backend.entity;

import java.time.Instant;
import java.util.UUID;

import clinic.backend.enums.SigningCertificateStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A staff member's registered public signing certificate (BR-59-16). The private key stays with the remote-signing
 * CA (NĐ 23/2025 Điều 37 khoản 4); only the public certificate is kept here.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "staff_signing_certificates")
public class StaffSigningCertificate extends VersionedEntity {

    @Column(name = "staff_account_id", nullable = false, updatable = false)
    private UUID staffAccountId;

    @Column(name = "provider_code", nullable = false, updatable = false, length = 50)
    private String providerCode;

    @Column(name = "provider_user_id", nullable = false, updatable = false, length = 100)
    private String providerUserId;

    /** Hexadecimal serial number. */
    @Column(name = "serial_number", nullable = false, updatable = false, length = 100)
    private String serialNumber;

    @Column(name = "issuer_dn", nullable = false, updatable = false, length = 500)
    private String issuerDn;

    @Column(name = "subject_dn", nullable = false, updatable = false, length = 500)
    private String subjectDn;

    @Column(name = "valid_from", nullable = false, updatable = false)
    private Instant validFrom;

    @Column(name = "valid_to", nullable = false, updatable = false)
    private Instant validTo;

    /** The X.509 certificate (DER). */
    @Getter(AccessLevel.NONE)
    @Column(name = "certificate_der", nullable = false, updatable = false)
    private byte[] certificateDer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SigningCertificateStatus status;

    @Column(name = "removed_at")
    private Instant removedAt;

    public StaffSigningCertificate(
            UUID id, Instant createdAt, UUID staffAccountId, String providerCode, String providerUserId,
            String serialNumber, String issuerDn, String subjectDn, Instant validFrom, Instant validTo,
            byte[] certificateDer) {
        super(id, createdAt);
        this.staffAccountId = staffAccountId;
        this.providerCode = providerCode;
        this.providerUserId = providerUserId;
        this.serialNumber = serialNumber;
        this.issuerDn = issuerDn;
        this.subjectDn = subjectDn;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.certificateDer = certificateDer.clone();
        this.status = SigningCertificateStatus.ACTIVE;
    }

    public byte[] getCertificateDer() {
        return certificateDer.clone();
    }

    public boolean isActive() {
        return status == SigningCertificateStatus.ACTIVE;
    }

    /** The certificate is no longer used for signing; its past signatures stay verifiable. */
    public void remove(Instant now) {
        if (!isActive()) {
            return;
        }
        status = SigningCertificateStatus.REMOVED;
        removedAt = now;
        touch(now);
    }
}
