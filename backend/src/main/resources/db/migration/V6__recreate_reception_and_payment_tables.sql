-- Patients, visits and payments take the approved schema (I1-D40). The V2 tables are dropped and
-- recreated instead of altered: almost every column changes, and the shared environment holds no rows
-- in them (only development databases carry demo data, and those are rebuilt).
-- The business rules of V2 are kept on the new columns; their decision IDs are unchanged.

DROP TABLE payment_items;
DROP TABLE payments;
DROP TABLE visit_room_assignments;
DROP TABLE visits;
DROP TABLE patients;

-- Business codes: prefix + zero-padded sequence value (I1-D26a).
ALTER SEQUENCE invoice_number_seq RENAME TO payment_code_seq;

-- Patients (export group I). Insurance card data is stored per visit (I1-D17).
CREATE TABLE patients (
    id                    UUID          NOT NULL,
    mabenhnhan            VARCHAR(255)  NOT NULL,
    cccd_so               VARCHAR(12),
    hochieu_so            VARCHAR(15),
    hoten                 VARCHAR(255)  NOT NULL,
    ngaysinh              DATE          NOT NULL,
    gioitinh              SMALLINT      NOT NULL,
    nghenghiep            VARCHAR(100),
    nghenghiep_ma         VARCHAR(5),
    dantoc                VARCHAR(50)   NOT NULL DEFAULT 'Kinh',
    dantoc_ma             VARCHAR(2)    NOT NULL DEFAULT '01',
    ngoaikieu             VARCHAR(255),
    ngoaikieu_ma          VARCHAR(2),
    diachi                VARCHAR(1024) NOT NULL,
    xaphuong              VARCHAR(1024),
    xaphuong_ma           VARCHAR(5),
    tinhthanh             VARCHAR(1024),
    tinhthanh_ma          VARCHAR(3),
    noilamviec            VARCHAR(1024),
    sodienthoai           VARCHAR(15),
    yeutorh               VARCHAR(5),
    nhommau               VARCHAR(5),
    hotennguoithan        VARCHAR(255),
    nguoithan_quanhe      VARCHAR(50),
    diachinguoithan       VARCHAR(1024),
    sodienthoainguoithan  VARCHAR(15),
    created_by_account_id UUID          NOT NULL,
    created_at            TIMESTAMPTZ   NOT NULL,
    updated_at            TIMESTAMPTZ   NOT NULL,
    version               BIGINT        NOT NULL,
    CONSTRAINT pk_patients PRIMARY KEY (id),
    CONSTRAINT uq_patients_mabenhnhan UNIQUE (mabenhnhan),
    -- Unique when present (BR-12, I1-D14).
    CONSTRAINT uq_patients_cccd_so UNIQUE (cccd_so),
    CONSTRAINT fk_patients_created_by
        FOREIGN KEY (created_by_account_id) REFERENCES staff_accounts (id) ON DELETE RESTRICT,
    -- 1 male, 2 female, 3 undetermined (I.16).
    CONSTRAINT ck_patients_gioitinh CHECK (gioitinh IN (1, 2, 3)),
    CONSTRAINT ck_patients_cccd_so CHECK (cccd_so ~ '^[0-9]{12}$'),
    -- Relative data is absent, or has at least a name and a relationship (I1-D16). Age-dependent
    -- requiredness is checked by the application because age changes over time.
    CONSTRAINT ck_patients_nguoithan CHECK (
        (hotennguoithan IS NULL AND nguoithan_quanhe IS NULL
            AND diachinguoithan IS NULL AND sodienthoainguoithan IS NULL)
        OR (hotennguoithan IS NOT NULL AND nguoithan_quanhe IS NOT NULL))
);

CREATE INDEX idx_patients_sodienthoai ON patients (sodienthoai);
-- Duplicate warning on full name + date of birth (I1-D19).
CREATE INDEX idx_patients_ngaysinh ON patients (ngaysinh);
CREATE INDEX idx_patients_created_by ON patients (created_by_account_id);

