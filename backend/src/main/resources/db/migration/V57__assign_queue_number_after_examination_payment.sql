-- I1-D48: a visit that must pay its examination fee gets its queue number only when the fee is paid, so it has no
-- number while Waiting for Fee Payment, and none if it is cancelled unpaid. Every other visit keeps its number.
ALTER TABLE visits ALTER COLUMN queue_number DROP NOT NULL;

-- The shared database already holds this constraint from when this file was V56 (renamed in the merge with main),
-- so drop it first to let this migration run there as well as on a fresh database.
ALTER TABLE visits DROP CONSTRAINT IF EXISTS ck_visits_queue_number_issued;

ALTER TABLE visits ADD CONSTRAINT ck_visits_queue_number_issued CHECK (
    queue_number IS NOT NULL OR status IN ('WAITING_FEE_PAYMENT', 'CANCELLED'));
