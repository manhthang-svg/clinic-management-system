-- V38: Align the service catalog with Report 3 UC-22 and UC-23.
-- Existing rows are never rewritten to satisfy uniqueness: the migration must fail
-- visibly when production data needs an explicit cleanup decision.

ALTER TABLE services
    ALTER COLUMN status SET DEFAULT 'INACTIVE',
    ADD CONSTRAINT ck_services_code_not_blank
        CHECK (char_length(btrim(code)) BETWEEN 1 AND 50),
    ADD CONSTRAINT ck_services_name_length
        CHECK (char_length(btrim(tenchidinh)) BETWEEN 2 AND 255),
    ADD CONSTRAINT ck_services_description_length
        CHECK (description IS NULL OR char_length(description) <= 500);

-- PostgreSQL UNIQUE constraints are case-sensitive. Business identifiers and
-- service names are compared after trimming and case-folding.
CREATE UNIQUE INDEX uq_services_code_ci
    ON services (lower(btrim(code)));

CREATE UNIQUE INDEX uq_services_name_ci
    ON services (lower(btrim(tenchidinh)));

-- UC-22 only offers active service groups. The ownership column introduced in
-- V35 remains nullable until an approved group-to-department backfill exists.
ALTER TABLE service_groups
    ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;

-- A mapping selected while creating a LAB service is usable as soon as that
-- service is activated. V26 demo mappings explicitly remain inactive.
ALTER TABLE service_lab_parameters
    ALTER COLUMN is_active SET DEFAULT TRUE;

-- Indicator codes are catalog identifiers and must not differ only by case or
-- surrounding whitespace.
CREATE UNIQUE INDEX uq_lab_parameters_code_ci
    ON lab_parameters (lower(btrim(code)));
