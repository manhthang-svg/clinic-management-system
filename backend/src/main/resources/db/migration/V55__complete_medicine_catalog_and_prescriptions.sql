-- Medicine catalog and e-prescription (UC-59, UC-81..84, UC-90..93), decision I1-D45.
-- Rules: BR-33, BR-47, BR-49, BR-52, BR-65, BR-79, BR-107; TT 26/2025/TT-BYT; QĐ 808/QĐ-BYT.

-- No use case writes these tables yet, so they hold no rows. Stop instead of guessing values for the new
-- required columns if a database has rows anyway.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM medicine_catalog) OR EXISTS (SELECT 1 FROM prescriptions) THEN
        RAISE EXCEPTION 'V55 expects empty medicine_catalog and prescriptions tables; migrate existing rows by hand first';
    END IF;
END $$;

-- Prescriber link code issued by the national prescription system (BR-49, BR-65, UC-93). Required before
-- a doctor prescribes; the application checks it, because it is entered after the account is created.
ALTER TABLE staff_accounts
    ADD COLUMN ma_lienthong_bacsi VARCHAR(50),
    ADD CONSTRAINT uq_staff_accounts_ma_lienthong_bacsi UNIQUE (ma_lienthong_bacsi),
    ADD CONSTRAINT ck_staff_accounts_ma_lienthong_bacsi CHECK (ma_lienthong_bacsi ~ '^[A-Za-z0-9]{1,50}$');

-- Medicine catalog (BR-79, UC-81).
ALTER TABLE medicine_catalog
    ALTER COLUMN mathuocvattu_bv TYPE VARCHAR(50),
    -- Registration number granted by the Drug Administration of Vietnam (Cục Quản lý Dược): the "mã thuốc BYT"
    -- of BR-79, one per product. mathuocvattu_byt stays the export field IV.2.6.8, the code of the
    -- TT 20/2022/TT-BYT insurance list, which products with the same active ingredient share; it is optional.
    ADD COLUMN so_dang_ky     VARCHAR(20)  NOT NULL,
    -- Active ingredient: TT 26 writes a single-ingredient medicine by its international nonproprietary name.
    ADD COLUMN hoatchat       VARCHAR(500) NOT NULL,
    -- Strength and route are free text, for example '500mg' and 'Uống'.
    ADD COLUMN hamluong       TEXT         NOT NULL,
    ADD COLUMN duongdung      TEXT         NOT NULL,
    -- Special-control group (Luật Dược 2016). Only THUONG can be prescribed, since the system issues only
    -- ordinary prescriptions (BR-52); the other groups need prescription types n or h.
    ADD COLUMN nhom_kiem_soat VARCHAR(20)  NOT NULL,
    ADD CONSTRAINT uq_medicine_catalog_so_dang_ky UNIQUE (so_dang_ky),
    ADD CONSTRAINT ck_medicine_catalog_mathuocvattu_bv CHECK (mathuocvattu_bv ~ '^[A-Z0-9-]{1,50}$'),
    ADD CONSTRAINT ck_medicine_catalog_so_dang_ky CHECK (btrim(so_dang_ky) <> ''),
    ADD CONSTRAINT ck_medicine_catalog_hoatchat CHECK (char_length(btrim(hoatchat)) >= 2),
    ADD CONSTRAINT ck_medicine_catalog_hamluong CHECK (btrim(hamluong) <> ''),
    ADD CONSTRAINT ck_medicine_catalog_duongdung CHECK (btrim(duongdung) <> ''),
    ADD CONSTRAINT ck_medicine_catalog_nhom_kiem_soat
        CHECK (nhom_kiem_soat IN ('THUONG', 'GAY_NGHIEN', 'HUONG_THAN', 'TIEN_CHAT'));

