-- PayOS metadata stored on the payment receipt.
ALTER TABLE payments
    ADD COLUMN payos_order_code BIGINT,
    ADD COLUMN payos_checkout_url VARCHAR(1000),
    ADD COLUMN payos_qr_code VARCHAR(2000),
    ADD COLUMN payos_payment_link_id VARCHAR(100);

ALTER TABLE payments
    ADD CONSTRAINT uq_payments_payos_order_code UNIQUE (payos_order_code);
