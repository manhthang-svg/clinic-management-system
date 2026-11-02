-- Paraclinical result slips (UC-67, UC-68): the fields printed on the clinic's result forms that the
-- schema did not hold yet. ASSUMPTION until the team confirms; see docs/paraclinical-results-db.md.
-- Structure only: no result templates or catalog values are seeded (I1-D01, I1-D02).

-- Imaging and functional results.
ALTER TABLE imaging_results
    -- "Kỹ thuật" line of the CT and MRI slips.
    ADD COLUMN kqcls_kythuat TEXT,
    -- Per-organ / per-site fields (ultrasound, endoscopy, ECG) as entered, keyed by field code, so that a
    -- draft can be edited again. kqcls_mota stays the printed and exported text.
    ADD COLUMN kqcls_mota_chitiet JSONB,
    -- Specialist doctor who reads and signs the slip. ASSUMPTION: free text, because the reading doctor
    -- may have no account (for example an outside radiologist). The performer stays the technician.
    ADD COLUMN reading_doctor_name VARCHAR(255),
    -- Number of prints; from the second print the slip is marked "Bản sao" (UC-68 AF-01).
    ADD COLUMN print_count INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_imaging_results_mota_chitiet
        CHECK (kqcls_mota_chitiet IS NULL OR jsonb_typeof(kqcls_mota_chitiet) = 'object'),
    ADD CONSTRAINT ck_imaging_results_reading_doctor_name
        CHECK (reading_doctor_name IS NULL OR btrim(reading_doctor_name) <> ''),
    ADD CONSTRAINT ck_imaging_results_print_count CHECK (print_count >= 0),
    -- Only a submitted result can be printed (UC-68 EF-01).
    ADD CONSTRAINT ck_imaging_results_print_submitted CHECK (print_count = 0 OR status = 'SUBMITTED');

-- Lab results. The "Nhận xét" line of the lab slip uses the existing ghichu column.
ALTER TABLE lab_results
    ADD COLUMN print_count INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_lab_results_print_count CHECK (print_count >= 0),
    ADD CONSTRAINT ck_lab_results_print_submitted CHECK (print_count = 0 OR status = 'SUBMITTED');

-- Images on the slip: order, caption and whether the image is printed (endoscopy prints four in a row,
-- ultrasound two beside the text).
ALTER TABLE result_files
    ADD COLUMN display_order INTEGER,
    ADD COLUMN caption VARCHAR(255),
    ADD COLUMN print_on_slip BOOLEAN NOT NULL DEFAULT TRUE,
    ADD CONSTRAINT ck_result_files_display_order CHECK (display_order IS NULL OR display_order > 0);

-- One position per image within a result.
CREATE UNIQUE INDEX uq_result_files_imaging_result_order
    ON result_files (imaging_result_id, display_order)
    WHERE imaging_result_id IS NOT NULL AND display_order IS NOT NULL;
CREATE UNIQUE INDEX uq_result_files_lab_result_order
    ON result_files (lab_result_id, display_order)
    WHERE lab_result_id IS NOT NULL AND display_order IS NOT NULL;

-- MRI safety screening written by the ordering doctor and printed on the MRI slip.
-- NULL means not asked (services other than MRI).
ALTER TABLE paraclinical_order_details
    ADD COLUMN has_metal_implant BOOLEAN,
    ADD COLUMN prior_results TEXT;

-- Normal-result templates that pre-fill a descriptive result. A template only pre-fills the form; a saved
-- result keeps its own text, so editing a template never changes past results.
-- TBD: who maintains templates (no use case yet).
CREATE TABLE imaging_result_templates (
    id                 UUID         NOT NULL,
    service_id         UUID         NOT NULL,
    name               VARCHAR(255) NOT NULL,
    kqcls_kythuat      TEXT,
    kqcls_mota         TEXT,
    kqcls_mota_chitiet JSONB,
    kqcls_ketluan      TEXT,
    default_template   BOOLEAN      NOT NULL DEFAULT FALSE,
    active             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    version            BIGINT       NOT NULL,
    CONSTRAINT pk_imaging_result_templates PRIMARY KEY (id),
    CONSTRAINT fk_imaging_result_templates_service
        FOREIGN KEY (service_id) REFERENCES services (id) ON DELETE RESTRICT,
    CONSTRAINT ck_imaging_result_templates_name CHECK (btrim(name) <> ''),
    -- A template fills either the free-text description or the per-organ fields.
    CONSTRAINT ck_imaging_result_templates_content
        CHECK (kqcls_mota IS NOT NULL OR kqcls_mota_chitiet IS NOT NULL),
    CONSTRAINT ck_imaging_result_templates_mota_chitiet
        CHECK (kqcls_mota_chitiet IS NULL OR jsonb_typeof(kqcls_mota_chitiet) = 'object'),
    CONSTRAINT ck_imaging_result_templates_default_active CHECK (NOT default_template OR active)
);

-- Templates of a service, for the "Điền kết quả bình thường" button.
CREATE INDEX idx_imaging_result_templates_service_id ON imaging_result_templates (service_id);
-- At most one default template per service.
CREATE UNIQUE INDEX uq_imaging_result_templates_default
    ON imaging_result_templates (service_id) WHERE default_template;
