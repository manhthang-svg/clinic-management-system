-- BR-14: a priority visit must record its reason. V46 dropped the "priority_reason IS NOT NULL" guard that V2 and
-- V6 had, and "NULL IN (...)" is NULL, which a CHECK accepts, so priority visits without a reason slipped through.
ALTER TABLE visits DROP CONSTRAINT ck_visits_priority_reason;

-- NOT VALID enforces the rule for every new or updated row without failing on rows saved while the guard was
-- missing; no reason is invented for them. The constraint is validated once no such row remains.
ALTER TABLE visits ADD CONSTRAINT ck_visits_priority_reason CHECK (
    (is_priority AND priority_reason IS NOT NULL AND priority_reason IN (
        'CHILD_UNDER_6', 'PREGNANT', 'EXTREME_DISABILITY', 'SEVERE_DISABILITY',
        'AGED_75_PLUS', 'MERITORIOUS',
        'ELDERLY', 'FRAIL_DISABLED'))
    OR (NOT is_priority AND priority_reason IS NULL)) NOT VALID;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM visits WHERE is_priority AND priority_reason IS NULL) THEN
        ALTER TABLE visits VALIDATE CONSTRAINT ck_visits_priority_reason;
    ELSE
        RAISE NOTICE 'ck_visits_priority_reason left NOT VALID: priority visits without a reason exist';
    END IF;
END $$;
