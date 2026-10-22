-- Staff account additions from the approved schema (I1-D40).
-- practice_license_no is the source of every "performer code" export field; the application requires it
-- for DOCTOR and TECHNICIAN accounts. account_expires_at and access_schedule limit when an account may
-- sign in (CV 365 III.1.1.g); null means no limit.

ALTER TABLE staff_accounts
    ADD COLUMN practice_license_no VARCHAR(255),
    ADD COLUMN account_expires_at  TIMESTAMPTZ,
    ADD COLUMN access_schedule     JSONB,
    ADD CONSTRAINT uq_staff_accounts_practice_license_no UNIQUE (practice_license_no);
