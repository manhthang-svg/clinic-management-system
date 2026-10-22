-- Prescriptions (export group IV) from the approved schema (I1-D40), following TT 26/2025/TT-BYT.
-- The application refuses to print or send a prescription when the patient has neither a citizen ID
-- nor a passport number; the facility data comes from clinic_facility.

CREATE TABLE prescriptions (
    id                UUID         NOT NULL,
    sophieu           VARCHAR(100) NOT NULL,
    madonthuoc        VARCHAR(14)  NOT NULL,
    loaidon           CHAR(1)      NOT NULL DEFAULT 'C',
    medical_record_id UUID         NOT NULL,
    doctor_account_id UUID         NOT NULL,
    chandoansobo      TEXT,
    doituongbn        SMALLINT     NOT NULL,
    prescribed_at     TIMESTAMPTZ  NOT NULL,
    printed_at        TIMESTAMPTZ,
    lienthong_status  VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    lienthong_sent_at TIMESTAMPTZ,
    lienthong_error   TEXT,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    version           BIGINT       NOT NULL,
    CONSTRAINT pk_prescriptions PRIMARY KEY (id),
    CONSTRAINT uq_prescriptions_sophieu UNIQUE (sophieu),
    CONSTRAINT uq_prescriptions_madonthuoc UNIQUE (madonthuoc),
    CONSTRAINT fk_prescriptions_medical_record
        FOREIGN KEY (medical_record_id) REFERENCES medical_records (id) ON DELETE RESTRICT,
    CONSTRAINT fk_prescriptions_doctor
        FOREIGN KEY (doctor_account_id) REFERENCES staff_accounts (id) ON DELETE RESTRICT,
    -- N narcotic; H psychotropic or precursor; C other.
    CONSTRAINT ck_prescriptions_loaidon CHECK (loaidon IN ('N', 'H', 'C')),
    -- xxxxxyyyyyyy-z: 5-character facility code, 7 random characters, "-", and the prescription type.
    -- The application checks that the prefix is clinic_facility.ma_cskcb.
    CONSTRAINT ck_prescriptions_madonthuoc CHECK (
        madonthuoc ~ '^[A-Za-z0-9]{12}-[NHC]$' AND right(madonthuoc, 1) = loaidon),
    -- 1 insurance, 2 self-paid (IV.2.5).
    CONSTRAINT ck_prescriptions_doituongbn CHECK (doituongbn IN (1, 2)),
    -- State of sending to the national prescription system (I1-D41).
    CONSTRAINT ck_prescriptions_lienthong_status
        CHECK (lienthong_status IN ('PENDING', 'SENT', 'ACCEPTED', 'REJECTED'))
);

CREATE INDEX idx_prescriptions_medical_record_id ON prescriptions (medical_record_id);

-- One medicine line (IV.2.6). Name and codes are snapshotted when prescribed.
CREATE TABLE prescription_details (
    id                     UUID          NOT NULL,
    prescription_id        UUID          NOT NULL,
    medicine_id            UUID,
    machidinh              VARCHAR(255)  NOT NULL,
    tenthuocvattu          VARCHAR(255)  NOT NULL,
    mathuocvattu_bv        VARCHAR(255),
    mathuocvattu_byt       VARCHAR(255),
    donvitinh              VARCHAR(50)   NOT NULL,
    nhom                   VARCHAR(255),
    loaithuoc              SMALLINT,
    soluongthuocvattu      NUMERIC(10,2) NOT NULL,
    cachdung               VARCHAR(255)  NOT NULL,
    lieu_moi_lan           VARCHAR(50)   NOT NULL,
    so_lan_ngay            SMALLINT      NOT NULL,
    so_ngay_dung           SMALLINT      NOT NULL,
    ngaykedonthuoc         DATE          NOT NULL,
    -- "Day number of use" in the appendix (an inpatient notion); outpatient exports 1.
    sothutungaydungthuoc   INTEGER,
    ylenhthuocvattu_ghichu TEXT,
    created_at             TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_prescription_details PRIMARY KEY (id),
    CONSTRAINT uq_prescription_details_machidinh UNIQUE (machidinh),
    CONSTRAINT fk_prescription_details_prescription
        FOREIGN KEY (prescription_id) REFERENCES prescriptions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_prescription_details_medicine
        FOREIGN KEY (medicine_id) REFERENCES medicine_catalog (id) ON DELETE RESTRICT,
    CONSTRAINT ck_prescription_details_dosage CHECK (
        soluongthuocvattu > 0 AND so_lan_ngay >= 1 AND so_ngay_dung >= 1)
);

CREATE INDEX idx_prescription_details_prescription_id ON prescription_details (prescription_id);
