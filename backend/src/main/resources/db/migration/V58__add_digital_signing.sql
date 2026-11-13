-- UC-90 digital signature of the e-prescription (docs/uc-59-e-prescription.md BR-59-10, BR-59-16; TT 26/2025
-- Điều 10, Luật GDĐT 2023 Điều 22-23, NĐ 23/2025 Điều 15-17). The clinic never stores a private key or a
-- signing credential: the remote-signing CA keeps the key (NĐ 23 Điều 37 khoản 4).

-- The Doctor's registered public certificate (BR-59-16): one active certificate per account.
CREATE TABLE staff_signing_certificates (
    id               UUID          NOT NULL,
    staff_account_id UUID          NOT NULL,
    -- Remote-signing provider and the Doctor's user id there.
    provider_code    VARCHAR(50)   NOT NULL,
    provider_user_id VARCHAR(100)  NOT NULL,
    -- Hexadecimal serial number.
    serial_number    VARCHAR(100)  NOT NULL,
    issuer_dn        VARCHAR(500)  NOT NULL,
    subject_dn       VARCHAR(500)  NOT NULL,
    valid_from       TIMESTAMPTZ   NOT NULL,
    valid_to         TIMESTAMPTZ   NOT NULL,
    -- The X.509 certificate itself (DER).
    certificate_der  BYTEA         NOT NULL,
    status           VARCHAR(20)   NOT NULL,
    removed_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ   NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL,
    version          BIGINT        NOT NULL,
    CONSTRAINT pk_staff_signing_certificates PRIMARY KEY (id),
    CONSTRAINT uq_staff_signing_certificates_certificate UNIQUE (issuer_dn, serial_number),
    CONSTRAINT fk_staff_signing_certificates_staff_account
        FOREIGN KEY (staff_account_id) REFERENCES staff_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT ck_staff_signing_certificates_status CHECK (status IN ('ACTIVE', 'REMOVED')),
    CONSTRAINT ck_staff_signing_certificates_removed_at CHECK ((status = 'REMOVED') = (removed_at IS NOT NULL)),
    CONSTRAINT ck_staff_signing_certificates_validity CHECK (valid_to > valid_from)
);

-- At most one active certificate per account; also serves the lookup of the Doctor's certificate.
CREATE UNIQUE INDEX uq_staff_signing_certificates_active
    ON staff_signing_certificates (staff_account_id) WHERE status = 'ACTIVE';

-- One request to sign a document version (BR-59-10 step 3; states K01-K06).
CREATE TABLE signing_requests (
    id                      UUID         NOT NULL,
    document_type           VARCHAR(40)  NOT NULL,
    document_id             UUID         NOT NULL,
    document_version        BIGINT       NOT NULL,
    document_sha256         CHAR(64)     NOT NULL,
    -- The exact bytes the signature covers (canonical payload, BR-59-10 step 2).
    payload                 BYTEA        NOT NULL,
    requested_by_account_id UUID         NOT NULL,
    certificate_id          UUID         NOT NULL,
    provider_transaction_id VARCHAR(100) NOT NULL,
    status                  VARCHAR(20)  NOT NULL,
    failure_reason          VARCHAR(100),
    requested_at            TIMESTAMPTZ  NOT NULL,
    expires_at              TIMESTAMPTZ  NOT NULL,
    finished_at             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL,
    updated_at              TIMESTAMPTZ  NOT NULL,
    version                 BIGINT       NOT NULL,
    CONSTRAINT pk_signing_requests PRIMARY KEY (id),
    CONSTRAINT fk_signing_requests_requested_by
        FOREIGN KEY (requested_by_account_id) REFERENCES staff_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_signing_requests_certificate
        FOREIGN KEY (certificate_id) REFERENCES staff_signing_certificates (id) ON DELETE RESTRICT,
    CONSTRAINT ck_signing_requests_document_type CHECK (document_type IN (
        'PHIEU_KHAM', 'PHIEU_CHI_DINH', 'KQ_CDHA', 'KQ_XN', 'DON_THUOC', 'KET_LUAN', 'GIAY_HEN',
        'BENH_AN_TONG_HOP')),
    CONSTRAINT ck_signing_requests_status
        CHECK (status IN ('PENDING', 'SIGNED', 'FAILED', 'DECLINED', 'EXPIRED', 'CANCELLED')),
    -- Only a final request has its end time; only a failed one has a reason.
    CONSTRAINT ck_signing_requests_finished_at CHECK ((status = 'PENDING') = (finished_at IS NULL)),
    CONSTRAINT ck_signing_requests_failure_reason CHECK ((status = 'FAILED') = (failure_reason IS NOT NULL)),
    CONSTRAINT ck_signing_requests_expires_at CHECK (expires_at > requested_at),
    CONSTRAINT ck_signing_requests_document_sha256 CHECK (document_sha256 ~ '^[0-9a-f]{64}$')
);

-- At most one pending request per document (UC-90 EF-17); also serves the lookup of the pending request (AF-06).
CREATE UNIQUE INDEX uq_signing_requests_pending
    ON signing_requests (document_type, document_id) WHERE status = 'PENDING';
CREATE INDEX idx_signing_requests_certificate_id ON signing_requests (certificate_id);

-- Evidence of a digital signature kept with the signed message (Luật GDĐT Điều 10, 13; BR-59-10 step 7).
ALTER TABLE record_signatures
    -- The CMS SignedData (DER), detached from the payload.
    ADD COLUMN signature_value       BYTEA,
    -- The exact bytes that were signed.
    ADD COLUMN signed_payload        BYTEA,
    -- The signer's certification path up to the trusted CA (PkiPath encoding).
    ADD COLUMN certificate_chain     BYTEA,
    -- Certificate status answers at the signing time.
    ADD COLUMN verification_evidence JSONB,
    ADD COLUMN signing_request_id    UUID,
    ADD CONSTRAINT fk_record_signatures_signing_request
        FOREIGN KEY (signing_request_id) REFERENCES signing_requests (id) ON DELETE RESTRICT,
    -- No digital-signature row exists before this migration (nothing wrote record_signatures).
    ADD CONSTRAINT ck_record_signatures_digital_evidence CHECK (
        signature_method <> 'DIGITAL_SIGNATURE'
        OR (signature_value IS NOT NULL AND signed_payload IS NOT NULL AND certificate_chain IS NOT NULL));

CREATE INDEX idx_record_signatures_signing_request_id ON record_signatures (signing_request_id);
