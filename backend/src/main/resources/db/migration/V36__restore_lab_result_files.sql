-- Reverts V33: Report 3 UC-67 lets the Technician attach files (images or PDF) to every result, lab results
-- included. Restores the lab_result_id column and the constraints of V8 and V32.

ALTER TABLE result_files
    ADD COLUMN lab_result_id UUID,
    ALTER COLUMN imaging_result_id DROP NOT NULL,
    ADD CONSTRAINT fk_result_files_lab_result
        FOREIGN KEY (lab_result_id) REFERENCES lab_results (id) ON DELETE RESTRICT,
    -- A file belongs to exactly one result (I1-D41).
    ADD CONSTRAINT ck_result_files_owner CHECK ((imaging_result_id IS NULL) <> (lab_result_id IS NULL));

CREATE INDEX idx_result_files_lab_result_id ON result_files (lab_result_id);

-- One position per image within a result.
DROP INDEX uq_result_files_imaging_result_order;
CREATE UNIQUE INDEX uq_result_files_imaging_result_order
    ON result_files (imaging_result_id, display_order)
    WHERE imaging_result_id IS NOT NULL AND display_order IS NOT NULL;
CREATE UNIQUE INDEX uq_result_files_lab_result_order
    ON result_files (lab_result_id, display_order)
    WHERE lab_result_id IS NOT NULL AND display_order IS NOT NULL;
