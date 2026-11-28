package clinic.backend.entity;

import java.time.Instant;
import java.util.UUID;

import clinic.backend.enums.RecordSignatureDocumentType;
import clinic.backend.enums.RecordSignatureMethod;
import clinic.backend.enums.RecordSignatureSignerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Append-only record of one signature or electronic confirmation (TT 13/2025/TT-BYT).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Immutable
@Table(name = "record_signatures")
public class RecordSignature extends CreatedOnlyEntity {

    @Column(name = "medical_record_id", nullable = false, updatable = false)
    private UUID medicalRecordId;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "signer_type", nullable = false, updatable = false, length = 20)
    private RecordSignatureSignerType signerType;

    @Column(name = "signer_account_id", updatable = false)
    private UUID signerAccountId;

    @Column(name = "signer_name", nullable = false, updatable = false, length = 255)
    private String signerName;

    @Column(name = "signer_id_number", updatable = false, length = 15)
    private String signerIdNumber;

    @Column(name = "signer_relationship", updatable = false, length = 50)
    private String signerRelationship;

    @Enumerated(EnumType.STRING)
    @Column(name = "signature_method", nullable = false, updatable = false, length = 30)
    private RecordSignatureMethod signatureMethod;

    @Column(name = "certificate_serial", updatable = false, length = 100)
    private String certificateSerial;

    @Column(name = "certificate_issuer", updatable = false, length = 255)
    private String certificateIssuer;

    @Column(name = "signature_format", updatable = false, length = 20)
    private String signatureFormat;

    @Column(name = "signed_file_uri", updatable = false, length = 1024)
    private String signedFileUri;

    @Column(name = "signed_at", nullable = false, updatable = false)
    private Instant signedAt;

    @Getter(AccessLevel.NONE)
    @Column(name = "timestamp_token", updatable = false)
    private byte[] timestampToken;

    /**
     * UC-58 BR-58-09: the signed-in Doctor's "Xác nhận" confirms the whole record (TT 13/2025/TT-BYT Điều 3
     * khoản 3). It is tied to the archive package by its hash; no certificate is involved.
     */
    public static RecordSignature staffElectronicConfirmation(
            UUID id, Instant now, UUID medicalRecordId, long documentVersion, String documentSha256,
            UUID signerAccountId, String signerName) {
        RecordSignature signature = new RecordSignature(id, now);
        signature.medicalRecordId = medicalRecordId;
        signature.documentType = RecordSignatureDocumentType.BENH_AN_TONG_HOP;
        signature.documentId = medicalRecordId;
        signature.documentVersion = documentVersion;
        signature.documentSha256 = documentSha256;
        signature.signerType = RecordSignatureSignerType.STAFF;
        signature.signerAccountId = signerAccountId;
        signature.signerName = signerName;
        signature.signatureMethod = RecordSignatureMethod.ELECTRONIC_CONFIRMATION;
        signature.signedAt = now;
        return signature;
    }

    private RecordSignature(UUID id, Instant createdAt) {
        super(id, createdAt);
    }

    public byte[] getTimestampToken() {
        return timestampToken == null ? null : timestampToken.clone();
    }
}
