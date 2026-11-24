-- UC-75: when the reminder email of a follow-up appointment was sent; null until it is sent. One reminder per
-- appointment, so a retry of the reminder job never sends it twice.
ALTER TABLE follow_up_appointments ADD COLUMN reminder_sent_at TIMESTAMPTZ;

-- The reminder job looks up the scheduled appointments of one date that have no reminder yet.
CREATE INDEX idx_follow_up_appointments_reminder_due
    ON follow_up_appointments (follow_up_date)
    WHERE status = 'SCHEDULED' AND reminder_sent_at IS NULL;
