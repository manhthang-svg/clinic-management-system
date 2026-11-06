-- TT 06/2026/TT-BYT appendix: the coding rules the V5 table did not hold yet (columns 26, 28, 29 and the
-- dagger/asterisk marks of column 18), the code hierarchy, and the source document. Rows are loaded by the
-- icd10-import profile, never by a migration (docs/database-conventions.md).

ALTER TABLE icd10
    -- Parent code: B18.00 -> B18.0 -> B18; null for a three-character category.
    ADD COLUMN ma_cha             VARCHAR(10),
    -- Column 26: the code has more detailed codes, so the most detailed one must be used instead.
    ADD COLUMN co_ma_chi_tiet_hon BOOLEAN      NOT NULL DEFAULT FALSE,
    -- Columns 28 and 29: the code applies only to this sex; 1 male, 2 female, as patients.gioitinh.
    ADD COLUMN gioi_tinh_ap_dung  SMALLINT,
    -- Column 18 mark: DAO_GAM (dagger, etiology) or DAU_SAO (asterisk, manifestation).
    ADD COLUMN ky_hieu            VARCHAR(10),
    -- Free-text note from the import, for example a correction of a misprint in the source.
    ADD COLUMN ghi_chu            TEXT,
    -- Document the row was loaded from, for example 'TT 06/2026/TT-BYT'.
    ADD COLUMN van_ban            VARCHAR(100),
    ADD CONSTRAINT ck_icd10_gioi_tinh_ap_dung CHECK (gioi_tinh_ap_dung IN (1, 2)),
    ADD CONSTRAINT ck_icd10_ky_hieu CHECK (ky_hieu IN ('DAO_GAM', 'DAU_SAO')),
    ADD CONSTRAINT ck_icd10_ma_cha_not_self CHECK (ma_cha <> maicd),
    -- Deferred so one import transaction can load parents and children in any order.
    ADD CONSTRAINT fk_icd10_ma_cha FOREIGN KEY (ma_cha) REFERENCES icd10 (maicd)
        ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED;
