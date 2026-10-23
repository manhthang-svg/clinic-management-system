-- Repair catalog column-name drift found in environments where the approved
-- export names were later changed manually to English aliases. Fresh databases
-- already have the target names from V4, so every rename is conditional.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'departments' AND column_name = 'department_name'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'departments' AND column_name = 'tenkhoa'
    ) THEN
        ALTER TABLE departments RENAME COLUMN department_name TO tenkhoa;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'departments' AND column_name = 'department_code'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'departments' AND column_name = 'makhoa'
    ) THEN
        ALTER TABLE departments RENAME COLUMN department_code TO makhoa;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'service_groups' AND column_name = 'service_group_code'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'service_groups' AND column_name = 'loaimachidinh'
    ) THEN
        ALTER TABLE service_groups RENAME COLUMN service_group_code TO loaimachidinh;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'service_groups' AND column_name = 'service_group_name'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'service_groups' AND column_name = 'loaichidinh'
    ) THEN
        ALTER TABLE service_groups RENAME COLUMN service_group_name TO loaichidinh;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'rooms' AND column_name = 'room_code'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'rooms' AND column_name = 'maphongkham'
    ) THEN
        ALTER TABLE rooms RENAME COLUMN room_code TO maphongkham;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'rooms' AND column_name = 'room_name'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'rooms' AND column_name = 'phongkham'
    ) THEN
        ALTER TABLE rooms RENAME COLUMN room_name TO phongkham;
    END IF;
END $$;
