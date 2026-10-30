TRUNCATE TABLE
    departments,
    service_groups,
    services,
    rooms,
    staff_accounts,
    patients,
    simulated_insurance_cards
RESTART IDENTITY CASCADE;

INSERT INTO departments (id, makhoa, tenkhoa, type, description, created_at, updated_at, version) VALUES
    ('0192a000-0001-7000-8000-000000000001', 'TEST-NOI', 'Khoa Nội', 'CLINICAL', 'Test fixture', now(), now(), 0),
    ('0192a000-0001-7000-8000-000000000003', 'TEST-XN', 'Khoa Xét nghiệm', 'PARACLINICAL', 'Test fixture', now(), now(), 0);

INSERT INTO service_groups (id, loaimachidinh, loaichidinh, type, created_at, updated_at, version) VALUES
    ('0192a000-0002-7000-8000-000000000002', 'BIOCHEMISTRY', 'Sinh hóa', 'LAB', now(), now(), 0),
    ('0192a000-0002-7000-8000-000000000007', 'MRI', 'Chụp cộng hưởng từ (MRI)', 'IMAGING', now(), now(), 0);

INSERT INTO services
    (id, code, tenchidinh, type, department_id, service_group_id, clinic_price, insurance_price,
     status, created_at, updated_at, version)
VALUES
    ('0192a000-0003-7000-8000-000000000001', 'KB-001', 'Khám bệnh', 'EXAMINATION', NULL, NULL,
     150000, 50600, 'ACTIVE', now(), now(), 0);

INSERT INTO rooms
    (id, department_id, service_group_id, maphongkham, phongkham, location, type, status,
     created_at, updated_at, version)
VALUES
    ('0192a000-0004-7000-8000-000000000004', '0192a000-0001-7000-8000-000000000001', NULL,
     'PK-NOI-TQ', 'Phòng khám Nội tổng quát', 'Test location', 'EXAMINATION', 'ACTIVE', now(), now(), 0),
    ('0192a000-0004-7000-8000-000000000005', '0192a000-0001-7000-8000-000000000001', NULL,
     'PK-NGOAI', 'Phòng khám Ngoại', 'Test location', 'EXAMINATION', 'ACTIVE', now(), now(), 0),
    ('0192a000-0004-7000-8000-000000000008', '0192a000-0001-7000-8000-000000000003',
     '0192a000-0002-7000-8000-000000000002', 'XN-SH', 'Phòng Xét nghiệm Sinh hóa', 'Test location',
     'PARACLINICAL', 'ACTIVE', now(), now(), 0);

INSERT INTO staff_accounts
    (id, username, password_hash, full_name, phone_number, email, role, status,
     password_change_required, failed_login_attempts, gender, created_at, updated_at, version)
VALUES
    ('0192a000-0005-7000-8000-000000000001', 'bs.tranvanan', '{bcrypt}test-fixture', 'Trần Văn An',
     '0902000001', 'bs.tranvanan@clinic.example', 'DOCTOR', 'ACTIVE', FALSE, 0, 'MALE', now(), now(), 0),
    ('0192a000-0005-7000-8000-000000000002', 'bs.lethibinh', '{bcrypt}test-fixture', 'Lê Thị Bình',
     '0902000002', 'bs.lethibinh@clinic.example', 'DOCTOR', 'ACTIVE', FALSE, 0, 'FEMALE', now(), now(), 0),
    ('0192a000-0005-7000-8000-000000000007', 'lt.nguyenthihong', '{bcrypt}test-fixture', 'Nguyễn Thị Hồng',
     '0902000007', 'lt.nguyenthihong@clinic.example', 'RECEPTIONIST', 'ACTIVE', FALSE, 0, 'FEMALE', now(), now(), 0);

INSERT INTO staff_room_assignments (id, staff_account_id, room_id, assigned_at, version) VALUES
    ('0192a000-0006-7000-8000-000000000001', '0192a000-0005-7000-8000-000000000001',
     '0192a000-0004-7000-8000-000000000004', now(), 0);

INSERT INTO patients
    (id, mabenhnhan, hoten, ngaysinh, gioitinh, cccd_so, sodienthoai, email, diachi, dantoc,
     created_by_account_id, created_at, updated_at, version)
VALUES
    ('0192a000-0007-7000-8000-000000000003', 'BN00000003', 'Lê Văn Dũng', DATE '1958-11-02', 1,
     '079058000003', '0901000003', 'patient@example.com', 'Test address', 'Kinh',
     '0192a000-0005-7000-8000-000000000007', now(), now(), 0),
    ('0192a000-0007-7000-8000-000000000004', 'BN00000004', 'Phạm Thị Hoa', DATE '1990-05-18', 2,
     '079190000004', '0901000004', NULL, 'Test address', 'Kinh',
     '0192a000-0005-7000-8000-000000000007', now(), now(), 0),
    ('0192a000-0007-7000-8000-000000000008', 'BN00000008', 'Nguyễn Thị Thảo', DATE '1995-10-10', 2,
     '079195000012', '0901000012', NULL, 'Test address', 'Kinh',
     '0192a000-0005-7000-8000-000000000007', now(), now(), 0);

INSERT INTO simulated_insurance_cards
    (card_number, full_name, date_of_birth, benefit_code, coverage_rate, valid_from, valid_to,
     primary_care_facility, status)
VALUES
    ('HT3790000000003', 'Lê Văn Dũng', DATE '1958-11-02', 3, 95, DATE '2026-01-01', DATE '2026-12-31',
     'Test facility', 'VALID'),
    ('DN4790000000004', 'Phạm Thị Hoa', DATE '1990-05-18', 4, 80, DATE '2026-01-01', DATE '2026-12-31',
     'Test facility', 'VALID');
