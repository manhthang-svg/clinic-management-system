-- V39: Report 3 presents reference ranges as part of the indicator catalog while an
-- Admin is selecting indicators for a new service. Store that ownership directly
-- on lab_parameters instead of on a service-specific mapping.

ALTER TABLE lab_reference_ranges
    ADD COLUMN parameter_id UUID;

UPDATE lab_reference_ranges AS reference_range
SET parameter_id = mapping.parameter_id
FROM service_lab_parameters AS mapping
WHERE reference_range.service_lab_parameter_id = mapping.id;

ALTER TABLE lab_reference_ranges
    ALTER COLUMN parameter_id SET NOT NULL,
    ADD CONSTRAINT fk_lab_reference_ranges_parameter
        FOREIGN KEY (parameter_id)
        REFERENCES lab_parameters (id)
        ON DELETE RESTRICT;

DROP INDEX idx_lab_reference_ranges_lookup;

ALTER TABLE lab_reference_ranges
    DROP CONSTRAINT lab_reference_ranges_service_lab_parameter_id_fkey,
    DROP COLUMN service_lab_parameter_id;

CREATE INDEX idx_lab_reference_ranges_lookup
    ON lab_reference_ranges (parameter_id, effective_from, effective_to, sex)
    WHERE is_active = TRUE;
