-- Development data only; not a Flyway migration. Never run it on a production database.
--
-- Seeds 10 self-paid patients whose visit today is in the Doctor's examination room with status
-- WAITING_CONCLUSION, as the system leaves a visit when its last paraclinical service is finished (S5):
-- examination fee paid, medical record open, clinical examination recorded, and one paid paraclinical order of
-- 1-2 services, all COMPLETED with a submitted result (lab values or an imaging description). No conclusion is
-- saved, so the Doctor can do UC-56 (conclusion) and then UC-59 / UC-90 (prescription).
--
-- Run:
--   IntelliJ Database console: open this file, pick the data source, Execute.
--   Docker:  docker compose cp backend/tools/seed/seed_waiting_conclusion.sql postgres:/tmp/seed.sql
--            docker compose exec postgres psql -U clinic -d clinic -v ON_ERROR_STOP=1 -f /tmp/seed.sql
-- Change v_doctor below for another Doctor. Running it again adds 10 more patients. Everything is one
-- transaction: on any error nothing is written.

DO $$
DECLARE
    v_doctor        uuid := '0192a000-0005-7000-8000-000000000001';
    v_room          uuid;
    v_room_name     text;
    v_registrar     uuid;
    v_technician    uuid;
    v_record_type   text;
    v_exam_service  record;
    v_today         date := (now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date;
    v_queue         integer;
    v_names         text[] := ARRAY[
        'Nguyễn Văn Hùng', 'Trần Thị Mai', 'Lê Hoàng Nam', 'Phạm Thị Lan', 'Hoàng Văn Đức',
        'Vũ Thị Hạnh', 'Đặng Quốc Bảo', 'Bùi Thị Thu', 'Đỗ Minh Tuấn', 'Ngô Thị Ngọc'];
    v_genders       smallint[] := ARRAY[1, 2, 1, 2, 1, 2, 1, 2, 1, 2];
    v_reasons       text[] := ARRAY[
        'Đau bụng vùng thượng vị', 'Mệt mỏi, chóng mặt', 'Ho kéo dài 2 tuần', 'Đau đầu, tăng huyết áp',
        'Đau ngực khi gắng sức', 'Tiểu buốt, tiểu rắt', 'Sốt cao 3 ngày', 'Đau khớp gối',
        'Khó thở về đêm', 'Kiểm tra đường huyết'];
    v_pairs         jsonb;
    v_pair_count    integer;
    v_pair          jsonb;
    i               integer;
    j               integer;
    v_lines         integer;
    v_patient       uuid;
    v_code          text;
    v_visit         uuid;
    v_sovaovien     text;
    v_record        uuid;
    v_payment       uuid;
    v_payment_code  text;
    v_order         uuid;
    v_sophieu       text;
    v_detail        uuid;
    v_machidinh     text;
    v_result        uuid;
    v_detail_queue  integer;
    v_total         numeric;
    v_arrived       timestamptz;
    v_done_at       timestamptz;
BEGIN
    -- The Doctor and the examination room assigned to them.
    IF NOT EXISTS (SELECT 1 FROM staff_accounts WHERE id = v_doctor AND role = 'DOCTOR' AND status = 'ACTIVE') THEN
        RAISE EXCEPTION 'No active Doctor with id %', v_doctor;
    END IF;
    SELECT r.id, r.phongkham INTO v_room, v_room_name
    FROM staff_room_assignments a
    JOIN rooms r ON r.id = a.room_id
    WHERE a.staff_account_id = v_doctor AND a.removed_at IS NULL
      AND r.type = 'EXAMINATION' AND r.status = 'ACTIVE';
    IF v_room IS NULL THEN
        RAISE EXCEPTION 'Doctor % has no active examination room assigned', v_doctor;
    END IF;

    -- Who registered the visits and who performed the services: any active Receptionist / Technician,
    -- else the Doctor.
    SELECT id INTO v_registrar FROM staff_accounts
    WHERE role = 'RECEPTIONIST' AND status = 'ACTIVE' ORDER BY created_at LIMIT 1;
    v_registrar := COALESCE(v_registrar, v_doctor);
    SELECT id INTO v_technician FROM staff_accounts
    WHERE role = 'TECHNICIAN' AND status = 'ACTIVE' ORDER BY created_at LIMIT 1;
    v_technician := COALESCE(v_technician, v_doctor);

    SELECT loaiba INTO v_record_type FROM medical_record_types
    ORDER BY is_default DESC, (loaiba = 'NGOAITRU') DESC LIMIT 1;
    IF v_record_type IS NULL THEN
        RAISE EXCEPTION 'No medical record type (V37 seeds NGOAITRU)';
    END IF;

    SELECT id, code, tenchidinh, clinic_price INTO v_exam_service FROM services
    WHERE type = 'EXAMINATION' AND status = 'ACTIVE' ORDER BY code LIMIT 1;

    -- Active paraclinical services with an active performing room of their group.
    SELECT jsonb_agg(jsonb_build_object(
               'service', s.id, 'code', s.code, 'name', s.tenchidinh, 'price', s.clinic_price, 'type', s.type,
               'room', r.id)
               ORDER BY s.code)
    INTO v_pairs
    FROM services s
    JOIN LATERAL (
        SELECT id FROM rooms
        WHERE service_group_id = s.service_group_id AND type = 'PARACLINICAL' AND status = 'ACTIVE'
        ORDER BY maphongkham LIMIT 1) r ON TRUE
    WHERE s.type IN ('LAB', 'IMAGING', 'FUNCTIONAL') AND s.status = 'ACTIVE';
    v_pair_count := COALESCE(jsonb_array_length(v_pairs), 0);
    IF v_pair_count = 0 THEN
        RAISE EXCEPTION 'No active paraclinical service has an active paraclinical room of its group';
    END IF;

    SELECT COALESCE(MAX(queue_number), 0) INTO v_queue
    FROM visits WHERE room_id = v_room AND queue_date = v_today;

    FOR i IN 1..10 LOOP
        -- Patient with a free BN + 7-digit code, as PatientServiceImpl generates it.
        LOOP
            v_code := 'BN' || (1000000 + floor(random() * 9000000))::int;
            EXIT WHEN NOT EXISTS (SELECT 1 FROM patients WHERE mabenhnhan = v_code);
        END LOOP;
        v_patient := gen_random_uuid();
        INSERT INTO patients (id, mabenhnhan, hoten, ngaysinh, gioitinh, diachi, sodienthoai,
                              created_by_account_id, created_at, updated_at, version)
        VALUES (v_patient, v_code, v_names[i],
                DATE '1950-01-01' + (floor(random() * 20000))::int, v_genders[i],
                (10 + i) || ' Nguyễn Trãi, Phường Bến Thành, TP. Hồ Chí Minh',
                '09' || lpad((floor(random() * 100000000))::int::text, 8, '0'),
                v_registrar, now(), now(), 0);

        -- Visit of today in the Doctor's room: examined, paraclinical services done, waiting for the conclusion.
        v_arrived := now() - make_interval(mins => 180 - i * 10);
        v_done_at := v_arrived + interval '90 minutes';
        v_queue := v_queue + 1;
        v_visit := gen_random_uuid();
        v_sovaovien := 'DK' || lpad(nextval('registration_code_seq')::text, 10, '0');
        INSERT INTO visits (id, sovaovien, patient_id, room_id, doctor_account_id, registered_by_account_id,
                            thoigianvaovien, denkhambenhluc, doituongbn_loai, insurance_verify_status, lydovaovien,
                            queue_date, queue_number, status, created_at, updated_at, version)
        VALUES (v_visit, v_sovaovien, v_patient, v_room, v_doctor, v_registrar,
                v_arrived, v_arrived + interval '20 minutes', 'DV', 'NOT_APPLICABLE', v_reasons[i],
                v_today, v_queue, 'WAITING_CONCLUSION', v_arrived, v_done_at, 0);

        -- Examination fee paid at registration (self-paid visit).
        IF v_exam_service.id IS NOT NULL THEN
            v_payment := gen_random_uuid();
            INSERT INTO payments (id, payment_code, visit_id, type, status, total_amount, insurance_total_amount,
                                  patient_total_amount, payment_method, paid_at, created_at, updated_at, version)
            VALUES (v_payment, 'HD' || lpad(nextval('payment_code_seq')::text, 10, '0'), v_visit,
                    'EXAMINATION_FEE', 'PAID', v_exam_service.clinic_price, 0, v_exam_service.clinic_price, 'CASH',
                    v_arrived + interval '2 minutes', v_arrived, now(), 0);
            INSERT INTO payment_items (id, payment_id, service_id, service_code_snapshot, tenchidinh, price_type,
                                       dongia, thanhtien, benhnhanphaitra, created_at)
            VALUES (gen_random_uuid(), v_payment, v_exam_service.id, v_exam_service.code,
                    v_exam_service.tenchidinh, 'CLINIC', v_exam_service.clinic_price, v_exam_service.clinic_price,
                    v_exam_service.clinic_price, v_arrived);
        END IF;

        -- Open medical record and the clinical examination (UC-47).
        v_record := gen_random_uuid();
        INSERT INTO medical_records (id, visit_id, soba, loaiba, status, opened_at, opened_by_account_id,
                                     created_at, updated_at, version)
        VALUES (v_record, v_visit, 'BA-' || v_sovaovien, v_record_type, 'OPEN', v_arrived + interval '20 minutes',
                v_doctor, now(), now(), 0);
        INSERT INTO clinical_examinations (id, medical_record_id, sophieu, examined_by_account_id, quatrinhbenhly,
                                           khambenh_toanthan, chandoansobo, created_at, updated_at, version)
        VALUES (gen_random_uuid(), v_record, 'KB-' || v_sovaovien, v_doctor,
                v_reasons[i] || ', khởi phát vài ngày nay, chưa điều trị gì.',
                'Tỉnh, tiếp xúc tốt. Da niêm hồng. Không phù, không xuất huyết dưới da.',
                v_reasons[i], v_arrived + interval '25 minutes', now(), 0);

        -- One paid paraclinical order of 1-2 services, all performed with a submitted result.
        v_lines := LEAST(v_pair_count, 1 + (i % 2));
        v_total := 0;
        FOR j IN 0..v_lines - 1 LOOP
            v_total := v_total + ((v_pairs -> ((i + j) % v_pair_count)) ->> 'price')::numeric;
        END LOOP;
        v_payment := gen_random_uuid();
        v_payment_code := 'HD' || lpad(nextval('payment_code_seq')::text, 10, '0');
        v_sophieu := 'CD' || substring(v_payment_code FROM 3);
        INSERT INTO payments (id, payment_code, visit_id, type, status, total_amount, insurance_total_amount,
                              patient_total_amount, payment_method, paid_at, created_at, updated_at, version)
        VALUES (v_payment, v_payment_code, v_visit, 'PARACLINICAL_FEE', 'PAID', v_total, 0, v_total, 'CASH',
                v_arrived + interval '35 minutes', v_arrived + interval '30 minutes', now(), 0);
        v_order := gen_random_uuid();
        INSERT INTO paraclinical_orders (id, sophieu, medical_record_id, doctor_account_id, payment_id, chandoansobo,
                                         doituongbn, noichidinh, tongcong, status, created_at, updated_at, version)
        VALUES (v_order, v_sophieu, v_record, v_doctor, v_payment, v_reasons[i], 2, v_room_name, v_total,
                'COMPLETED', v_arrived + interval '30 minutes', v_done_at, 0);

        FOR j IN 0..v_lines - 1 LOOP
            v_pair := v_pairs -> ((i + j) % v_pair_count);
            v_detail := gen_random_uuid();
            v_machidinh := v_sophieu || '-' || lpad((j + 1)::text, 2, '0');
            SELECT COALESCE(MAX(queue_number), 0) + 1 INTO v_detail_queue
            FROM paraclinical_order_details
            WHERE room_id = (v_pair ->> 'room')::uuid
              AND (checked_in_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date = v_today;
            INSERT INTO paraclinical_order_details (id, order_id, service_id, machidinh, ngaychidinh, tenchidinh,
                                                    dongia, thanhtien, benhnhanphaitra, room_id, queue_number,
                                                    status, performer_account_id, released_at, checked_in_at,
                                                    checked_in_by_account_id, accepted_at, performed_at,
                                                    created_at, updated_at, version)
            VALUES (v_detail, v_order, (v_pair ->> 'service')::uuid, v_machidinh, v_arrived + interval '30 minutes',
                    v_pair ->> 'name', (v_pair ->> 'price')::numeric, (v_pair ->> 'price')::numeric,
                    (v_pair ->> 'price')::numeric, (v_pair ->> 'room')::uuid, v_detail_queue, 'COMPLETED',
                    v_technician, v_arrived + interval '35 minutes', v_arrived + interval '40 minutes', v_technician,
                    v_arrived + interval '45 minutes', v_arrived + interval '60 minutes',
                    v_arrived + interval '30 minutes', v_done_at, 0);
            INSERT INTO payment_items (id, payment_id, service_id, order_detail_id, service_code_snapshot, tenchidinh,
                                       price_type, dongia, thanhtien, benhnhanphaitra, created_at)
            VALUES (gen_random_uuid(), v_payment, (v_pair ->> 'service')::uuid, v_detail, v_pair ->> 'code',
                    v_pair ->> 'name', 'CLINIC', (v_pair ->> 'price')::numeric, (v_pair ->> 'price')::numeric,
                    (v_pair ->> 'price')::numeric, v_arrived + interval '30 minutes');

            v_result := gen_random_uuid();
            IF v_pair ->> 'type' = 'LAB' THEN
                INSERT INTO lab_results (id, order_detail_id, performer_account_id, sophieu, chandoan, giolaymau,
                                         sample_collected_by_account_id, status, submitted_at, created_at,
                                         updated_at, version)
                VALUES (v_result, v_detail, v_technician, v_machidinh, v_reasons[i], v_arrived + interval '45 minutes',
                        v_technician, 'SUBMITTED', v_done_at, v_arrived + interval '45 minutes', v_done_at, 0);
                -- The service's active indicators, each with a value inside its active reference range when one
                -- exists (development data, not clinical values).
                INSERT INTO lab_result_items (id, lab_result_id, machiso, pkqxn_tenxetnghiem, ketqua, donvi,
                                              trisothamchieu, ketquabatthuong, ngayketqua)
                SELECT gen_random_uuid(), v_result, p.code, p.name,
                       CASE
                           WHEN p.value_type = 'QUALITATIVE' THEN COALESCE(rr.normal_values[1], p.allowed_values[1])
                           WHEN rr.reference_type = 'RANGE' THEN round((rr.lower_bound + rr.upper_bound) / 2, 2)::text
                           WHEN rr.reference_type = 'UPPER_LIMIT' THEN round(rr.upper_bound * 0.6, 2)::text
                           WHEN rr.reference_type = 'LOWER_LIMIT' THEN round(rr.lower_bound * 1.3, 2)::text
                           ELSE 'Bình thường'
                       END,
                       p.unit,
                       CASE
                           WHEN rr.reference_type = 'RANGE' THEN rr.lower_bound || ' - ' || rr.upper_bound
                           WHEN rr.reference_type = 'UPPER_LIMIT' THEN '< ' || rr.upper_bound
                           WHEN rr.reference_type = 'LOWER_LIMIT' THEN '> ' || rr.lower_bound
                           ELSE rr.reference_text
                       END,
                       '0', v_done_at
                FROM service_lab_parameters sp
                JOIN lab_parameters p ON p.id = sp.parameter_id AND p.is_active
                LEFT JOIN LATERAL (
                    SELECT * FROM lab_reference_ranges r
                    WHERE r.parameter_id = p.id AND r.is_active
                      AND r.effective_from <= v_today AND (r.effective_to IS NULL OR r.effective_to >= v_today)
                      AND (r.sex IS NULL OR r.sex = v_genders[i])
                    ORDER BY r.sex NULLS LAST LIMIT 1) rr ON TRUE
                WHERE sp.service_id = (v_pair ->> 'service')::uuid AND sp.is_active
                ORDER BY sp.display_order
                LIMIT 15;
                IF NOT FOUND THEN
                    INSERT INTO lab_result_items (id, lab_result_id, pkqxn_tenxetnghiem, ketqua, ketquabatthuong,
                                                  ngayketqua)
                    VALUES (gen_random_uuid(), v_result, v_pair ->> 'name', 'Bình thường', '0', v_done_at);
                END IF;
            ELSE
                INSERT INTO imaging_results (id, order_detail_id, performer_account_id, sophieu, chandoan, kqcls_mota,
                                             kqcls_ketluan, status, submitted_at, created_at, updated_at, version)
                VALUES (v_result, v_detail, v_technician, v_machidinh, v_reasons[i],
                        'Hình ảnh khảo sát rõ. Cấu trúc, kích thước và đậm độ trong giới hạn bình thường. '
                            || 'Không thấy tổn thương khu trú.',
                        'Chưa phát hiện bất thường trên ' || (v_pair ->> 'name') || '.',
                        'SUBMITTED', v_done_at, v_arrived + interval '50 minutes', v_done_at, 0);
            END IF;
        END LOOP;
    END LOOP;

    RAISE NOTICE 'Seeded 10 patients waiting for the conclusion in room % (%), queue date %',
        v_room_name, v_room, v_today;
END $$;
