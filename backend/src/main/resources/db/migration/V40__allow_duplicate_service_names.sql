-- UC-22 requires the service code to be unique, including inactive services.
-- Service names are descriptive and are not declared unique by the specification.
DROP INDEX IF EXISTS uq_services_name_ci;
