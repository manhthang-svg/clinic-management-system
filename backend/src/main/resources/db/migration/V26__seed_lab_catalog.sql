-- Flyway 4/4: a small, NON-CLINICALLY-VALIDATED starter catalog.
-- Codes and labels below are from the sample spreadsheets/screenshots in the
-- task, not a certified implementation of LOINC or a BYT coding catalog.
-- Regulatory codes are deliberately NULL pending a verified crosswalk.
-- Reference ranges below are DEMO / INACTIVE and MUST NOT be used to
-- auto-interpret patient results until a professional verifies and activates.
-- Mapping rows are inserted INACTIVE only for service codes already present;
-- this migration does NOT create billable services or set any service ACTIVE.

INSERT INTO public.lab_parameters
(code, name, value_type, unit, unit_ucum, allowed_values)
VALUES
    ('WBC',    'Số lượng bạch cầu', 'NUMERIC', 'G/L', '10*3/uL', NULL),
    ('RBC',    'Số lượng hồng cầu', 'NUMERIC', 'T/L', '10*6/uL', NULL),
    ('HGB',    'Lượng huyết sắc tố (Hemoglobin)', 'NUMERIC', 'g/dL', 'g/dL', NULL),
    ('HCT',    'Hematocrit', 'NUMERIC', '%', '%', NULL),
    ('MCV',    'Thể tích trung bình hồng cầu', 'NUMERIC', 'fL', 'fL', NULL),
    ('MCH',    'Lượng hemoglobin trung bình trong một hồng cầu', 'NUMERIC', 'pg', 'pg', NULL),
    ('MCHC',   'Nồng độ hemoglobin trung bình trong hồng cầu', 'NUMERIC', 'g/dL', 'g/dL', NULL),
    ('RDW',    'Dải phân bố kích thước hồng cầu', 'NUMERIC', '%', '%', NULL),
    ('PLT',    'Số lượng tiểu cầu', 'NUMERIC', 'G/L', '10*3/uL', NULL),
    ('PDW',    'Dải phân bố kích thước tiểu cầu', 'NUMERIC', 'fL', 'fL', NULL),
    ('MPV',    'Thể tích trung bình tiểu cầu', 'NUMERIC', 'fL', 'fL', NULL),
    ('NEU#',   'Số lượng bạch cầu trung tính', 'NUMERIC', 'G/L', '10*3/uL', NULL),
    ('NEU%',   'Tỷ lệ bạch cầu trung tính', 'NUMERIC', '%', '%', NULL),
    ('LYM#',   'Số lượng bạch cầu Lymphocyte', 'NUMERIC', 'G/L', '10*3/uL', NULL),
    ('LYM%',   'Tỷ lệ bạch cầu Lymphocyte', 'NUMERIC', '%', '%', NULL),
    ('MONO#',  'Số lượng bạch cầu Monocyte', 'NUMERIC', 'G/L', '10*3/uL', NULL),
    ('MONO%',  'Tỷ lệ bạch cầu Monocyte', 'NUMERIC', '%', '%', NULL),
    ('EOS#',   'Số lượng bạch cầu ưa acid', 'NUMERIC', 'G/L', '10*3/uL', NULL),
    ('EOS%',   'Tỷ lệ bạch cầu ưa acid', 'NUMERIC', '%', '%', NULL),
    ('BASO#',  'Số lượng bạch cầu ưa bazơ', 'NUMERIC', 'G/L', '10*3/uL', NULL),
    ('BASO%',  'Tỷ lệ bạch cầu ưa bazơ', 'NUMERIC', '%', '%', NULL),
    ('PT',     'Thời gian prothrombin', 'NUMERIC', 's', 's', NULL),
    ('INR',    'INR', 'NUMERIC', NULL, '{INR}', NULL),
    ('APTT',   'Thời gian thromboplastin một phần hoạt hóa', 'NUMERIC', 's', 's', NULL),
    ('FIB',    'Fibrinogen (phương pháp Clauss)', 'NUMERIC', 'mg/dL', 'mg/dL', NULL),
    ('DDIMER', 'D-Dimer (FEU)', 'NUMERIC', 'ng/mL FEU', 'ng{FEU}/mL', NULL),
    ('ESR',    'Máu lắng (Westergren)', 'NUMERIC', 'mm/h', 'mm/h', NULL),
    ('RETIC%', 'Tỷ lệ hồng cầu lưới', 'NUMERIC', '%', '%', NULL),
    ('FERR',   'Ferritin', 'NUMERIC', 'pmol/L', 'pmol/L', NULL),
    ('FE',     'Sắt huyết thanh', 'NUMERIC', 'ug/dL', 'ug/dL', NULL),
    -- Examples of qualitative values (not mapped to any unverified service).
    ('HBSAG', 'HBsAg', 'QUALITATIVE', NULL, NULL,
     ARRAY['NEGATIVE', 'POSITIVE', 'INDETERMINATE']::text[]),
    ('URINE_PROTEIN', 'Protein niệu', 'QUALITATIVE', NULL, NULL,
     ARRAY['NEGATIVE', 'TRACE', 'ONE_PLUS', 'TWO_PLUS', 'THREE_PLUS']::text[])
    ON CONFLICT (code) DO NOTHING;

