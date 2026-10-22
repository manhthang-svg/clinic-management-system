-- Audit trail, exports, signatures and archives from the approved schema (I1-D40).
-- audit_logs replaces business_audit_events (I1-D35), which holds no rows on the shared environment.

DROP TABLE business_audit_events;

-- Append-only business audit trail (CV 365 III.1.1.đ); sign-in and account events stay in
-- security_audit_events. old_value and new_value mask citizen ID, phone and insurance card numbers, and
-- old_value holds enough to restore a deleted row (CV 365 III.1.1.c). Blocking UPDATE and DELETE with
-- database privileges needs the deployment roles (TBD).
CREATE TABLE audit_logs (
    id               UUID         NOT NULL,
    occurred_at      TIMESTAMPTZ  NOT NULL,
    actor_account_id UUID,
    action           VARCHAR(40)  NOT NULL,
    entity_type      VARCHAR(50)  NOT NULL,
    entity_id        UUID,
    sovaovien        VARCHAR(100),
    outcome          VARCHAR(10)  NOT NULL,
    changed_fields   TEXT[],
    old_value        JSONB,
    new_value        JSONB,
    ip_address       VARCHAR(45),
    user_agent       VARCHAR(255),
    correlation_id   VARCHAR(64),
    CONSTRAINT pk_audit_logs PRIMARY KEY (id),
    CONSTRAINT fk_audit_logs_actor
        FOREIGN KEY (actor_account_id) REFERENCES staff_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT ck_audit_logs_action CHECK (action IN (
        'CREATE', 'UPDATE', 'CANCEL', 'DELETE', 'RESTORE', 'LOCK', 'UNLOCK', 'VIEW_RECORD', 'PRINT', 'EXPORT',
        'SIGN')),
    CONSTRAINT ck_audit_logs_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE'))
);

CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id, occurred_at);
-- History of one medical record.
CREATE INDEX idx_audit_logs_sovaovien ON audit_logs (sovaovien, occurred_at);

-- One export of a medical record.
CREATE TABLE export_logs (
    id                     UUID          NOT NULL,
    medical_record_id      UUID          NOT NULL,
    sovaovien              VARCHAR(100)  NOT NULL,
    export_format          VARCHAR(10)   NOT NULL,
    purpose                VARCHAR(30)   NOT NULL,
    recipient              VARCHAR(255),
    file_uri               VARCHAR(1024),
    file_sha256            CHAR(64),
    spec_version           VARCHAR(50)   NOT NULL DEFAULT 'CV365/2025',
    status                 VARCHAR(20)   NOT NULL,
    error_message          TEXT,
    exported_by_account_id UUID,
    exported_at            TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_export_logs PRIMARY KEY (id),
    CONSTRAINT fk_export_logs_medical_record
        FOREIGN KEY (medical_record_id) REFERENCES medical_records (id) ON DELETE RESTRICT,
    CONSTRAINT fk_export_logs_exported_by
        FOREIGN KEY (exported_by_account_id) REFERENCES staff_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT ck_export_logs_export_format CHECK (export_format IN ('XML', 'JSON', 'PDF')),
    CONSTRAINT ck_export_logs_purpose
        CHECK (purpose IN ('LIEN_THONG', 'LUU_TRU', 'CUNG_CAP_NGUOI_BENH', 'CO_QUAN_CO_THAM_QUYEN')),
    CONSTRAINT ck_export_logs_status CHECK (status IN ('SUCCESS', 'FAILED')),
    -- A failed export says why (I1-D41).
    CONSTRAINT ck_export_logs_error_message CHECK (status = 'SUCCESS' OR error_message IS NOT NULL),
    CONSTRAINT ck_export_logs_file_sha256 CHECK (file_sha256 ~ '^[0-9a-f]{64}$')
);

CREATE INDEX idx_export_logs_medical_record_id ON export_logs (medical_record_id);