-- Visits. The current room and queue number live on the visit; transfers are kept in visit_transfers.
-- The foreign key to follow_up_appointments is added with that table (V7).
CREATE TABLE visits (
    id                       UUID         NOT NULL,
    sovaovien                VARCHAR(100) NOT NULL,
    patient_id               UUID         NOT NULL,
    room_id                  UUID         NOT NULL,
    doctor_account_id        UUID,
    followup_appointment_id  UUID,
    registered_by_account_id UUID         NOT NULL,
    thoigianvaovien          TIMESTAMPTZ  NOT NULL,
    denkhambenhluc           TIMESTAMPTZ,
    -- VARCHAR(4): the spreadsheet says 3, which cannot hold BHYT (I1-D41).
    doituongbn_loai          VARCHAR(4)   NOT NULL,
    mabhyt                   VARCHAR(15),
    tungaybhyt               DATE,
    denngaybhyt              DATE,
    noidangkykcbbd           VARCHAR(255),
    insurance_benefit_code   SMALLINT,
    insurance_coverage_rate  SMALLINT,
    insurance_verify_status  VARCHAR(20),
    insurance_verified_at    TIMESTAMPTZ,
    lydotiepnhan             TEXT,
    noigioithieu_loai        VARCHAR(200),
    lydovaovien              TEXT         NOT NULL,
    is_priority              BOOLEAN      NOT NULL DEFAULT FALSE,
    priority_reason          VARCHAR(20),
    queue_date               DATE         NOT NULL,
    queue_number             INTEGER      NOT NULL,
    status                   VARCHAR(30)  NOT NULL,
    is_skipped               BOOLEAN      NOT NULL DEFAULT FALSE,
    skipped_at               TIMESTAMPTZ,
    completed_at             TIMESTAMPTZ,
    created_at               TIMESTAMPTZ  NOT NULL,
    updated_at               TIMESTAMPTZ  NOT NULL,
    version                  BIGINT       NOT NULL,
    CONSTRAINT pk_visits PRIMARY KEY (id),
    CONSTRAINT uq_visits_sovaovien UNIQUE (sovaovien),
    -- Queue numbers are per room per day (I1-D28); a transfer takes a new number in the new room.
    CONSTRAINT uq_visits_queue UNIQUE (room_id, queue_date, queue_number),
    CONSTRAINT fk_visits_patient FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE RESTRICT,
    CONSTRAINT fk_visits_room FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_visits_doctor
        FOREIGN KEY (doctor_account_id) REFERENCES staff_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_visits_registered_by
        FOREIGN KEY (registered_by_account_id) REFERENCES staff_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT ck_visits_doituongbn_loai CHECK (doituongbn_loai IN ('BHYT', 'DV')),
    CONSTRAINT ck_visits_insurance_verify_status
        CHECK (insurance_verify_status IN ('VALID', 'INVALID', 'NOT_APPLICABLE')),
    CONSTRAINT ck_visits_status CHECK (status IN (
        'WAITING_FEE_PAYMENT', 'WAITING', 'IN_EXAMINATION', 'WAITING_PARACLINICAL',
        'WAITING_CONCLUSION', 'COMPLETED', 'CANCELLED', 'ABANDONED')),
    -- A priority visit records its reason (I1-D39).
    CONSTRAINT ck_visits_priority_reason CHECK (
        (is_priority AND priority_reason IS NOT NULL AND priority_reason IN ('ELDERLY', 'FRAIL_DISABLED'))
        OR (NOT is_priority AND priority_reason IS NULL)),
    -- 15 characters; the third is the benefit code (R3 §I.3.3).
    CONSTRAINT ck_visits_mabhyt CHECK (
        mabhyt ~ '^[A-Z]{2}[1-5][0-9]{12}$'
        AND (insurance_benefit_code IS NULL
            OR substring(mabhyt FROM 3 FOR 1) = insurance_benefit_code::text)),
    CONSTRAINT ck_visits_insurance_benefit_code CHECK (insurance_benefit_code BETWEEN 1 AND 5),
    CONSTRAINT ck_visits_insurance_coverage_rate CHECK (insurance_coverage_rate BETWEEN 1 AND 100),
    CONSTRAINT ck_visits_card_validity CHECK (denngaybhyt >= tungaybhyt),
    CONSTRAINT ck_visits_queue_number CHECK (queue_number >= 1)
);

