-- Medical record of a visit and its clinical content (export groups I to III), from the approved schema
-- (I1-D40): the record, personal history and allergies, the clinical examination with its findings,
-- vital signs, the conclusion with its secondary diagnoses, and the follow-up appointment.

-- One record per visit; the root of one export package. Locked when closed (BR-32); a locked record
-- is changed only after an unlock with a reason.
CREATE TABLE medical_records (
    id                      UUID         NOT NULL,
    visit_id                UUID         NOT NULL,
    soba                    VARCHAR(100) NOT NULL,
    loaiba                  VARCHAR(20)  NOT NULL,
    soluutru                VARCHAR(200),
    tiensubenhtatcuagiadinh TEXT,
    status                  VARCHAR(20)  NOT NULL,
    opened_at               TIMESTAMPTZ  NOT NULL,
    opened_by_account_id    UUID         NOT NULL,
    closed_at               TIMESTAMPTZ,
    closed_by_account_id    UUID,
    unlocked_at             TIMESTAMPTZ,
    unlocked_by_account_id  UUID,
    unlock_reason           TEXT,
    created_at              TIMESTAMPTZ  NOT NULL,
    updated_at              TIMESTAMPTZ  NOT NULL,
    version                 BIGINT       NOT NULL,
    CONSTRAINT pk_medical_records PRIMARY KEY (id),
    CONSTRAINT uq_medical_records_visit_id UNIQUE (visit_id),
    CONSTRAINT uq_medical_records_soba UNIQUE (soba),
    CONSTRAINT uq_medical_records_soluutru UNIQUE (soluutru),
    CONSTRAINT fk_medical_records_visit FOREIGN KEY (visit_id) REFERENCES visits (id) ON DELETE RESTRICT,
    CONSTRAINT fk_medical_records_loaiba
        FOREIGN KEY (loaiba) REFERENCES medical_record_types (loaiba) ON DELETE RESTRICT,
    CONSTRAINT fk_medical_records_opened_by
        FOREIGN KEY (opened_by_account_id) REFERENCES staff_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_medical_records_closed_by
        FOREIGN KEY (closed_by_account_id) REFERENCES staff_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_medical_records_unlocked_by
        FOREIGN KEY (unlocked_by_account_id) REFERENCES staff_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT ck_medical_records_status CHECK (status IN ('OPEN', 'LOCKED', 'ARCHIVED')),
    CONSTRAINT ck_medical_records_unlock_reason CHECK (unlocked_at IS NULL OR unlock_reason IS NOT NULL),
    CONSTRAINT ck_medical_records_unlock
        CHECK ((unlocked_at IS NULL) = (unlocked_by_account_id IS NULL))
);

-- Personal history and drug allergies (I.45, BR-23). The patient is required so an allergy recorded
-- in one visit is still found in later visits.
CREATE TABLE medical_histories (
    id                    UUID         NOT NULL,
    patient_id            UUID         NOT NULL,
    medical_record_id     UUID,
    history_type          VARCHAR(30)  NOT NULL,
    thongtin_ma           VARCHAR(200),
    thongtin_noidung      TEXT         NOT NULL,
    thongtin_giatri       VARCHAR(100),
    thongtin_ghichu       TEXT,
    updated_by_account_id UUID         NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    version               BIGINT       NOT NULL,
    CONSTRAINT pk_medical_histories PRIMARY KEY (id),
    CONSTRAINT fk_medical_histories_patient FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE RESTRICT,
    CONSTRAINT fk_medical_histories_medical_record
        FOREIGN KEY (medical_record_id) REFERENCES medical_records (id) ON DELETE RESTRICT,
    CONSTRAINT fk_medical_histories_updated_by
        FOREIGN KEY (updated_by_account_id) REFERENCES staff_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT ck_medical_histories_history_type CHECK (history_type IN ('PERSONAL', 'DRUG_ALLERGY'))
);

CREATE INDEX idx_medical_histories_patient_id ON medical_histories (patient_id);
CREATE INDEX idx_medical_histories_medical_record_id ON medical_histories (medical_record_id);

