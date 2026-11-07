ALTER TABLE clinical_examinations
    ADD COLUMN khambenh_bophan TEXT;

UPDATE clinical_examinations examination
SET khambenh_bophan = legacy.combined_finding
FROM (
    SELECT examination_id,
           string_agg(coquan || ': ' || dauchung
               || CASE WHEN dauchung_ghichu IS NULL THEN '' ELSE ' (' || dauchung_ghichu || ')' END,
               E'\n' ORDER BY created_at) AS combined_finding
    FROM examination_findings
    GROUP BY examination_id
) legacy
WHERE examination.id = legacy.examination_id;
