-- BR-14: priority reasons follow the legal priority groups that fit this clinic. The clinic does not take
-- emergency cases, so there is no emergency reason. ELDERLY and FRAIL_DISABLED stay valid for visits already
-- recorded with them; new registrations use the six reasons below only.
ALTER TABLE visits DROP CONSTRAINT ck_visits_priority_reason;

ALTER TABLE visits ADD CONSTRAINT ck_visits_priority_reason CHECK (
    (is_priority AND priority_reason IN (
        'CHILD_UNDER_6', 'PREGNANT', 'EXTREME_DISABILITY', 'SEVERE_DISABILITY',
        'AGED_75_PLUS', 'MERITORIOUS',
        'ELDERLY', 'FRAIL_DISABLED'))
    OR (NOT is_priority AND priority_reason IS NULL));
