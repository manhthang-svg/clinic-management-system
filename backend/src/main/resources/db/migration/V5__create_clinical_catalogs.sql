-- Clinical catalogs from the approved schema (I1-D40): medical-record forms, ICD-10 codes, the medicine
-- catalog, and the clinic facility. No reference rows are loaded here; the published lists are loaded
-- by an approved data migration once an owner is assigned (I1-D02).

-- Medical-record forms of Appendix XXVIII, TT 32/2023/TT-BYT. Natural key: the form code (I.1).
-- Old forms stay inactive so old records can still be read.
CREATE TABLE medical_record_types (
    loaiba     VARCHAR(20)  NOT NULL,
    ten        VARCHAR(255) NOT NULL,
    pham_vi    VARCHAR(20)  NOT NULL,
    van_ban    VARCHAR(50)  NOT NULL,
    is_default BOOLEAN      NOT NULL DEFAULT FALSE,
    is_active  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    version    BIGINT       NOT NULL,
    CONSTRAINT pk_medical_record_types PRIMARY KEY (loaiba),
    CONSTRAINT ck_medical_record_types_pham_vi CHECK (pham_vi IN ('OUTPATIENT', 'INPATIENT'))
);

-- A new medical record takes the single default form.
CREATE UNIQUE INDEX uq_medical_record_types_default ON medical_record_types (is_default) WHERE is_default;

-- ICD-10 list of TT 06/2026/TT-BYT. Natural key: the code (III.19).
CREATE TABLE icd10 (
    maicd                         VARCHAR(10)  NOT NULL,
    tenicd                        VARCHAR(255) NOT NULL,
    -- Columns 24, 25 and 27 of the list: never, preferably not, or only as a cause of death a main diagnosis.
    khong_la_benh_chinh           BOOLEAN      NOT NULL DEFAULT FALSE,
    khong_khuyen_khich_benh_chinh BOOLEAN      NOT NULL DEFAULT FALSE,
    chi_nguyen_nhan_tu_vong       BOOLEAN      NOT NULL DEFAULT FALSE,
    hieu_luc_tu                   DATE         NOT NULL,
    hieu_luc_den                  DATE,
    -- A chronic disease that needs follow-up (BR-34).
    is_chronic                    BOOLEAN      NOT NULL DEFAULT FALSE,
    is_active                     BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_icd10 PRIMARY KEY (maicd),
    CONSTRAINT ck_icd10_validity CHECK (hieu_luc_den >= hieu_luc_tu)
);

-- Medicines that can be prescribed; no price and no stock (BR-33).
CREATE TABLE medicine_catalog (
    id               UUID         NOT NULL,
    mathuocvattu_bv  VARCHAR(255) NOT NULL,
    mathuocvattu_byt VARCHAR(255),
    tenthuocvattu    VARCHAR(255) NOT NULL,
    donvitinh        VARCHAR(50)  NOT NULL,
    nhom             VARCHAR(255),
    loaithuoc        SMALLINT,
    status           VARCHAR(20)  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    version          BIGINT       NOT NULL,
    CONSTRAINT pk_medicine_catalog PRIMARY KEY (id),
    CONSTRAINT uq_medicine_catalog_mathuocvattu_bv UNIQUE (mathuocvattu_bv),
    CONSTRAINT ck_medicine_catalog_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

-- The clinic itself: the facility code starts every prescription code (TT 26/2025/TT-BYT).
-- Credentials for the national prescription system are kept in the secret store, never here.
CREATE TABLE clinic_facility (
    id                     UUID          NOT NULL,
    ma_cskcb               VARCHAR(5)    NOT NULL,
    ten_cskcb              VARCHAR(255)  NOT NULL,
    diachi                 VARCHAR(1024) NOT NULL,
    sodienthoai            VARCHAR(15)   NOT NULL,
    so_giay_phep_hoat_dong VARCHAR(100)  NOT NULL,
    hinh_thuc_to_chuc      VARCHAR(50)   NOT NULL,
    ma_lienthong_donthuoc  VARCHAR(50),
    created_at             TIMESTAMPTZ   NOT NULL,
    updated_at             TIMESTAMPTZ   NOT NULL,
    version                BIGINT        NOT NULL,
    CONSTRAINT pk_clinic_facility PRIMARY KEY (id),
    CONSTRAINT uq_clinic_facility_ma_cskcb UNIQUE (ma_cskcb),
    CONSTRAINT ck_clinic_facility_ma_cskcb CHECK (char_length(ma_cskcb) = 5)
);

-- At most one row: the index is on a constant.
CREATE UNIQUE INDEX uq_clinic_facility_single_row ON clinic_facility ((TRUE));