-- Clinical examination (export group II); one per record.
CREATE TABLE clinical_examinations (
    id                            UUID         NOT NULL,
    medical_record_id             UUID         NOT NULL,
    sophieu                       VARCHAR(100) NOT NULL,
    examined_by_account_id        UUID         NOT NULL,
    chandoancuanoigioithieu       TEXT,
    quatrinhbenhly                TEXT,
    khambenh_toanthan             TEXT,
    chandoansobo                  TEXT,
    chandoanvaovien               TEXT,
    khambenh_chandoanvaovienmaicd VARCHAR(10),
    created_at                    TIMESTAMPTZ  NOT NULL,
    updated_at                    TIMESTAMPTZ  NOT NULL,
    version                       BIGINT       NOT NULL,
    CONSTRAINT pk_clinical_examinations PRIMARY KEY (id),
    CONSTRAINT uq_clinical_examinations_medical_record_id UNIQUE (medical_record_id),
    CONSTRAINT uq_clinical_examinations_sophieu UNIQUE (sophieu),
    CONSTRAINT fk_clinical_examinations_medical_record
        FOREIGN KEY (medical_record_id) REFERENCES medical_records (id) ON DELETE RESTRICT,
    CONSTRAINT fk_clinical_examinations_examined_by
        FOREIGN KEY (examined_by_account_id) REFERENCES staff_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_clinical_examinations_khambenh_chandoanvaovienmaicd
        FOREIGN KEY (khambenh_chandoanvaovienmaicd) REFERENCES icd10 (maicd) ON DELETE RESTRICT
);

-- Signs per organ system (II.25); exported grouped by coquan.
CREATE TABLE examination_findings (
    id              UUID        NOT NULL,
    examination_id  UUID        NOT NULL,
    coquan          VARCHAR(50) NOT NULL,
    vncode          VARCHAR(25),
    snomed          VARCHAR(25),
    dauchung        TEXT        NOT NULL,
    dauchung_ghichu TEXT,
    created_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_examination_findings PRIMARY KEY (id),
    CONSTRAINT fk_examination_findings_examination
        FOREIGN KEY (examination_id) REFERENCES clinical_examinations (id) ON DELETE RESTRICT
);

CREATE INDEX idx_examination_findings_examination_id ON examination_findings (examination_id);

-- Vital signs. The initial measurement is exported in group II; every measurement in III.30.
CREATE TABLE vital_signs (
    id                     UUID         NOT NULL,
    medical_record_id      UUID         NOT NULL,
    is_initial             BOOLEAN      NOT NULL DEFAULT FALSE,
    mach                   SMALLINT,
    nhietdo                NUMERIC(4,1),
    nhiptho                SMALLINT,
    huyetap_tamthu         SMALLINT,
    huyetap_tamtruong      SMALLINT,
    cannang                NUMERIC(5,2),
    chieucao               NUMERIC(5,1),
    spo2                   SMALLINT,
    thoidiem               TIMESTAMPTZ  NOT NULL,
    measured_by_account_id UUID         NOT NULL,
    created_at             TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_vital_signs PRIMARY KEY (id),
    CONSTRAINT fk_vital_signs_medical_record
        FOREIGN KEY (medical_record_id) REFERENCES medical_records (id) ON DELETE RESTRICT,
    CONSTRAINT fk_vital_signs_measured_by
        FOREIGN KEY (measured_by_account_id) REFERENCES staff_accounts (id) ON DELETE RESTRICT
);

CREATE INDEX idx_vital_signs_medical_record_id ON vital_signs (medical_record_id);
-- Group II holds one measurement, so a record has at most one initial measurement (I1-D41).
CREATE UNIQUE INDEX uq_vital_signs_initial ON vital_signs (medical_record_id) WHERE is_initial;

-- Conclusion (export group III); one per record. Secondary diagnoses are rows of
-- conclusion_secondary_diagnoses, joined with ";" when exported (III.21, III.22).
CREATE TABLE conclusions (
    id                UUID         NOT NULL,
    medical_record_id UUID         NOT NULL,
    doctor_account_id UUID         NOT NULL,
    chandoan          TEXT         NOT NULL,
    chandoan_ma       VARCHAR(255),
    maicd             VARCHAR(10)  NOT NULL,
    tenicd            VARCHAR(255) NOT NULL,
    huongdieutri      TEXT         NOT NULL,
    tomtat            TEXT,
    ketquadieutri     SMALLINT     NOT NULL,
    loidanbacsi       TEXT         NOT NULL,
    ngayravien        DATE,
    ghichu            TEXT,
    concluded_at      TIMESTAMPTZ  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    version           BIGINT       NOT NULL,
    CONSTRAINT pk_conclusions PRIMARY KEY (id),
    CONSTRAINT uq_conclusions_medical_record_id UNIQUE (medical_record_id),
    CONSTRAINT fk_conclusions_medical_record
        FOREIGN KEY (medical_record_id) REFERENCES medical_records (id) ON DELETE RESTRICT,
    CONSTRAINT fk_conclusions_doctor
        FOREIGN KEY (doctor_account_id) REFERENCES staff_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_conclusions_maicd FOREIGN KEY (maicd) REFERENCES icd10 (maicd) ON DELETE RESTRICT
);

