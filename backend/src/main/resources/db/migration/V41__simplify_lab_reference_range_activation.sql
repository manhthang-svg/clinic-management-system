-- Reference ranges are configured and activated directly by Admin.
-- Keep source/effective dates for traceability, but remove the separate verification workflow.
ALTER TABLE lab_reference_ranges
    DROP CONSTRAINT IF EXISTS ck_lab_reference_ranges_active_verified,
    DROP CONSTRAINT IF EXISTS ck_lab_reference_ranges_verified_pair,
    DROP COLUMN IF EXISTS verified_by_account_id,
    DROP COLUMN IF EXISTS verified_at,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
