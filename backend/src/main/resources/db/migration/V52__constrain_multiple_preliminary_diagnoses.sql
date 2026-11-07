ALTER TABLE clinical_examination_diagnoses
    ADD CONSTRAINT fk_clinical_examination_diagnoses_icd10
        FOREIGN KEY (maicd) REFERENCES icd10(maicd) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_clinical_examination_diagnoses_order CHECK (thu_tu >= 1);

CREATE INDEX idx_clinical_examination_diagnoses_examination
    ON clinical_examination_diagnoses(examination_id);
