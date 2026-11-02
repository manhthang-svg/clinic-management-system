-- Result files belong to imaging and functional results only. A lab result is fully held by lab_result_items,
-- and no use case attaches files to it (the partner-lab PDF of V8 was an assumption without a use case).

-- Refuse to drop data: lab result files would be lost silently.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM result_files WHERE lab_result_id IS NOT NULL) THEN
        RAISE EXCEPTION 'result_files still holds lab result files; move or delete them before V33';
    END IF;
END $$;

DROP INDEX uq_result_files_lab_result_order;
DROP INDEX idx_result_files_lab_result_id;

ALTER TABLE result_files
    DROP CONSTRAINT ck_result_files_owner,
    DROP CONSTRAINT fk_result_files_lab_result,
    DROP COLUMN lab_result_id,
    ALTER COLUMN imaging_result_id SET NOT NULL;

-- One position per image within a result; imaging_result_id is never NULL any more.
DROP INDEX uq_result_files_imaging_result_order;
CREATE UNIQUE INDEX uq_result_files_imaging_result_order
    ON result_files (imaging_result_id, display_order)
    WHERE display_order IS NOT NULL;
