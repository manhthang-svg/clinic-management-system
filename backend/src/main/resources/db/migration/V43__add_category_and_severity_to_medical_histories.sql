-- UC-39 Update Medical History and Allergies (BR-23).
-- category: group of a personal-history entry (chronic disease, past surgery, family history, other).
-- severity: severity of a drug allergy. thongtin_ghichu keeps the reaction of an allergy, and
-- thongtin_giatri = '0' marks the "no known drug allergy" statement (CV 365 I.45.3: 1 có / 0 không).
--
-- This script was first pushed as V42, which clashed with another V42 already applied to the shared
-- database. Databases that ran the old V42 already have these columns, so every statement is idempotent.
ALTER TABLE medical_histories
    ADD COLUMN IF NOT EXISTS category VARCHAR(30),
    ADD COLUMN IF NOT EXISTS severity VARCHAR(20);

ALTER TABLE medical_histories DROP CONSTRAINT IF EXISTS ck_medical_histories_category;
ALTER TABLE medical_histories DROP CONSTRAINT IF EXISTS ck_medical_histories_severity;

ALTER TABLE medical_histories
    ADD CONSTRAINT ck_medical_histories_category
        CHECK (category IS NULL OR category IN ('CHRONIC_DISEASE', 'SURGERY', 'FAMILY', 'OTHER')),
    ADD CONSTRAINT ck_medical_histories_severity
        CHECK (severity IS NULL OR severity IN ('MILD', 'MODERATE', 'SEVERE'));
