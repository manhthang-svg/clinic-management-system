-- BR-27: the cost of a line within insurance scope, min(insurance price, clinic price), snapshotted when the
-- line is created (BR-09). The applied coverage rate of an insurance visit depends on the visit's total in
-- scope, so unpaid lines are repriced from this amount when services are added or cancelled.
ALTER TABLE payment_items
    ADD COLUMN insurance_scope_amount NUMERIC(15,0) NOT NULL DEFAULT 0;

-- Existing insurance lines were charged at the insurance price, which is their amount in scope.
UPDATE payment_items
SET insurance_scope_amount = dongia
WHERE price_type = 'INSURANCE';

ALTER TABLE payment_items
    ADD CONSTRAINT ck_payment_items_insurance_scope CHECK (
        insurance_scope_amount >= 0
        AND insurance_scope_amount <= thanhtien
        AND (price_type = 'INSURANCE' OR insurance_scope_amount = 0));
