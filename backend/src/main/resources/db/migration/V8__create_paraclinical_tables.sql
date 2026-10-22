-- Paraclinical orders and results (export groups V to VII) from the approved schema (I1-D40).

-- Order form (group V). Each order is paid by the paraclinical payment created with it.
CREATE TABLE paraclinical_orders (
    id                UUID          NOT NULL,
    sophieu           VARCHAR(100)  NOT NULL,
    medical_record_id UUID          NOT NULL,
    doctor_account_id UUID          NOT NULL,
    payment_id        UUID          NOT NULL,
    chandoansobo      TEXT,
    doituongbn        SMALLINT      NOT NULL,
    mucdochidinh      VARCHAR(255)  NOT NULL DEFAULT 'Thường',
    noichidinh        VARCHAR(255)  NOT NULL,
    tongcong          NUMERIC(15,0) NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    version           BIGINT        NOT NULL,
    CONSTRAINT pk_paraclinical_orders PRIMARY KEY (id),
    CONSTRAINT uq_paraclinical_orders_sophieu UNIQUE (sophieu),
    -- One payment per order (I1-D41).
    CONSTRAINT uq_paraclinical_orders_payment_id UNIQUE (payment_id),
    CONSTRAINT fk_paraclinical_orders_medical_record
        FOREIGN KEY (medical_record_id) REFERENCES medical_records (id) ON DELETE RESTRICT,
    CONSTRAINT fk_paraclinical_orders_doctor
        FOREIGN KEY (doctor_account_id) REFERENCES staff_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_paraclinical_orders_payment FOREIGN KEY (payment_id) REFERENCES payments (id) ON DELETE RESTRICT,
    -- 1 insurance, 2 self-paid (V.2.5).
    CONSTRAINT ck_paraclinical_orders_doituongbn CHECK (doituongbn IN (1, 2)),
    CONSTRAINT ck_paraclinical_orders_tongcong CHECK (tongcong >= 0),
    CONSTRAINT ck_paraclinical_orders_status
        CHECK (status IN ('PENDING_PAYMENT', 'RELEASED', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX idx_paraclinical_orders_medical_record_id ON paraclinical_orders (medical_record_id);

-- One ordered service (V.2.6). Results in groups VI and VII refer to its machidinh.
CREATE TABLE paraclinical_order_details (
    id                      UUID          NOT NULL,
    order_id                UUID          NOT NULL,
    service_id              UUID          NOT NULL,
    machidinh               VARCHAR(255)  NOT NULL,
    ngaychidinh             TIMESTAMPTZ   NOT NULL,
    tenchidinh              VARCHAR(255)  NOT NULL,
    loaichidinh             VARCHAR(255),
    loaimachidinh           VARCHAR(255),
    soluong                 NUMERIC(15,2) NOT NULL DEFAULT 1,
    dongia                  NUMERIC(15,0) NOT NULL,
    thanhtien               NUMERIC(15,0) NOT NULL,
    bhyt_chitra             NUMERIC(15,0) NOT NULL DEFAULT 0,
    benhnhanphaitra         NUMERIC(15,0) NOT NULL,
    room_id                 UUID,
    queue_number            INTEGER,
    status                  VARCHAR(20)   NOT NULL,
    is_skipped              BOOLEAN       NOT NULL DEFAULT FALSE,
    skipped_at              TIMESTAMPTZ,
    performer_account_id    UUID,
    released_at             TIMESTAMPTZ,
    accepted_at             TIMESTAMPTZ,
    performed_at            TIMESTAMPTZ,
    cancel_reason           TEXT,
    cancelled_by_account_id UUID,
    cancelled_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ   NOT NULL,
    updated_at              TIMESTAMPTZ   NOT NULL,
    version                 BIGINT        NOT NULL,
    CONSTRAINT pk_paraclinical_order_details PRIMARY KEY (id),
    CONSTRAINT uq_paraclinical_order_details_machidinh UNIQUE (machidinh),
    CONSTRAINT fk_paraclinical_order_details_order
        FOREIGN KEY (order_id) REFERENCES paraclinical_orders (id) ON DELETE RESTRICT,
    CONSTRAINT fk_paraclinical_order_details_service
        FOREIGN KEY (service_id) REFERENCES services (id) ON DELETE RESTRICT,
    CONSTRAINT fk_paraclinical_order_details_room FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_paraclinical_order_details_performer
        FOREIGN KEY (performer_account_id) REFERENCES staff_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_paraclinical_order_details_cancelled_by
        FOREIGN KEY (cancelled_by_account_id) REFERENCES staff_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT ck_paraclinical_order_details_status CHECK (status IN (
        'PENDING_PAYMENT', 'WAITING', 'IN_PROGRESS', 'PENDING_RESULT', 'COMPLETED', 'CANCELLED', 'ABANDONED')),
    CONSTRAINT ck_paraclinical_order_details_soluong CHECK (soluong > 0),
    -- Money calculation rule 7 (docs/iteration-1-decisions.md), applied to the line total.
    CONSTRAINT ck_paraclinical_order_details_amounts CHECK (
        bhyt_chitra >= 0 AND benhnhanphaitra >= 0 AND bhyt_chitra + benhnhanphaitra = thanhtien),
    CONSTRAINT ck_paraclinical_order_details_queue_number CHECK (queue_number >= 1),
    CONSTRAINT ck_paraclinical_order_details_cancellation CHECK (
        (cancelled_at IS NULL) = (cancelled_by_account_id IS NULL))
);

CREATE INDEX idx_paraclinical_order_details_order_id ON paraclinical_order_details (order_id);
-- Worklist of a paraclinical room.
CREATE INDEX idx_paraclinical_order_details_room_status ON paraclinical_order_details (room_id, status);

-- The payment line of an ordered service (V6 created the column before this table existed).
ALTER TABLE payment_items ADD CONSTRAINT fk_payment_items_order_detail
    FOREIGN KEY (order_detail_id) REFERENCES paraclinical_order_details (id) ON DELETE RESTRICT;

-- Result of an imaging or functional service (group VI); one per ordered service. A submitted result
-- cannot be edited (BR-29).
CREATE TABLE imaging_results (
    id                   UUID          NOT NULL,
    order_detail_id      UUID          NOT NULL,
    performer_account_id UUID          NOT NULL,
    sophieu              VARCHAR(100)  NOT NULL,
    ngayketqua           TIMESTAMPTZ,
    chandoan             TEXT,
    kqcls_mota           TEXT          NOT NULL,
    kqcls_ketluan        TEXT          NOT NULL,
    kqcls_denghi         TEXT,
    kqcls_linkhinhanh    VARCHAR(1024),
    status               VARCHAR(20)   NOT NULL,
    submitted_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ   NOT NULL,
    updated_at           TIMESTAMPTZ   NOT NULL,
    version              BIGINT        NOT NULL,
    CONSTRAINT pk_imaging_results PRIMARY KEY (id),
    CONSTRAINT uq_imaging_results_order_detail_id UNIQUE (order_detail_id),
    CONSTRAINT uq_imaging_results_sophieu UNIQUE (sophieu),
    CONSTRAINT fk_imaging_results_order_detail
        FOREIGN KEY (order_detail_id) REFERENCES paraclinical_order_details (id) ON DELETE RESTRICT,
    CONSTRAINT fk_imaging_results_performer
        FOREIGN KEY (performer_account_id) REFERENCES staff_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT ck_imaging_results_status CHECK (status IN ('DRAFT', 'SUBMITTED')),
    CONSTRAINT ck_imaging_results_submitted_at CHECK ((status = 'SUBMITTED') = (submitted_at IS NOT NULL))
);

-- Result form of a lab service (group VII); one per ordered service (BR-29).
CREATE TABLE lab_results (
    id                             UUID         NOT NULL,
    order_detail_id                UUID         NOT NULL,
    performer_account_id           UUID         NOT NULL,
    sophieu                        VARCHAR(100) NOT NULL,
    chandoan                       TEXT,
    ghichu                         TEXT,
    giolaymau                      TIMESTAMPTZ,
    sample_collected_by_account_id UUID,
    masobenhpham                   VARCHAR(255),
    status                         VARCHAR(20)  NOT NULL,
    submitted_at                   TIMESTAMPTZ,
    created_at                     TIMESTAMPTZ  NOT NULL,
    updated_at                     TIMESTAMPTZ  NOT NULL,
    version                        BIGINT       NOT NULL,
    CONSTRAINT pk_lab_results PRIMARY KEY (id),
    CONSTRAINT uq_lab_results_order_detail_id UNIQUE (order_detail_id),
    CONSTRAINT uq_lab_results_sophieu UNIQUE (sophieu),
    CONSTRAINT fk_lab_results_order_detail
        FOREIGN KEY (order_detail_id) REFERENCES paraclinical_order_details (id) ON DELETE RESTRICT,
    CONSTRAINT fk_lab_results_performer
        FOREIGN KEY (performer_account_id) REFERENCES staff_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_lab_results_sample_collected_by
        FOREIGN KEY (sample_collected_by_account_id) REFERENCES staff_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT ck_lab_results_status CHECK (status IN ('DRAFT', 'SUBMITTED')),
    CONSTRAINT ck_lab_results_submitted_at CHECK ((status = 'SUBMITTED') = (submitted_at IS NOT NULL))
);

-- One measured value (VII.2.13).
CREATE TABLE lab_result_items (
    id                 UUID         NOT NULL,
    lab_result_id      UUID         NOT NULL,
    machiso            VARCHAR(255),
    pkqxn_tenxetnghiem VARCHAR(255) NOT NULL,
    ketqua             VARCHAR(255) NOT NULL,
    donvi              VARCHAR(50),
    trisothamchieu     VARCHAR(50),
    ketquabatthuong    CHAR(1),
    loaixetnghiem      VARCHAR(255),
    nhom               VARCHAR(255),
    ngayketqua         TIMESTAMPTZ,
    CONSTRAINT pk_lab_result_items PRIMARY KEY (id),
    CONSTRAINT fk_lab_result_items_lab_result
        FOREIGN KEY (lab_result_id) REFERENCES lab_results (id) ON DELETE RESTRICT,
    -- 1 abnormal, 0 normal (VII.2.13.6).
    CONSTRAINT ck_lab_result_items_ketquabatthuong CHECK (ketquabatthuong IN ('0', '1'))
);

CREATE INDEX idx_lab_result_items_lab_result_id ON lab_result_items (lab_result_id);

-- Files and images of a result (VI.2.9); partner labs often send lab results as PDF.
CREATE TABLE result_files (
    id                     UUID          NOT NULL,
    imaging_result_id      UUID,
    lab_result_id          UUID,
    hinhanh                VARCHAR(1024) NOT NULL,
    file_name              VARCHAR(255)  NOT NULL,
    mime_type              VARCHAR(100)  NOT NULL,
    file_sha256            CHAR(64)      NOT NULL,
    uploaded_by_account_id UUID          NOT NULL,
    uploaded_at            TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_result_files PRIMARY KEY (id),
    CONSTRAINT fk_result_files_imaging_result
        FOREIGN KEY (imaging_result_id) REFERENCES imaging_results (id) ON DELETE RESTRICT,
    CONSTRAINT fk_result_files_lab_result
        FOREIGN KEY (lab_result_id) REFERENCES lab_results (id) ON DELETE RESTRICT,
    CONSTRAINT fk_result_files_uploaded_by
        FOREIGN KEY (uploaded_by_account_id) REFERENCES staff_accounts (id) ON DELETE RESTRICT,
    -- A file belongs to exactly one result (I1-D41).
    CONSTRAINT ck_result_files_owner CHECK ((imaging_result_id IS NULL) <> (lab_result_id IS NULL)),
    CONSTRAINT ck_result_files_file_sha256 CHECK (file_sha256 ~ '^[0-9a-f]{64}$')
);

CREATE INDEX idx_result_files_imaging_result_id ON result_files (imaging_result_id);
CREATE INDEX idx_result_files_lab_result_id ON result_files (lab_result_id);
