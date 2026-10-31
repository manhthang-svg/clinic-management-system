-- Flyway 1/4: correct unique indexes that currently apply to entire tables.
-- Scope: one clinic per database. No application rows are deleted.
-- IMPORTANT: PostgreSQL partial-index predicates are based on the current
-- business statuses in the supplied schema. Keep status changes in sync.

-- Allow many services of the same type (LAB, IMAGING, etc.).
-- services.code is already unique.
DROP INDEX IF EXISTS public.uq_services_active_examination;

-- One active visit per patient, but unlimited completed/cancelled history.
DROP INDEX IF EXISTS public.uq_visits_active_patient;
CREATE UNIQUE INDEX uq_visits_active_patient
    ON public.visits (patient_id)
    WHERE status IN (
        'WAITING_FEE_PAYMENT',
        'WAITING',
        'IN_EXAMINATION',
        'WAITING_PARACLINICAL',
        'WAITING_CONCLUSION'
    );

-- A visit can have multiple payments, but at most one non-cancelled
-- examination-fee payment. PARACLINICAL_FEE payments are unrestricted.
DROP INDEX IF EXISTS public.uq_payments_examination_fee_visit;
CREATE UNIQUE INDEX uq_payments_examination_fee_visit
    ON public.payments (visit_id)
    WHERE type = 'EXAMINATION_FEE' AND status <> 'CANCELLED';

-- Multiple vital-sign measurements per medical record; only one initial set.
DROP INDEX IF EXISTS public.uq_vital_signs_initial;
CREATE UNIQUE INDEX uq_vital_signs_initial
    ON public.vital_signs (medical_record_id)
    WHERE is_initial = TRUE;

-- One default medical-record type per scope, not UNIQUE on every boolean value.
DROP INDEX IF EXISTS public.uq_medical_record_types_default;
CREATE UNIQUE INDEX uq_medical_record_types_default
    ON public.medical_record_types (pham_vi)
    WHERE is_default = TRUE;

-- Preserve staff room-assignment history, with at most one active assignment
-- per staff account in this small-clinic MVP.
DROP INDEX IF EXISTS public.uq_staff_room_assignments_active_account;
CREATE UNIQUE INDEX uq_staff_room_assignments_active_account
    ON public.staff_room_assignments (staff_account_id)
    WHERE removed_at IS NULL;
