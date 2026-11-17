-- Retention category of an archived medical record (docs/uc-58-close-medical-record.md BR-58-06, decision 10A,
-- approved 17/11/2026). TT 33/2025/TT-BYT Phụ lục: inpatient and outpatient records 10 years (dòng 44);
-- psychiatric, work-accident and traffic-accident records 20 years (dòng 40). The Doctor picks the category when
-- closing the record (UC-58); it is kept so the facility knows why a record is kept longer before any disposal
-- (Luật Lưu trữ 2024 Điều 16).
ALTER TABLE record_archives
    ADD COLUMN retention_category VARCHAR(30) NOT NULL DEFAULT 'STANDARD',
    ADD CONSTRAINT ck_record_archives_retention_category
        CHECK (retention_category IN ('STANDARD', 'WORK_ACCIDENT', 'TRAFFIC_ACCIDENT', 'PSYCHIATRIC')),
    -- The 20-year categories of dòng 40 are never kept for less (TT 33/2025 Điều 1 khoản 2 điểm b).
    ADD CONSTRAINT ck_record_archives_retention_category_years
        CHECK (retention_category = 'STANDARD' OR retention_years >= 20);
