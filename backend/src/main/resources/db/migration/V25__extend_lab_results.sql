-- Flyway 3/4: evolve existing LAB result tables without dropping old data.
-- Legacy free-text columns remain intact for historical display/export.

ALTER TABLE public.lab_results
    ADD COLUMN specimen_type varchar(50),
    ADD COLUMN received_at timestamptz,
    ADD COLUMN verified_by_account_id uuid,
    ADD COLUMN verified_at timestamptz,
    ADD CONSTRAINT fk_lab_results_verified_by
        FOREIGN KEY (verified_by_account_id)
        REFERENCES public.staff_accounts(id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_lab_results_verified_pair
        CHECK ((verified_by_account_id IS NULL) = (verified_at IS NULL));

-- Allow DRAFT result shells prior to staff assignment.
-- Application must require performer_account_id for new submissions.
ALTER TABLE public.lab_results
    ALTER COLUMN performer_account_id DROP NOT NULL;

ALTER TABLE public.lab_result_items
    ADD COLUMN service_lab_parameter_id uuid,
    ADD COLUMN numeric_value numeric,
    ADD COLUMN qualitative_code varchar(50),
    ADD COLUMN result_status varchar(20) NOT NULL DEFAULT 'RESULTED',
    ADD COLUMN interpretation varchar(30),
    ADD COLUMN note text,
    ADD COLUMN display_order_snapshot integer,
    ADD COLUMN section_name_snapshot varchar(100),
    ADD COLUMN group_name_snapshot varchar(100),
    ADD COLUMN subgroup_name_snapshot varchar(100),
    ADD COLUMN created_at timestamptz,
    ADD CONSTRAINT fk_lab_result_items_service_lab_parameter
        FOREIGN KEY (service_lab_parameter_id)
        REFERENCES public.service_lab_parameters(id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_lab_result_items_result_status
        CHECK (result_status IN (
            'PENDING', 'RESULTED', 'NOT_PERFORMED', 'NOT_APPLICABLE'
        )),
    ADD CONSTRAINT ck_lab_result_items_interpretation
        CHECK (interpretation IS NULL OR interpretation IN (
            'NORMAL', 'LOW', 'HIGH', 'ABNORMAL',
            'INDETERMINATE', 'NOT_EVALUATED'
        )),
    ADD CONSTRAINT ck_lab_result_items_display_order
        CHECK (display_order_snapshot IS NULL OR display_order_snapshot > 0);

ALTER TABLE public.lab_result_items
    ALTER COLUMN ketqua DROP NOT NULL,
ALTER COLUMN trisothamchieu TYPE varchar(255);

-- Turn any historical empty-string results into an explicit pending state.
UPDATE public.lab_result_items
SET result_status = 'PENDING', ketqua = NULL
WHERE ketqua IS NULL OR btrim(ketqua) = '';

-- Preserve historical metadata from the parent result if available.
UPDATE public.lab_result_items AS i
SET created_at = lr.created_at
    FROM public.lab_results AS lr
WHERE i.lab_result_id = lr.id;

-- A future-row default does not rewrite historical timestamps.
ALTER TABLE public.lab_result_items
    ALTER COLUMN created_at SET DEFAULT now(),
ALTER COLUMN created_at SET NOT NULL;

-- Migrate existing 0/1 abnormal flags into richer interpretation.
UPDATE public.lab_result_items
SET interpretation = CASE ketquabatthuong
                         WHEN '0' THEN 'NORMAL'
                         WHEN '1' THEN 'ABNORMAL'
                         ELSE NULL
    END
WHERE result_status = 'RESULTED'
  AND interpretation IS NULL;

ALTER TABLE public.lab_result_items
    ADD CONSTRAINT ck_lab_result_items_pending_has_no_value
        CHECK (result_status = 'RESULTED' OR
               (ketqua IS NULL AND numeric_value IS NULL
                   AND qualitative_code IS NULL)),
    ADD CONSTRAINT ck_lab_result_items_resulted_has_value
        CHECK (result_status <> 'RESULTED'
               OR nullif(btrim(ketqua), '') IS NOT NULL);

CREATE INDEX idx_lab_result_items_mapping
    ON public.lab_result_items (service_lab_parameter_id)
    WHERE service_lab_parameter_id IS NOT NULL;

-- Do not force the mapping FK to NOT NULL: historical free-text rows may not
-- match a catalog code until a verified conversion has been completed.
-- Do not synthesize verified_at for old SUBMITTED results: it would falsely
-- claim a verification event that the legacy schema never recorded.
