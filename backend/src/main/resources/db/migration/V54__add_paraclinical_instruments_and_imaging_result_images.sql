-- UC-67 Record Paraclinical Result: results are entered by hand; the system only records which instrument ran
-- them. ASSUMPTION until the SRS is updated (decision of the UC-67 owner, 2026-11-09):
--   * a lab result is its list of lab_result_items only, with no attached files (no PDF);
--   * an imaging or functional result is its text plus a list of images.
-- The instrument catalog is split in two by the result it records: LAB instruments run lab_result_items,
-- IMAGING instruments run imaging_results (imaging and functional tests).

-- 1. Instrument catalog.
CREATE TABLE paraclinical_instruments (
    id          UUID         NOT NULL,
    code        VARCHAR(20)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    result_type VARCHAR(20)  NOT NULL,
    room_id     UUID         NOT NULL,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    version     BIGINT       NOT NULL,
    CONSTRAINT pk_paraclinical_instruments PRIMARY KEY (id),
    CONSTRAINT uq_paraclinical_instruments_code UNIQUE (code),
    -- Target of the typed foreign keys below.
    CONSTRAINT uq_paraclinical_instruments_id_result_type UNIQUE (id, result_type),
    CONSTRAINT fk_paraclinical_instruments_room
        FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE RESTRICT,
    CONSTRAINT ck_paraclinical_instruments_result_type CHECK (result_type IN ('LAB', 'IMAGING')),
    CONSTRAINT ck_paraclinical_instruments_code CHECK (code = btrim(code) AND char_length(code) BETWEEN 2 AND 20),
    CONSTRAINT ck_paraclinical_instruments_name CHECK (char_length(btrim(name)) >= 2)
);

-- The instruments of a room, for the instrument picker of the result form.
CREATE INDEX idx_paraclinical_instruments_room_id ON paraclinical_instruments (room_id);

-- 2. Lab part: the instrument of each measured value. instrument_result_type is fixed to LAB, so the
-- composite foreign key accepts only LAB instruments. The name is kept as printed when the result is submitted.
ALTER TABLE lab_result_items
    ADD COLUMN instrument_id            UUID,
    ADD COLUMN instrument_result_type   VARCHAR(20) NOT NULL DEFAULT 'LAB',
    ADD COLUMN instrument_name_snapshot VARCHAR(255),
    ADD CONSTRAINT ck_lab_result_items_instrument_result_type CHECK (instrument_result_type = 'LAB'),
    ADD CONSTRAINT fk_lab_result_items_instrument
        FOREIGN KEY (instrument_id, instrument_result_type)
        REFERENCES paraclinical_instruments (id, result_type) ON DELETE RESTRICT;

-- 3. Imaging part: one instrument for the whole result, IMAGING instruments only.
ALTER TABLE imaging_results
    ADD COLUMN instrument_id            UUID,
    ADD COLUMN instrument_result_type   VARCHAR(20) NOT NULL DEFAULT 'IMAGING',
    ADD COLUMN instrument_name_snapshot VARCHAR(255),
    ADD CONSTRAINT ck_imaging_results_instrument_result_type CHECK (instrument_result_type = 'IMAGING'),
    ADD CONSTRAINT fk_imaging_results_instrument
        FOREIGN KEY (instrument_id, instrument_result_type)
        REFERENCES paraclinical_instruments (id, result_type) ON DELETE RESTRICT;

-- 4. result_files becomes the image list of an imaging result. Refuse to drop data: lab files or non-image
-- files would be lost silently.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM result_files WHERE lab_result_id IS NOT NULL) THEN
        RAISE EXCEPTION 'result_files still holds lab result files; move or delete them before V54';
    END IF;
    IF EXISTS (SELECT 1 FROM result_files WHERE mime_type NOT LIKE 'image/%') THEN
        RAISE EXCEPTION 'result_files still holds non-image files; move or delete them before V54';
    END IF;
END $$;

DROP INDEX uq_result_files_lab_result_order;
DROP INDEX idx_result_files_lab_result_id;
DROP INDEX uq_result_files_imaging_result_order;

ALTER TABLE result_files
    DROP CONSTRAINT ck_result_files_owner,
    DROP CONSTRAINT fk_result_files_lab_result,
    DROP COLUMN lab_result_id,
    ALTER COLUMN imaging_result_id SET NOT NULL;

ALTER TABLE result_files RENAME TO imaging_result_images;
ALTER TABLE imaging_result_images RENAME CONSTRAINT pk_result_files TO pk_imaging_result_images;
ALTER TABLE imaging_result_images
    RENAME CONSTRAINT fk_result_files_imaging_result TO fk_imaging_result_images_imaging_result;
ALTER TABLE imaging_result_images
    RENAME CONSTRAINT fk_result_files_uploaded_by TO fk_imaging_result_images_uploaded_by;
ALTER TABLE imaging_result_images
    RENAME CONSTRAINT ck_result_files_file_sha256 TO ck_imaging_result_images_file_sha256;
ALTER TABLE imaging_result_images
    RENAME CONSTRAINT ck_result_files_display_order TO ck_imaging_result_images_display_order;
ALTER INDEX idx_result_files_imaging_result_id RENAME TO idx_imaging_result_images_imaging_result_id;

ALTER TABLE imaging_result_images
    ADD CONSTRAINT ck_imaging_result_images_mime_type CHECK (mime_type LIKE 'image/%');

-- One position per image within a result.
CREATE UNIQUE INDEX uq_imaging_result_images_order
    ON imaging_result_images (imaging_result_id, display_order)
    WHERE display_order IS NOT NULL;
