-- UC-74 AF-01: allow demographic verification when CCCD has not been synchronized.
ALTER TABLE health_insurance_records
    ALTER COLUMN identity_number DROP NOT NULL;

-- Rebuild simulator rows from every patient currently in the shared database.
DELETE FROM health_insurance_records;

WITH insured_patients AS (
    SELECT
        p.*,
        (7000000000 + row_number() OVER (ORDER BY p.id))::TEXT AS generated_social_number
    FROM patients p
)
INSERT INTO health_insurance_records (
    id, ma_so_bhxh, ma_the_bhyt, so_so_cu, identity_number,
    ho_ten, ngay_sinh, loai_ngay_sinh, gioi_tinh,
    ma_tinh_ks, ma_huyen_ks, ma_xa_ks, dia_chi, ma_ho,
    ma_cskcb_bd, ten_cskcb_bd, gt_the_tu, gt_the_den, ngay_du_5_nam,
    ti_le_huong, trang_thai, error_code
)
SELECT
    id,
    generated_social_number,
    'DN479' || generated_social_number,
    NULL,
    cccd_so,
    hoten,
    to_char(ngaysinh, 'YYYYMMDD'),
    0,
    gioitinh,
    '79',
    '760',
    '26734',
    diachi,
    NULL,
    '79014',
    'Bệnh viện Đa khoa Sài Gòn',
    DATE '2026-01-01',
    DATE '2030-12-31',
    NULL,
    80,
    1,
    '00'
FROM insured_patients;
