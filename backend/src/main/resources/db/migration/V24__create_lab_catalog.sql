-- Flyway 2/4: LAB catalog, service/parameter mapping, reference rules.
-- Requires PostgreSQL 13+ (gen_random_uuid() is built in).
-- New reference rules default to inactive until professionally verified.

ALTER TABLE public.services
    ADD COLUMN technical_code varchar(50),
    ADD COLUMN technical_code_system varchar(50),
    ADD CONSTRAINT ck_services_technical_code_pair
        CHECK ((technical_code IS NULL) = (technical_code_system IS NULL));

CREATE TABLE public.lab_parameters (
                                       id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                       code varchar(50) NOT NULL UNIQUE,
                                       name varchar(255) NOT NULL,
                                       value_type varchar(20) NOT NULL,
                                       unit varchar(50),
                                       unit_ucum varchar(50),
                                       loinc_code varchar(20),
                                       ma_dung_chung_byt varchar(50),
                                       allowed_values text[],
                                       is_active boolean NOT NULL DEFAULT TRUE,
                                       created_at timestamptz NOT NULL DEFAULT now(),
                                       updated_at timestamptz NOT NULL DEFAULT now(),
                                       version bigint NOT NULL DEFAULT 0,
                                       CONSTRAINT ck_lab_parameters_value_type
                                           CHECK (value_type IN ('NUMERIC', 'QUALITATIVE', 'TEXT')),
                                       CONSTRAINT ck_lab_parameters_allowed_values
                                           CHECK (
                                               (value_type = 'QUALITATIVE'
                                                   AND allowed_values IS NOT NULL
                                                   AND cardinality(allowed_values) > 0)
                                                   OR
                                               (value_type <> 'QUALITATIVE' AND allowed_values IS NULL)
                                               )
);

CREATE TABLE public.service_lab_parameters (
                                               id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                               service_id uuid NOT NULL REFERENCES public.services(id) ON DELETE RESTRICT,
                                               parameter_id uuid NOT NULL REFERENCES public.lab_parameters(id) ON DELETE RESTRICT,
                                               section_name varchar(100),
                                               group_name varchar(100),
                                               subgroup_name varchar(100),
                                               display_order integer NOT NULL,
                                               is_required boolean NOT NULL DEFAULT TRUE,
                                               is_active boolean NOT NULL DEFAULT FALSE,
                                               created_at timestamptz NOT NULL DEFAULT now(),
                                               updated_at timestamptz NOT NULL DEFAULT now(),
                                               version bigint NOT NULL DEFAULT 0,
                                               CONSTRAINT uq_service_lab_parameters_service_parameter
                                                   UNIQUE (service_id, parameter_id),
                                               CONSTRAINT uq_service_lab_parameters_service_order
                                                   UNIQUE (service_id, display_order),
                                               CONSTRAINT ck_service_lab_parameters_display_order
                                                   CHECK (display_order > 0)
);

-- The service type must be LAB and the parameter must be active; the
-- corresponding cross-table checks belong in the service layer.
CREATE INDEX idx_service_lab_parameters_parameter
    ON public.service_lab_parameters (parameter_id);

CREATE TABLE public.lab_reference_ranges (
                                             id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                             service_lab_parameter_id uuid NOT NULL
                                                 REFERENCES public.service_lab_parameters(id) ON DELETE RESTRICT,
                                             reference_type varchar(30) NOT NULL,
                                             lower_bound numeric,
                                             upper_bound numeric,
                                             lower_inclusive boolean NOT NULL DEFAULT TRUE,
                                             upper_inclusive boolean NOT NULL DEFAULT TRUE,
                                             normal_values text[],
                                             reference_text varchar(255),
                                             sex smallint,
                                             age_min_days integer,
                                             age_max_days integer,
                                             effective_from date NOT NULL,
                                             effective_to date,
                                             source varchar(255) NOT NULL,
                                             is_active boolean NOT NULL DEFAULT FALSE,
                                             verified_by_account_id uuid
                                                 REFERENCES public.staff_accounts(id) ON DELETE RESTRICT,
                                             verified_at timestamptz,
                                             created_at timestamptz NOT NULL DEFAULT now(),
                                             updated_at timestamptz NOT NULL DEFAULT now(),
                                             CONSTRAINT ck_lab_reference_ranges_type
                                                 CHECK (reference_type IN (
                                                                           'RANGE', 'UPPER_LIMIT', 'LOWER_LIMIT', 'QUALITATIVE', 'TEXT'
                                                     )),
                                             CONSTRAINT ck_lab_reference_ranges_shape
                                                 CHECK (
                                                     (reference_type = 'RANGE'
                                                         AND lower_bound IS NOT NULL AND upper_bound IS NOT NULL
                                                         AND lower_bound < upper_bound AND normal_values IS NULL)
                                                         OR
                                                     (reference_type = 'UPPER_LIMIT'
                                                         AND lower_bound IS NULL AND upper_bound IS NOT NULL
                                                         AND normal_values IS NULL)
                                                         OR
                                                     (reference_type = 'LOWER_LIMIT'
                                                         AND lower_bound IS NOT NULL AND upper_bound IS NULL
                                                         AND normal_values IS NULL)
                                                         OR
                                                     (reference_type = 'QUALITATIVE'
                                                         AND lower_bound IS NULL AND upper_bound IS NULL
                                                         AND normal_values IS NOT NULL
                                                         AND cardinality(normal_values) > 0)
                                                         OR
                                                     (reference_type = 'TEXT'
                                                         AND lower_bound IS NULL AND upper_bound IS NULL
                                                         AND normal_values IS NULL
                                                         AND nullif(btrim(reference_text), '') IS NOT NULL)
                                                     ),
                                             CONSTRAINT ck_lab_reference_ranges_sex
                                                 CHECK (sex IS NULL OR sex IN (1, 2, 3)),
                                             CONSTRAINT ck_lab_reference_ranges_age
                                                 CHECK (
                                                     (age_min_days IS NULL OR age_min_days >= 0)
                                                         AND (age_max_days IS NULL OR age_max_days >= 0)
                                                         AND (age_min_days IS NULL OR age_max_days IS NULL
                                                         OR age_min_days <= age_max_days)
                                                     ),
                                             CONSTRAINT ck_lab_reference_ranges_period
                                                 CHECK (effective_to IS NULL OR effective_to >= effective_from),
                                             CONSTRAINT ck_lab_reference_ranges_verified_pair
                                                 CHECK ((verified_by_account_id IS NULL) = (verified_at IS NULL)),
                                             CONSTRAINT ck_lab_reference_ranges_active_verified
                                                 CHECK (NOT is_active OR verified_at IS NOT NULL)
);

CREATE INDEX idx_lab_reference_ranges_lookup
    ON public.lab_reference_ranges
        (service_lab_parameter_id, effective_from, effective_to, sex)
    WHERE is_active = TRUE;

-- For overlapping sex/age/date rules, enforce no ambiguity in the service
-- before enabling a rule; this MVP intentionally does not add exclusion
-- constraints across nullable ranges.