-- Configure mapping only where these exact service codes exist.
-- Keep the display order stable for each service.
WITH wanted(service_code, parameter_code, section_name, group_name,
            subgroup_name, display_order, is_required) AS (
    VALUES
        ('XN-HH-001','WBC',   'Công thức máu','Tổng phân tích tế bào máu',NULL::varchar,  1,TRUE),
        ('XN-HH-001','RBC',   'Công thức máu','Tổng phân tích tế bào máu',NULL::varchar,  2,TRUE),
        ('XN-HH-001','HGB',   'Công thức máu','Tổng phân tích tế bào máu',NULL::varchar,  3,TRUE),
        ('XN-HH-001','HCT',   'Công thức máu','Tổng phân tích tế bào máu',NULL::varchar,  4,TRUE),
        ('XN-HH-001','MCV',   'Công thức máu','Tổng phân tích tế bào máu',NULL::varchar,  5,TRUE),
        ('XN-HH-001','MCH',   'Công thức máu','Tổng phân tích tế bào máu',NULL::varchar,  6,TRUE),
        ('XN-HH-001','MCHC',  'Công thức máu','Tổng phân tích tế bào máu',NULL::varchar,  7,TRUE),
        ('XN-HH-001','RDW',   'Công thức máu','Tổng phân tích tế bào máu',NULL::varchar,  8,TRUE),
        ('XN-HH-001','PLT',   'Công thức máu','Tổng phân tích tế bào máu',NULL::varchar,  9,TRUE),
        ('XN-HH-001','PDW',   'Công thức máu','Tổng phân tích tế bào máu',NULL::varchar, 10,FALSE),
        ('XN-HH-001','MPV',   'Công thức máu','Tổng phân tích tế bào máu',NULL::varchar, 11,FALSE),
        ('XN-HH-001','NEU#',  'Công thức máu','Tổng phân tích tế bào máu',NULL::varchar, 12,FALSE),
        ('XN-HH-001','NEU%',  'Công thức máu','Tổng phân tích tế bào máu',NULL::varchar, 13,FALSE),
        ('XN-HH-001','LYM#',  'Công thức máu','Tổng phân tích tế bào máu',NULL::varchar, 14,FALSE),
        ('XN-HH-001','LYM%',  'Công thức máu','Tổng phân tích tế bào máu',NULL::varchar, 15,FALSE),
        ('XN-HH-001','MONO#', 'Công thức máu','Tổng phân tích tế bào máu',NULL::varchar, 16,FALSE),
        ('XN-HH-001','MONO%', 'Công thức máu','Tổng phân tích tế bào máu',NULL::varchar, 17,FALSE),
        ('XN-HH-001','EOS#',  'Công thức máu','Tổng phân tích tế bào máu',NULL::varchar, 18,FALSE),
        ('XN-HH-001','EOS%',  'Công thức máu','Tổng phân tích tế bào máu',NULL::varchar, 19,FALSE),
        ('XN-HH-001','BASO#', 'Công thức máu','Tổng phân tích tế bào máu',NULL::varchar, 20,FALSE),
        ('XN-HH-001','BASO%', 'Công thức máu','Tổng phân tích tế bào máu',NULL::varchar, 21,FALSE),
        ('XN-HH-002','PT',    'Huyết học','Đông máu',NULL::varchar, 1,TRUE),
        ('XN-HH-003','INR',   'Huyết học','Đông máu',NULL::varchar, 1,TRUE),
        ('XN-HH-004','APTT',  'Huyết học','Đông máu',NULL::varchar, 1,TRUE),
        ('XN-HH-005','FIB',   'Huyết học','Đông máu',NULL::varchar, 1,TRUE),
        ('XN-HH-006','DDIMER','Huyết học','Đông máu',NULL::varchar, 1,TRUE),
        ('XN-HH-007','ESR',   'Huyết học','Máu lắng',NULL::varchar, 1,TRUE),
        ('XN-HH-008','RETIC%','Huyết học','Hồng cầu lưới',NULL::varchar, 1,TRUE),
        ('XN-HH-009','FERR',  'Huyết học','Ferritin',NULL::varchar, 1,TRUE),
        ('XN-HH-010','FE',    'Huyết học','Sắt huyết thanh',NULL::varchar, 1,TRUE)
)
INSERT INTO public.service_lab_parameters
    (service_id, parameter_id, section_name, group_name,
     subgroup_name, display_order, is_required, is_active)
