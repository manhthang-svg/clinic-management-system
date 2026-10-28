-- PayOS metadata stored on the payment receipt.
-- Some shared development databases received these columns before the migration
-- was versioned. IF NOT EXISTS lets Flyway reconcile those databases while still
-- creating the complete schema from scratch.
ALTER TABLE public.payments
    ADD COLUMN IF NOT EXISTS payos_order_code BIGINT,
    ADD COLUMN IF NOT EXISTS payos_checkout_url VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS payos_qr_code VARCHAR(2000),
    ADD COLUMN IF NOT EXISTS payos_payment_link_id VARCHAR(100);

DO $$
BEGIN
    -- Existing shared databases use a semantically equivalent partial unique
    -- index with this name rather than a table constraint.
    IF to_regclass('public.uq_payments_payos_order_code') IS NULL THEN
        ALTER TABLE public.payments
            ADD CONSTRAINT uq_payments_payos_order_code UNIQUE (payos_order_code);
    END IF;
END
$$;
