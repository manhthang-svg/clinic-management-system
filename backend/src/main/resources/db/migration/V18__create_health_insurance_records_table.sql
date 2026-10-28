-- =========================================================================
-- V18: Create simulated health insurance records table (QĐ 130/QĐ-BYT & HCM LGSP)
-- Used by the simulated insurance portal adapter to verify cards at reception.
-- =========================================================================

CREATE TABLE health_insurance_records (
    id                  UUID         NOT NULL,
    
    -- Identification codes (supports both 10-digit new format and 15-character old format)
    ma_so_bhxh          VARCHAR(10)  NOT NULL,
    ma_the_bhyt         VARCHAR(15),
    so_so_cu            VARCHAR(20),
    
    -- Citizen demographic information (LGSP standard)
    ho_ten              VARCHAR(100) NOT NULL,
    ngay_sinh           VARCHAR(8)   NOT NULL, -- Format yyyyMMdd
    loai_ngay_sinh      SMALLINT     NOT NULL DEFAULT 0, -- 0: full dob, 1: year only, 2: year and month
    gioi_tinh           SMALLINT     NOT NULL, -- 1: Nam, 2: Nữ, 3: Khác
    
    -- Administrative units (General Statistics Office - TCTK standard)
    ma_tinh_ks          VARCHAR(2)   NOT NULL,
    ma_huyen_ks         VARCHAR(3)   NOT NULL,
    ma_xa_ks            VARCHAR(5)   NOT NULL,
    dia_chi             TEXT,
    ma_ho               VARCHAR(20),
    
    -- Insurance coverage & primary care facility (Ministry of Health - BYT standard)
    ma_cskcb_bd         VARCHAR(5)   NOT NULL,
    ten_cskcb_bd        VARCHAR(255) NOT NULL,
    gt_the_tu           DATE         NOT NULL,
    gt_the_den          DATE         NOT NULL,
    ngay_du_5_nam       DATE,
    ti_le_huong         SMALLINT     NOT NULL,
    
    -- Verification status and response code
    trang_thai          SMALLINT     NOT NULL DEFAULT 1, -- 1: Active, 0: Suspended/Revoked
    error_code          VARCHAR(5)   NOT NULL DEFAULT '00', -- '00': Valid, '01': Expired, '02': Revoked
    
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_health_insurance_records PRIMARY KEY (id),
    CONSTRAINT uq_health_insurance_records_ma_so_bhxh UNIQUE (ma_so_bhxh),
    CONSTRAINT ck_health_insurance_records_ma_so_bhxh CHECK (ma_so_bhxh ~ '^[0-9]{10}$'),
    CONSTRAINT ck_health_insurance_records_ngay_sinh CHECK (ngay_sinh ~ '^[0-9]{8}$'),
    CONSTRAINT ck_health_insurance_records_gioi_tinh CHECK (gioi_tinh IN (1, 2, 3)),
    CONSTRAINT ck_health_insurance_records_loai_ngay_sinh CHECK (loai_ngay_sinh IN (0, 1, 2)),
    CONSTRAINT ck_health_insurance_records_ti_le_huong CHECK (ti_le_huong BETWEEN 1 AND 100),
    CONSTRAINT ck_health_insurance_records_hieu_luc CHECK (gt_the_den >= gt_the_tu)
);

-- Indexes for fast lookup at reception (by card number, social insurance code, or demographics)
CREATE INDEX idx_hir_ma_the_bhyt ON health_insurance_records (ma_the_bhyt);
CREATE INDEX idx_hir_ma_so_bhxh ON health_insurance_records (ma_so_bhxh);
CREATE INDEX idx_hir_ho_ten_ngay_sinh ON health_insurance_records (ho_ten, ngay_sinh);

-- =========================================================================
-- Seed initial mock data for insurance verification testing
-- =========================================================================
INSERT INTO health_insurance_records (
    id, ma_so_bhxh, ma_the_bhyt, so_so_cu, ho_ten, ngay_sinh, loai_ngay_sinh, gioi_tinh,
    ma_tinh_ks, ma_huyen_ks, ma_xa_ks, dia_chi, ma_ho,
    ma_cskcb_bd, ten_cskcb_bd, gt_the_tu, gt_the_den, ngay_du_5_nam, ti_le_huong, trang_thai, error_code
) VALUES 
-- 1. Matching sample photo: Nguyễn Hoài Băng Tâm (15-char valid card, Khánh Hòa)
(
    '0192c000-0001-7000-8000-000000000104',
    '0000000104', 'HS4560000000104', NULL,
    'Nguyễn Hoài Băng Tâm', '20180714', 0, 2,
    '56', '568', '22477', 'Tỉnh Khánh Hòa', 'HGD5656804',
    '56158', 'Phòng khám ĐKKV Bãi Dài', DATE '2026-01-01', DATE '2026-12-31', DATE '2023-07-14', 80, 1, '00'
),

-- 2. New 10-digit format (QĐ 1666): Trần Bảo Nam (TP. Hồ Chí Minh)
(
    '0192c000-0001-7000-8000-000000000205',
    '7912345678', '7912345678', '7912345678',
    'Trần Bảo Nam', '20150322', 0, 1,
    '79', '760', '26734', 'Quận 1, TP. Hồ Chí Minh', 'HGD7976001',
    '79014', 'Bệnh viện Đa khoa Sài Gòn', DATE '2026-01-01', DATE '2026-12-31', DATE '2020-05-01', 80, 1, '00'
),

-- 3. 100% entitlement rate: Đặng Tiến Dũng (Cựu chiến binh - CC1, Hà Nội)
(
    '0192c000-0001-7000-8000-000000000003',
    '0110145363', 'CC10110145363', '0110145363',
    'Đặng Tiến Dũng', '19500803', 0, 1,
    '01', '001', '00001', 'Quận Hoàn Kiếm, TP. Hà Nội', 'HGD0100103',
    '01014', 'Bệnh viện Hữu Nghị', DATE '2026-01-01', DATE '2026-12-31', DATE '2019-10-15', 100, 1, '00'
),

-- 4. Expired card test case: Đàm Đình Sơn (Ninh Bình)
(
    '0192c000-0001-7000-8000-000000000004',
    '8306005079', 'GD48306005079', '8306005079',
    'Đàm Đình Sơn', '19601215', 0, 1,
    '27', '261', '09373', 'Tỉnh Ninh Bình', 'HGD2726101',
    '27015', 'TTYT Huyện Nho Quan', DATE '2025-01-01', DATE '2025-12-31', NULL, 80, 1, '01'
),

-- 5. Revoked / blocked card test case: Võ Việt Dũng (Hà Nội)
(
    '0192c000-0001-7000-8000-000000000005',
    '0113011672', 'DN40113011672', '0113011672',
    'Võ Việt Dũng', '19910929', 0, 1,
    '01', '005', '00160', 'Quận Ba Đình, TP. Hà Nội', 'HGD0100502',
    '01001', 'Bệnh viện Bạch Mai', DATE '2026-01-01', DATE '2026-12-31', NULL, 80, 0, '02'
);