SELECT s.id, p.id, w.section_name, w.group_name,
       w.subgroup_name, w.display_order, w.is_required, FALSE
FROM wanted AS w
         JOIN public.services AS s ON s.code = w.service_code AND s.type = 'LAB'
         JOIN public.lab_parameters AS p ON p.code = w.parameter_code
    ON CONFLICT DO NOTHING;

-- These numeric reference values are taken from an example user image,
-- NOT verified clinical reference intervals. Keep all INACTIVE.
WITH examples(service_code, parameter_code, ref_type, lower_bound, upper_bound,
              lower_inclusive, upper_inclusive, reference_text) AS (
    VALUES
        ('XN-HH-001','WBC',   'RANGE', 4.0::numeric, 10.0::numeric, TRUE,TRUE,'4.0-10.0'),
        ('XN-HH-001','RBC',   'RANGE', 3.8::numeric, 5.8::numeric, TRUE,TRUE,'3.8-5.8'),
        ('XN-HH-001','HGB',   'RANGE',12.0::numeric,17.0::numeric, TRUE,TRUE,'12.0-17.0'),
        ('XN-HH-001','PLT',   'RANGE',150::numeric, 400::numeric, TRUE,TRUE,'150-400'),
        ('XN-HH-003','INR',   'RANGE',0.8::numeric, 1.2::numeric, TRUE,TRUE,'0.8-1.2'),
        ('XN-HH-005','FIB',   'RANGE',200::numeric, 400::numeric, TRUE,TRUE,'200-400'),
        ('XN-HH-006','DDIMER','UPPER_LIMIT',NULL::numeric,500::numeric,TRUE,FALSE,'<500')
)
INSERT INTO public.lab_reference_ranges
    (service_lab_parameter_id, reference_type, lower_bound, upper_bound,
     lower_inclusive, upper_inclusive, reference_text, effective_from,
     source, is_active)
SELECT sp.id, ex.ref_type, ex.lower_bound, ex.upper_bound,
       ex.lower_inclusive, ex.upper_inclusive, ex.reference_text, CURRENT_DATE,
       'DEMO_UNVERIFIED_FROM_USER_SAMPLE', FALSE
FROM examples AS ex
         JOIN public.services AS s ON s.code = ex.service_code AND s.type = 'LAB'
         JOIN public.lab_parameters AS p ON p.code = ex.parameter_code
         JOIN public.service_lab_parameters AS sp
              ON sp.service_id = s.id AND sp.parameter_id = p.id
WHERE NOT EXISTS (
    SELECT 1 FROM public.lab_reference_ranges AS r
    WHERE r.service_lab_parameter_id = sp.id
);

-- Unambiguous backfill only: older result items have machiso as a free-text
-- code. Match by both the actual ordered service and the parameter code.
WITH candidates AS (
    SELECT i.id AS item_id,
           MIN(sp.id::text)::uuid AS mapping_id
    FROM public.lab_result_items AS i
             JOIN public.lab_results AS lr ON lr.id = i.lab_result_id
             JOIN public.paraclinical_order_details AS od ON od.id = lr.order_detail_id
             JOIN public.service_lab_parameters AS sp ON sp.service_id = od.service_id
             JOIN public.lab_parameters AS p ON p.id = sp.parameter_id
    WHERE i.service_lab_parameter_id IS NULL
      AND i.machiso IS NOT NULL
      AND upper(btrim(i.machiso)) = upper(p.code)
    GROUP BY i.id
    HAVING count(DISTINCT sp.id) = 1
)
UPDATE public.lab_result_items AS i
SET service_lab_parameter_id = c.mapping_id
    FROM candidates AS c
WHERE i.id = c.item_id;

-- Parse only simple, unambiguous decimal values from matched NUMERIC items.
UPDATE public.lab_result_items AS i
SET numeric_value = btrim(i.ketqua)::numeric
FROM public.service_lab_parameters AS sp
    JOIN public.lab_parameters AS p ON p.id = sp.parameter_id
WHERE i.service_lab_parameter_id = sp.id
  AND i.result_status = 'RESULTED'
  AND p.value_type = 'NUMERIC'
  AND i.numeric_value IS NULL
  AND i.ketqua ~ '^[+-]?[0-9]+([.][0-9]+)?$';

-- Audit queries for manual review after migration:
-- SELECT code, name FROM lab_parameters ORDER BY code;
-- SELECT i.id, i.machiso FROM lab_result_items i
--   WHERE i.service_lab_parameter_id IS NULL;
-- SELECT * FROM lab_reference_ranges WHERE is_active = FALSE;