CREATE TABLE conclusion_secondary_diagnoses (
    id            UUID         NOT NULL,
    conclusion_id UUID         NOT NULL,
    maicd         VARCHAR(10)  NOT NULL,
    tenicd        VARCHAR(255) NOT NULL,
    thu_tu        SMALLINT     NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_conclusion_secondary_diagnoses PRIMARY KEY (id),
    -- A code appears once per conclusion; it also serves lookups by conclusion.
    CONSTRAINT uq_conclusion_secondary_diagnoses_conclusion_maicd UNIQUE (conclusion_id, maicd),
    CONSTRAINT fk_conclusion_secondary_diagnoses_conclusion
        FOREIGN KEY (conclusion_id) REFERENCES conclusions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_conclusion_secondary_diagnoses_maicd
        FOREIGN KEY (maicd) REFERENCES icd10 (maicd) ON DELETE RESTRICT,
    CONSTRAINT ck_conclusion_secondary_diagnoses_thu_tu CHECK (thu_tu >= 1)
);

-- Follow-up appointment created at conclusion (BR-34); one per record.
CREATE TABLE follow_up_appointments (
    id                    UUID        NOT NULL,
    medical_record_id     UUID        NOT NULL,
    so_giay_hen           VARCHAR(50),
    patient_id            UUID        NOT NULL,
    doctor_account_id     UUID        NOT NULL,
    maicd                 VARCHAR(10),
    follow_up_date        DATE        NOT NULL,
    loidanbacsi           TEXT,
    status                VARCHAR(20) NOT NULL,
    arrived_visit_id      UUID,
    printed_at            TIMESTAMPTZ,
    cancel_reason         TEXT,
    created_by_account_id UUID        NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL,
    version               BIGINT      NOT NULL,
    CONSTRAINT pk_follow_up_appointments PRIMARY KEY (id),
    CONSTRAINT uq_follow_up_appointments_medical_record_id UNIQUE (medical_record_id),
    CONSTRAINT uq_follow_up_appointments_so_giay_hen UNIQUE (so_giay_hen),
    CONSTRAINT fk_follow_up_appointments_medical_record
        FOREIGN KEY (medical_record_id) REFERENCES medical_records (id) ON DELETE RESTRICT,
    CONSTRAINT fk_follow_up_appointments_patient
        FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE RESTRICT,
    CONSTRAINT fk_follow_up_appointments_doctor
        FOREIGN KEY (doctor_account_id) REFERENCES staff_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_follow_up_appointments_maicd FOREIGN KEY (maicd) REFERENCES icd10 (maicd) ON DELETE RESTRICT,
    CONSTRAINT fk_follow_up_appointments_arrived_visit
        FOREIGN KEY (arrived_visit_id) REFERENCES visits (id) ON DELETE RESTRICT,
    CONSTRAINT fk_follow_up_appointments_created_by
        FOREIGN KEY (created_by_account_id) REFERENCES staff_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT ck_follow_up_appointments_status
        CHECK (status IN ('SCHEDULED', 'ARRIVED', 'MISSED', 'CANCELLED')),
    CONSTRAINT ck_follow_up_appointments_arrived
        CHECK ((status = 'ARRIVED') = (arrived_visit_id IS NOT NULL))
);

-- Scheduled appointments of a patient, for reception.
CREATE INDEX idx_follow_up_appointments_patient_date ON follow_up_appointments (patient_id, follow_up_date);
CREATE INDEX idx_follow_up_appointments_arrived_visit_id ON follow_up_appointments (arrived_visit_id);

-- The visit that carries out an appointment (V6 created the column before this table existed).
ALTER TABLE visits ADD CONSTRAINT fk_visits_followup_appointment
    FOREIGN KEY (followup_appointment_id) REFERENCES follow_up_appointments (id) ON DELETE RESTRICT;
CREATE INDEX idx_visits_followup_appointment_id ON visits (followup_appointment_id);