-- Append-only: one row per signature or electronic confirmation (TT 13/2025/TT-BYT, CV 365 V.1).
-- A signed document is changed only after the record is unlocked with a reason, then signed again.
CREATE TABLE record_signatures (
    id                  UUID          NOT NULL,
    medical_record_id   UUID          NOT NULL,
    document_type       VARCHAR(40)   NOT NULL,
    document_id         UUID          NOT NULL,
    document_version    BIGINT        NOT NULL,
    document_sha256     CHAR(64)      NOT NULL,
    signer_type         VARCHAR(20)   NOT NULL,
    signer_account_id   UUID,
    signer_name         VARCHAR(255)  NOT NULL,
    signer_id_number    VARCHAR(15),
    signer_relationship VARCHAR(50),
    signature_method    VARCHAR(30)   NOT NULL,
    certificate_serial  VARCHAR(100),
    certificate_issuer  VARCHAR(255),
    signature_format    VARCHAR(20),
    signed_file_uri     VARCHAR(1024),
    signed_at           TIMESTAMPTZ   NOT NULL,
    timestamp_token     BYTEA,
    created_at          TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_record_signatures PRIMARY KEY (id),
    CONSTRAINT fk_record_signatures_medical_record
        FOREIGN KEY (medical_record_id) REFERENCES medical_records (id) ON DELETE RESTRICT,
    CONSTRAINT fk_record_signatures_signer
        FOREIGN KEY (signer_account_id) REFERENCES staff_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT ck_record_signatures_document_type CHECK (document_type IN (
        'PHIEU_KHAM', 'PHIEU_CHI_DINH', 'KQ_CDHA', 'KQ_XN', 'DON_THUOC', 'KET_LUAN', 'GIAY_HEN',
        'BENH_AN_TONG_HOP')),
    CONSTRAINT ck_record_signatures_signer_type CHECK (signer_type IN ('STAFF', 'PATIENT', 'REPRESENTATIVE')),
    CONSTRAINT ck_record_signatures_signature_method
        CHECK (signature_method IN ('DIGITAL_SIGNATURE', 'ELECTRONIC_CONFIRMATION')),
    -- Staff sign with their account; a patient or representative is identified by an ID number, and a
    -- representative also states the relationship.
    CONSTRAINT ck_record_signatures_signer CHECK (
        (signer_type = 'STAFF' AND signer_account_id IS NOT NULL)
        OR (signer_type = 'PATIENT' AND signer_account_id IS NULL AND signer_id_number IS NOT NULL)
        OR (signer_type = 'REPRESENTATIVE' AND signer_account_id IS NULL AND signer_id_number IS NOT NULL
            AND signer_relationship IS NOT NULL)),
    CONSTRAINT ck_record_signatures_certificate
        CHECK (signature_method <> 'DIGITAL_SIGNATURE' OR certificate_serial IS NOT NULL),
    CONSTRAINT ck_record_signatures_document_sha256 CHECK (document_sha256 ~ '^[0-9a-f]{64}$')
);

CREATE INDEX idx_record_signatures_medical_record_id ON record_signatures (medical_record_id);
CREATE INDEX idx_record_signatures_document ON record_signatures (document_type, document_id);

-- Frozen archive package of a locked record: signed XML and PDF (TT 33/2025/TT-BYT).
CREATE TABLE record_archives (
    id                UUID          NOT NULL,
    medical_record_id UUID          NOT NULL,
    soluutru          VARCHAR(200)  NOT NULL,
    export_log_id     UUID          NOT NULL,
    package_uri       VARCHAR(1024) NOT NULL,
    package_sha256    CHAR(64)      NOT NULL,
    retention_years   SMALLINT      NOT NULL DEFAULT 10,
    expires_on        DATE          NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    archived_at       TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_record_archives PRIMARY KEY (id),
    CONSTRAINT uq_record_archives_medical_record_id UNIQUE (medical_record_id),
    CONSTRAINT uq_record_archives_soluutru UNIQUE (soluutru),
    CONSTRAINT fk_record_archives_medical_record
        FOREIGN KEY (medical_record_id) REFERENCES medical_records (id) ON DELETE RESTRICT,
    CONSTRAINT fk_record_archives_export_log
        FOREIGN KEY (export_log_id) REFERENCES export_logs (id) ON DELETE RESTRICT,
    CONSTRAINT ck_record_archives_status CHECK (status IN ('ARCHIVED', 'PENDING_DISPOSAL', 'DISPOSED')),
    -- At least 10 years (I1-D41).
    CONSTRAINT ck_record_archives_retention_years CHECK (retention_years >= 10),
    CONSTRAINT ck_record_archives_package_sha256 CHECK (package_sha256 ~ '^[0-9a-f]{64}$')
);

CREATE INDEX idx_record_archives_export_log_id ON record_archives (export_log_id);
