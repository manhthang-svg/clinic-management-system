-- These access-window fields are no longer part of the approved staff account schema.
-- practice_license_no, introduced in V3, remains unchanged.
ALTER TABLE staff_accounts
    DROP COLUMN account_expires_at,
    DROP COLUMN access_schedule;