-- Prescriptions: ordinary type c only (BR-52), at most one per visit, notes and advice (BR-107).
ALTER TABLE prescriptions
    DROP CONSTRAINT ck_prescriptions_loaidon,
    DROP CONSTRAINT ck_prescriptions_madonthuoc,
    ALTER COLUMN loaidon SET DEFAULT 'c',
    ADD COLUMN luu_y         VARCHAR(2000),
    ADD COLUMN loi_dan       VARCHAR(2000),
    -- Follow-up date copied from the appointment when the prescription is sent (BR-54).
    ADD COLUMN ngay_tai_kham DATE,
    ADD CONSTRAINT uq_prescriptions_medical_record_id UNIQUE (medical_record_id),
    ADD CONSTRAINT ck_prescriptions_loaidon CHECK (loaidon = 'c'),
    -- xxxxxyyyyyyy-z: 5-digit facility code, 7 random characters 0-9 or a-z, "-", type c. Generated when the
    -- prescription is created and never changed. The application checks the prefix is clinic_facility.ma_cskcb.
    ADD CONSTRAINT ck_prescriptions_madonthuoc CHECK (madonthuoc ~ '^[0-9]{5}[0-9a-z]{7}-c$');

-- The unique constraint serves lookups by visit record.
DROP INDEX idx_prescriptions_medical_record_id;

-- Each attempt is a row of prescription_submissions.
ALTER TABLE prescriptions DROP COLUMN lienthong_error;

-- Medicine lines: snapshot of the catalog fields and the limits of BR-107.
ALTER TABLE prescription_details
    DROP CONSTRAINT ck_prescription_details_dosage,
    ALTER COLUMN mathuocvattu_bv TYPE VARCHAR(50),
    ADD COLUMN so_dang_ky     VARCHAR(20)  NOT NULL,
    ADD COLUMN hoatchat       VARCHAR(500) NOT NULL,
    ADD COLUMN hamluong       TEXT         NOT NULL,
    ADD COLUMN duongdung      TEXT         NOT NULL,
    ADD COLUMN nhom_kiem_soat VARCHAR(20)  NOT NULL,
    -- A medicine appears once per prescription.
    ADD CONSTRAINT uq_prescription_details_medicine UNIQUE (prescription_id, medicine_id),
    ADD CONSTRAINT ck_prescription_details_nhom_kiem_soat
        CHECK (nhom_kiem_soat IN ('THUONG', 'GAY_NGHIEN', 'HUONG_THAN', 'TIEN_CHAT')),
    -- Up to 90 days in total; more than 30 days only for a disease of the long-term list (TT 26/2025),
    -- which the application checks against the prescription's diagnoses.
    ADD CONSTRAINT ck_prescription_details_dosage CHECK (
        so_lan_ngay BETWEEN 1 AND 24
        AND so_ngay_dung BETWEEN 1 AND 90
        AND soluongthuocvattu > 0 AND soluongthuocvattu <= 9999);

-- The unique constraint on (prescription_id, medicine_id) serves lookups by prescription.
DROP INDEX idx_prescription_details_prescription_id;

-- Diagnoses sent with the prescription: the saved conclusion's main (thu_tu 1) and secondary diagnoses,
-- copied when the prescription is sent; the conclusion's diagnoses are locked after a successful send.
CREATE TABLE prescription_diagnoses (
    id              UUID         NOT NULL,
    prescription_id UUID         NOT NULL,
    maicd           VARCHAR(10)  NOT NULL,
    tenicd          VARCHAR(255) NOT NULL,
    thu_tu          SMALLINT     NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_prescription_diagnoses PRIMARY KEY (id),
    -- Also serves lookups by prescription.
    CONSTRAINT uq_prescription_diagnoses_code UNIQUE (prescription_id, maicd),
    CONSTRAINT uq_prescription_diagnoses_order UNIQUE (prescription_id, thu_tu),
    CONSTRAINT fk_prescription_diagnoses_prescription
        FOREIGN KEY (prescription_id) REFERENCES prescriptions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_prescription_diagnoses_maicd FOREIGN KEY (maicd) REFERENCES icd10 (maicd) ON DELETE RESTRICT,
    CONSTRAINT ck_prescription_diagnoses_thu_tu CHECK (thu_tu >= 1)
);

