-- Removes two structures that nothing reads or writes.

-- Normal-result templates (V32) have no use case and no owner; the frontend keeps its pre-fill texts as
-- constants until a use case for maintaining templates exists.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM imaging_result_templates) THEN
        RAISE EXCEPTION 'imaging_result_templates still holds templates; export them before V34';
    END IF;
END $$;

DROP TABLE imaging_result_templates;

-- The result time of an imaging result (VI.2.3) is its submission time, as for lab results; the export
-- reads it from submitted_at.
ALTER TABLE imaging_results DROP COLUMN ngayketqua;
