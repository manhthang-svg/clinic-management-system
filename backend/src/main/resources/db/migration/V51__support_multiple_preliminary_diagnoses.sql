CREATE TABLE clinical_examination_diagnoses (
    id UUID PRIMARY KEY,
    examination_id UUID NOT NULL REFERENCES clinical_examinations(id) ON DELETE CASCADE,
    maicd VARCHAR(10) NOT NULL,
    tenicd VARCHAR(255) NOT NULL,
    thu_tu SMALLINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_clinical_examination_diagnoses_code UNIQUE (examination_id, maicd),
    CONSTRAINT uq_clinical_examination_diagnoses_order UNIQUE (examination_id, thu_tu)
);

INSERT INTO clinical_examination_diagnoses (id, examination_id, maicd, tenicd, thu_tu, created_at)
SELECT gen_random_uuid(), id, khambenh_chandoanvaovienmaicd, chandoanvaovien, 1, created_at
FROM clinical_examinations
WHERE khambenh_chandoanvaovienmaicd IS NOT NULL
  AND chandoanvaovien IS NOT NULL;

