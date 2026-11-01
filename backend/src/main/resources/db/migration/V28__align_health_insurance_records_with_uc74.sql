-- UC-74: use the richer BHXH simulator introduced in V18 as the single verification source.

UPDATE health_insurance_records
SET ma_the_bhyt = CASE ma_so_bhxh
    WHEN '0110145363' THEN 'CC1010110145363'
    WHEN '8306005079' THEN 'GD4278306005079'
    WHEN '0113011672' THEN 'DN4010113011672'
    ELSE ma_the_bhyt
END
WHERE ma_so_bhxh IN ('0110145363', '8306005079', '0113011672');

-- Deterministic shared samples for the partial-date matching rules in revised UC-74.
INSERT INTO health_insurance_records (
    id, ma_so_bhxh, ma_the_bhyt, so_so_cu, ho_ten, ngay_sinh, loai_ngay_sinh, gioi_tinh,
    ma_tinh_ks, ma_huyen_ks, ma_xa_ks, dia_chi, ma_ho,
    ma_cskcb_bd, ten_cskcb_bd, gt_the_tu, gt_the_den, ngay_du_5_nam,
    ti_le_huong, trang_thai, error_code
) VALUES
    ('0192c000-0001-7000-8000-000000000206', '0000000202', 'TE2790000000202', NULL,
     'Bệnh Nhân Mô Phỏng Tháng Năm', '19850600', 2, 2,
     '79', '760', '26734', 'TP. Hồ Chí Minh', NULL,
     '79014', 'Bệnh viện Đa khoa Sài Gòn', DATE '2026-01-01', DATE '2026-12-31', NULL,
     80, 1, '00'),
    ('0192c000-0001-7000-8000-000000000207', '0000000101', 'TE1790000000101', NULL,
     'Bệnh Nhân Mô Phỏng Chỉ Năm', '19700000', 1, 1,
     '79', '760', '26734', 'TP. Hồ Chí Minh', NULL,
     '79014', 'Bệnh viện Đa khoa Sài Gòn', DATE '2026-01-01', DATE '2026-12-31', NULL,
     95, 1, '00');

ALTER TABLE health_insurance_records
    ADD CONSTRAINT ck_health_insurance_records_ma_the_bhyt CHECK (
        ma_the_bhyt IS NULL
        OR ma_the_bhyt ~ '^[0-9]{10}$'
        OR ma_the_bhyt ~ '^[A-Z]{2}[0-9]{13}$'
    ),
    ADD CONSTRAINT ck_health_insurance_records_card_social_number CHECK (
        ma_the_bhyt IS NULL
        OR (length(ma_the_bhyt) = 10 AND ma_the_bhyt = ma_so_bhxh)
        OR (length(ma_the_bhyt) = 15 AND right(ma_the_bhyt, 10) = ma_so_bhxh)
    ),
    ADD CONSTRAINT ck_health_insurance_records_trang_thai CHECK (trang_thai IN (0, 1)),
    ADD CONSTRAINT ck_health_insurance_records_error_code CHECK (error_code IN ('00', '01', '02'));

CREATE UNIQUE INDEX uq_health_insurance_records_ma_the_bhyt
    ON health_insurance_records (ma_the_bhyt)
    WHERE ma_the_bhyt IS NOT NULL;

ALTER TABLE visits
    ADD COLUMN insurance_social_number VARCHAR(10),
    ADD COLUMN insurance_primary_facility_code VARCHAR(5);

ALTER TABLE visits DROP CONSTRAINT ck_visits_mabhyt;
ALTER TABLE visits ADD CONSTRAINT ck_visits_mabhyt CHECK (
    mabhyt IS NULL
    OR mabhyt ~ '^[0-9]{10}$'
    OR mabhyt ~ '^[A-Z]{2}[0-9]{13}$'
);

UPDATE visits
SET doituongbn_loai = 'DV',
    mabhyt = NULL,
    tungaybhyt = NULL,
    denngaybhyt = NULL,
    noidangkykcbbd = NULL,
    insurance_benefit_code = NULL,
    insurance_coverage_rate = NULL,
    insurance_verified_at = NULL,
    insurance_verify_status = 'NOT_APPLICABLE'
WHERE insurance_verify_status = 'UNVERIFIED';

ALTER TABLE visits DROP CONSTRAINT ck_visits_insurance_verify_status;
ALTER TABLE visits ADD CONSTRAINT ck_visits_insurance_verify_status
    CHECK (insurance_verify_status IN ('VALID', 'INVALID', 'NOT_APPLICABLE'));

ALTER TABLE visits ADD CONSTRAINT ck_visits_insurance_snapshot CHECK (
    (insurance_verify_status = 'VALID'
        AND doituongbn_loai = 'BHYT'
        AND mabhyt IS NOT NULL
        AND insurance_social_number ~ '^[0-9]{10}$'
        AND tungaybhyt IS NOT NULL
        AND denngaybhyt IS NOT NULL
        AND insurance_primary_facility_code ~ '^[0-9]{5}$'
        AND noidangkykcbbd IS NOT NULL
        AND insurance_coverage_rate BETWEEN 1 AND 100
        AND insurance_verified_at IS NOT NULL)
    OR (insurance_verify_status = 'NOT_APPLICABLE'
        AND doituongbn_loai = 'DV'
        AND insurance_social_number IS NULL
        AND insurance_primary_facility_code IS NULL
        AND insurance_coverage_rate IS NULL
        AND insurance_verified_at IS NULL)
) NOT VALID;
