-- Personal identification number (số định danh cá nhân, 12 digits) of a Doctor (docs/uc-59-e-prescription.md
-- BR-59-16): a signing certificate is registered for the Doctor only when the identity number in its subject is
-- this number (NĐ 23/2025 Điều 16 khoản 1 điểm a; TT 15/2025/TT-BKHCN Điều 5 khoản 2 điểm a). Set by the Admin
-- after the account is created, like ma_lienthong_bacsi; Doctor only, which the application checks.
ALTER TABLE staff_accounts
    ADD COLUMN citizen_id VARCHAR(12),
    ADD CONSTRAINT uq_staff_accounts_citizen_id UNIQUE (citizen_id),
    ADD CONSTRAINT ck_staff_accounts_citizen_id CHECK (citizen_id ~ '^[0-9]{12}$');
