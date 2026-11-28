-- The digital signature of the e-prescription is out of scope (BR-47: "không làm ký điện tử"; TT 26/2025 Điều 10
-- left out on purpose, docs/uc-59-e-prescription.md). The prescription is sent to the national prescription system
-- through the Doctor's national sign-in (QĐ 808/QĐ-BYT VI), after the facility has added the Doctor (IV).
-- staff_accounts.citizen_id (V60) stays as staff information.

-- QĐ 808 IV / V: when the facility added the Doctor to the national prescription system; NULL = not added, or
-- removed. QĐ 808 has no API to ask whether a Doctor is added, so the clinic records it.
ALTER TABLE staff_accounts ADD COLUMN lienthong_added_at TIMESTAMPTZ;

-- Digital signatures of prescriptions (test data of the simulated CA); the electronic confirmations of UC-58 stay.
DELETE FROM record_signatures WHERE signature_method = 'DIGITAL_SIGNATURE';

ALTER TABLE record_signatures
    DROP CONSTRAINT ck_record_signatures_digital_evidence,
    DROP CONSTRAINT fk_record_signatures_signing_request,
    DROP COLUMN signature_value,
    DROP COLUMN signed_payload,
    DROP COLUMN certificate_chain,
    DROP COLUMN verification_evidence,
    DROP COLUMN signing_request_id;

DROP TABLE signing_requests;
DROP TABLE staff_signing_certificates;