-- A patient has at most one visit that is not finished (BR-17).
CREATE UNIQUE INDEX uq_visits_active_patient
    ON visits (patient_id) WHERE status NOT IN ('COMPLETED', 'CANCELLED', 'ABANDONED');
CREATE INDEX idx_visits_patient_thoigianvaovien ON visits (patient_id, thoigianvaovien);
CREATE INDEX idx_visits_status_thoigianvaovien ON visits (status, thoigianvaovien);
-- UC-35 searches by a card number used in previous visits (I1-D17).
CREATE INDEX idx_visits_mabhyt ON visits (mabhyt);
CREATE INDEX idx_visits_registered_by ON visits (registered_by_account_id);

-- One row per room transfer of a visit (BR-24).
CREATE TABLE visit_transfers (
    id                        UUID        NOT NULL,
    visit_id                  UUID        NOT NULL,
    from_room_id              UUID        NOT NULL,
    to_room_id                UUID        NOT NULL,
    reason                    TEXT        NOT NULL,
    old_queue_number          INTEGER     NOT NULL,
    new_queue_number          INTEGER     NOT NULL,
    transferred_by_account_id UUID        NOT NULL,
    transferred_at            TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_visit_transfers PRIMARY KEY (id),
    CONSTRAINT fk_visit_transfers_visit FOREIGN KEY (visit_id) REFERENCES visits (id) ON DELETE RESTRICT,
    CONSTRAINT fk_visit_transfers_from_room
        FOREIGN KEY (from_room_id) REFERENCES rooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_visit_transfers_to_room
        FOREIGN KEY (to_room_id) REFERENCES rooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_visit_transfers_transferred_by
        FOREIGN KEY (transferred_by_account_id) REFERENCES staff_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT ck_visit_transfers_rooms CHECK (from_room_id <> to_room_id),
    CONSTRAINT ck_visit_transfers_queue_numbers CHECK (old_queue_number >= 1 AND new_queue_number >= 1)
);

CREATE INDEX idx_visit_transfers_visit_id ON visit_transfers (visit_id);

-- Payments to collect for a visit; not electronic invoices.
CREATE TABLE payments (
    id                     UUID          NOT NULL,
    payment_code           VARCHAR(20)   NOT NULL,
    visit_id               UUID          NOT NULL,
    type                   VARCHAR(20)   NOT NULL,
    status                 VARCHAR(20)   NOT NULL,
    total_amount           NUMERIC(15,0) NOT NULL,
    insurance_total_amount NUMERIC(15,0) NOT NULL,
    patient_total_amount   NUMERIC(15,0) NOT NULL,
    payment_method         VARCHAR(20),
    receipt_number         VARCHAR(100),
    cashier_account_id     UUID,
    paid_at                TIMESTAMPTZ,
    cancelled_at           TIMESTAMPTZ,
    note                   TEXT,
    created_at             TIMESTAMPTZ   NOT NULL,
    updated_at             TIMESTAMPTZ   NOT NULL,
    version                BIGINT        NOT NULL,
    CONSTRAINT pk_payments PRIMARY KEY (id),
    -- Assigned at creation, kept when cancelled (I1-D26).
    CONSTRAINT uq_payments_payment_code UNIQUE (payment_code),
    CONSTRAINT uq_payments_receipt_number UNIQUE (receipt_number),
    CONSTRAINT fk_payments_visit FOREIGN KEY (visit_id) REFERENCES visits (id) ON DELETE RESTRICT,
    CONSTRAINT fk_payments_cashier
        FOREIGN KEY (cashier_account_id) REFERENCES staff_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT ck_payments_type CHECK (type IN ('EXAMINATION_FEE', 'PARACLINICAL_FEE')),
    CONSTRAINT ck_payments_status CHECK (status IN ('PENDING', 'PAID', 'FEE_EXEMPT', 'CANCELLED')),
    CONSTRAINT ck_payments_payment_method CHECK (payment_method IN ('CASH', 'BANK_TRANSFER', 'CARD')),
    CONSTRAINT ck_payments_amounts CHECK (
        insurance_total_amount >= 0 AND patient_total_amount >= 0
        AND total_amount = insurance_total_amount + patient_total_amount),
    CONSTRAINT ck_payments_paid_at CHECK ((status = 'PAID') = (paid_at IS NOT NULL)),
    CONSTRAINT ck_payments_cancelled_at CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL))
);

