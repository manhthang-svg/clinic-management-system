-- I1-D48: a visit that must pay its examination fee gets its queue number only when the fee is paid, so it has no
-- number while Waiting for Fee Payment, and none if it is cancelled unpaid. Every other visit keeps its number.
ALTER TABLE visits ALTER COLUMN queue_number DROP NOT NULL;

ALTER TABLE visits ADD CONSTRAINT ck_visits_queue_number_issued CHECK (
    queue_number IS NOT NULL OR status IN ('WAITING_FEE_PAYMENT', 'CANCELLED'));
