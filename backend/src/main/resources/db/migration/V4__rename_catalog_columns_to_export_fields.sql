-- Organization and service catalog columns take the export field names and lengths of the approved
-- schema (I1-D40). Columns are renamed, not recreated, so existing rows keep their values.

-- Departments: makhoa/tenkhoa from the department code list (I.5, I.6, III.3, III.4).
-- updated_by_account_id is dropped: changes are traced by the business audit log.
-- makhoa is added without a default; this assumes the table is empty outside development databases.
ALTER TABLE departments RENAME COLUMN name TO tenkhoa;
ALTER TABLE departments
    ALTER COLUMN tenkhoa TYPE VARCHAR(255),
    ADD COLUMN makhoa    VARCHAR(50) NOT NULL,
    ADD COLUMN is_active BOOLEAN     NOT NULL DEFAULT TRUE,
    DROP COLUMN updated_by_account_id,
    ADD CONSTRAINT uq_departments_makhoa UNIQUE (makhoa);
-- Department names stay unique, compared case-insensitively (I1-D04).
ALTER INDEX uq_departments_name RENAME TO uq_departments_tenkhoa;

-- Service groups: loaimachidinh/loaichidinh (V.2.6.6, V.2.6.5).
ALTER TABLE service_groups RENAME COLUMN code TO loaimachidinh;
ALTER TABLE service_groups RENAME COLUMN name TO loaichidinh;
ALTER TABLE service_groups
    ALTER COLUMN loaimachidinh TYPE VARCHAR(255),
    ALTER COLUMN loaichidinh   TYPE VARCHAR(255);
ALTER TABLE service_groups RENAME CONSTRAINT uq_service_groups_code TO uq_service_groups_loaimachidinh;

-- Rooms: maphongkham/phongkham (II.3, II.4). The code keeps its letters-digits-hyphen format (I1-D05).
ALTER TABLE rooms RENAME COLUMN code TO maphongkham;
ALTER TABLE rooms RENAME COLUMN name TO phongkham;
ALTER TABLE rooms
    ALTER COLUMN maphongkham TYPE VARCHAR(255),
    ALTER COLUMN phongkham   TYPE VARCHAR(255);
ALTER TABLE rooms RENAME CONSTRAINT uq_rooms_code TO uq_rooms_maphongkham;
ALTER TABLE rooms RENAME CONSTRAINT ck_rooms_code TO ck_rooms_maphongkham;

-- Services: tenchidinh (V.2.6.10); the code follows the technical-service code list, up to 50 characters.
ALTER TABLE services RENAME COLUMN name TO tenchidinh;
ALTER TABLE services
    ALTER COLUMN tenchidinh TYPE VARCHAR(255),
    ALTER COLUMN code       TYPE VARCHAR(50);

-- A payment line snapshots the service code, so the snapshot must hold a full code.
ALTER TABLE payment_items ALTER COLUMN service_code_snapshot TYPE VARCHAR(50);