-- One examination-fee payment per visit; a visit can have several paraclinical payments.
CREATE UNIQUE INDEX uq_payments_examination_fee_visit ON payments (visit_id) WHERE type = 'EXAMINATION_FEE';
CREATE INDEX idx_payments_visit_id ON payments (visit_id);
CREATE INDEX idx_payments_status_created_at ON payments (status, created_at);

-- Payment lines. Prices are snapshotted when the line is created (BR-09). The foreign key to
-- paraclinical_order_details is added with that table (V8).
CREATE TABLE payment_items (
    id                    UUID          NOT NULL,
    payment_id            UUID          NOT NULL,
    service_id            UUID          NOT NULL,
    order_detail_id       UUID,
    service_code_snapshot VARCHAR(50)   NOT NULL,
    tenchidinh            VARCHAR(255)  NOT NULL,
    soluong               NUMERIC(15,2) NOT NULL DEFAULT 1,
    price_type            VARCHAR(20)   NOT NULL,
    dongia                NUMERIC(15,0) NOT NULL,
    coverage_rate         SMALLINT,
    thanhtien             NUMERIC(15,0) NOT NULL,
    bhyt_chitra           NUMERIC(15,0) NOT NULL DEFAULT 0,
    benhnhanphaitra       NUMERIC(15,0) NOT NULL,
    is_cancelled          BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_payment_items PRIMARY KEY (id),
    -- Payments are never deleted (R3 §IV.2.3), so no cascade.
    CONSTRAINT fk_payment_items_payment FOREIGN KEY (payment_id) REFERENCES payments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_items_service FOREIGN KEY (service_id) REFERENCES services (id) ON DELETE RESTRICT,
    CONSTRAINT ck_payment_items_price_type CHECK (price_type IN ('INSURANCE', 'CLINIC')),
    CONSTRAINT ck_payment_items_soluong CHECK (soluong > 0),
    -- Money calculation rule 7 (docs/iteration-1-decisions.md), applied to the line total.
    CONSTRAINT ck_payment_items_amounts CHECK (
        bhyt_chitra >= 0 AND benhnhanphaitra >= 0 AND bhyt_chitra + benhnhanphaitra = thanhtien),
    CONSTRAINT ck_payment_items_coverage CHECK (
        (price_type = 'INSURANCE' AND coverage_rate IS NOT NULL AND coverage_rate BETWEEN 1 AND 100)
        OR (price_type = 'CLINIC' AND coverage_rate IS NULL AND bhyt_chitra = 0))
);

CREATE INDEX idx_payment_items_payment_id ON payment_items (payment_id);
CREATE INDEX idx_payment_items_service_id ON payment_items (service_id);
CREATE INDEX idx_payment_items_order_detail_id ON payment_items (order_detail_id);
