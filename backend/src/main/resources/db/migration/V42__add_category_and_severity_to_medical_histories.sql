-- UC-39 Update Medical History and Allergies (BR-23).
-- category: group of a personal-history entry (chronic disease, past surgery, family history, other).
-- severity: severity of a drug allergy. thongtin_ghichu keeps the reaction of an allergy, and
-- thongtin_giatri = '0' marks the "no known drug allergy" statement (CV 365 I.45.3: 1 có / 0 không).
ALTER TABLE medical_histories
    ADD COLUMN category VARCHAR(30),
    ADD COLUMN severity VARCHAR(20);

ALTER TABLE medical_histories
    ADD CONSTRAINT ck_medical_histories_category
        CHECK (category IS NULL OR category IN ('CHRONIC_DISEASE', 'SURGERY', 'FAMILY', 'OTHER')),
    ADD CONSTRAINT ck_medical_histories_severity
        CHECK (severity IS NULL OR severity IN ('MILD', 'MODERATE', 'SEVERE'));
