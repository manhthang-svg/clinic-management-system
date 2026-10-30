ALTER TABLE patients
    ADD COLUMN email TEXT,
    ADD CONSTRAINT ck_patients_email_length
        CHECK (email IS NULL OR char_length(email) <= 100);
