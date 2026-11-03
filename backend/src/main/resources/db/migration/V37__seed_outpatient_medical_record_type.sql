-- UC-49 opens the outpatient medical record (NGOAITRU) when a Doctor orders paraclinical services, but no
-- migration seeded that record type, so a fresh database failed on fk_medical_records_loaiba.
-- Databases that already have the row keep it; the new row is the default only when no outpatient default exists.
-- ASSUMPTION: the legal reference of the outpatient form is QĐ 4069/2001/QĐ-BYT.
INSERT INTO medical_record_types (loaiba, ten, pham_vi, van_ban, is_default, is_active, created_at, updated_at, version)
SELECT 'NGOAITRU', 'Bệnh án ngoại trú', 'OUTPATIENT', 'QĐ 4069/2001/QĐ-BYT',
       NOT EXISTS (SELECT 1 FROM medical_record_types WHERE is_default),
       TRUE, now(), now(), 0
ON CONFLICT (loaiba) DO NOTHING;
