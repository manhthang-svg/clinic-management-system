-- A service group belongs to the department that owns its paraclinical services.
-- The column is nullable for the first migration because existing service-group rows
-- cannot be assigned safely without an approved data-mapping/backfill.
ALTER TABLE service_groups
    ADD COLUMN department_id UUID;

ALTER TABLE service_groups
    ADD CONSTRAINT fk_service_groups_department
        FOREIGN KEY (department_id)
        REFERENCES departments (id)
        ON DELETE RESTRICT;

CREATE INDEX idx_service_groups_department_id
    ON service_groups (department_id);