-- One attempt to send a prescription to the national prescription system (BR-47, UC-90..92).
CREATE TABLE prescription_submissions (
    id                      UUID        NOT NULL,
    prescription_id         UUID        NOT NULL,
    attempt_no              SMALLINT    NOT NULL,
    submitted_by_account_id UUID        NOT NULL,
    submitted_at            TIMESTAMPTZ NOT NULL,
    -- NO_RESPONSE: the system did not answer (MSG86).
    result                  VARCHAR(20) NOT NULL,
    -- Error list returned with a rejection, as a JSON array.
    errors                  JSONB,
    responded_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_prescription_submissions PRIMARY KEY (id),
    -- Also serves lookups by prescription.
    CONSTRAINT uq_prescription_submissions_attempt UNIQUE (prescription_id, attempt_no),
    CONSTRAINT fk_prescription_submissions_prescription
        FOREIGN KEY (prescription_id) REFERENCES prescriptions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_prescription_submissions_submitted_by
        FOREIGN KEY (submitted_by_account_id) REFERENCES staff_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT ck_prescription_submissions_attempt_no CHECK (attempt_no >= 1),
    CONSTRAINT ck_prescription_submissions_result CHECK (result IN ('ACCEPTED', 'REJECTED', 'NO_RESPONSE')),
    -- A rejection carries its error list; other results carry none.
    CONSTRAINT ck_prescription_submissions_errors CHECK (
        (result = 'REJECTED' AND errors IS NOT NULL AND jsonb_typeof(errors) = 'array')
        OR (result <> 'REJECTED' AND errors IS NULL)),
    -- An answered attempt has its answer time.
    CONSTRAINT ck_prescription_submissions_responded_at CHECK ((result = 'NO_RESPONSE') = (responded_at IS NULL))
);

-- A prescription is accepted at most once; it is locked afterwards.
CREATE UNIQUE INDEX uq_prescription_submissions_accepted
    ON prescription_submissions (prescription_id) WHERE result = 'ACCEPTED';

-- Diseases whose medicines may be prescribed for more than 30 days (TT 26/2025/TT-BYT Phụ lục VII).
-- Rows are loaded by the long-term-disease-import profile after the ICD-10 list, never by a migration
-- (docs/database-conventions.md).
CREATE TABLE long_term_prescription_diseases (
    id          UUID         NOT NULL,
    -- Row number (column 1) and specialty number (column 2, for example '4.13') in the appendix.
    stt         INTEGER      NOT NULL,
    ma_tt       VARCHAR(10)  NOT NULL,
    ten_benh    TEXT         NOT NULL,
    -- ICD-10 codes as the appendix writes them, for example 'E10 - E14 (trừ E10.0, ...)'. Null for the rows
    -- the appendix lists without a code; they match no diagnosis.
    ma_icd_goc  VARCHAR(255),
    van_ban     VARCHAR(100) NOT NULL,
    hieu_luc_tu DATE         NOT NULL,
    hieu_luc_den DATE,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    version     BIGINT       NOT NULL,
    CONSTRAINT pk_long_term_prescription_diseases PRIMARY KEY (id),
    CONSTRAINT uq_long_term_prescription_diseases_stt UNIQUE (van_ban, stt),
    CONSTRAINT ck_long_term_prescription_diseases_stt CHECK (stt >= 1),
    CONSTRAINT ck_long_term_prescription_diseases_validity CHECK (hieu_luc_den >= hieu_luc_tu)
);

-- The ICD-10 codes a disease of the list covers, with every child code of a range listed.
CREATE TABLE long_term_prescription_disease_codes (
    id         UUID        NOT NULL,
    disease_id UUID        NOT NULL,
    maicd      VARCHAR(10) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_long_term_prescription_disease_codes PRIMARY KEY (id),
    CONSTRAINT uq_long_term_prescription_disease_codes UNIQUE (disease_id, maicd),
    CONSTRAINT fk_long_term_prescription_disease_codes_disease
        FOREIGN KEY (disease_id) REFERENCES long_term_prescription_diseases (id) ON DELETE RESTRICT,
    CONSTRAINT fk_long_term_prescription_disease_codes_maicd
        FOREIGN KEY (maicd) REFERENCES icd10 (maicd) ON DELETE RESTRICT
);

-- Prescribing more than 30 days looks up the diagnosis codes of the prescription.
CREATE INDEX idx_long_term_prescription_disease_codes_maicd ON long_term_prescription_disease_codes (maicd);
