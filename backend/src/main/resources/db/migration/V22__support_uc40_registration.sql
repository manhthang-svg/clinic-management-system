-- UC-40 accepts an insurance card when the simulated portal is temporarily unavailable.
ALTER TABLE visits DROP CONSTRAINT ck_visits_insurance_verify_status;
ALTER TABLE visits ADD CONSTRAINT ck_visits_insurance_verify_status
    CHECK (insurance_verify_status IN ('VALID', 'UNVERIFIED', 'INVALID', 'NOT_APPLICABLE'));
