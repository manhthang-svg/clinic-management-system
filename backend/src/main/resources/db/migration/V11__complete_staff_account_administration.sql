-- UC-04 through UC-11 account administration constraints and idempotency support.

CREATE EXTENSION IF NOT EXISTS unaccent;

ALTER TABLE staff_accounts
    ADD COLUMN creation_submission_id UUID;

ALTER TABLE security_audit_events
    ADD COLUMN ip_address VARCHAR(45);

CREATE UNIQUE INDEX uq_staff_accounts_creation_submission
    ON staff_accounts (creation_submission_id)
    WHERE creation_submission_id IS NOT NULL;

-- Phone numbers are unique across Active and Locked accounts (UC-04, UC-07, UC-08).
CREATE UNIQUE INDEX uq_staff_accounts_phone_number
    ON staff_accounts (phone_number)
    WHERE phone_number IS NOT NULL;

CREATE INDEX idx_staff_accounts_full_name_lower ON staff_accounts (lower(full_name));
CREATE INDEX idx_staff_accounts_last_login_at ON staff_accounts (last_login_at);
