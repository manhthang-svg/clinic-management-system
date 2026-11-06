-- Expiry of the PayOS payment link attached to the receipt.
ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS payos_expired_at TIMESTAMPTZ;
