-- Iteration 1 schema: account additions, organization catalog, patients, reception, payments,
-- business audit, and the simulated insurance portal.
-- Sources: docs/iteration-1-schema.dbml, docs/iteration-1-decisions.md (I1-Dxx),
-- docs/use-cases/staff-account.md (AQ-xx).

-- Staff account additions (AQ-06, AQ-15, AQ-18). The "lock details set exactly when LOCKED" CHECK is
-- added with UC-10, because accounts can be LOCKED today without lock details.

ALTER TABLE staff_accounts
    ADD COLUMN date_of_birth         DATE,
    ADD COLUMN gender                VARCHAR(20),
    ADD COLUMN address               TEXT,
    ADD COLUMN created_by_account_id UUID,
    ADD COLUMN last_login_at         TIMESTAMPTZ,
    ADD COLUMN password_changed_at   TIMESTAMPTZ,
    ADD COLUMN lock_reason           TEXT,
    ADD COLUMN locked_at             TIMESTAMPTZ,
    ADD COLUMN locked_by_account_id  UUID,
    ADD CONSTRAINT fk_staff_accounts_created_by
        FOREIGN KEY (created_by_account_id) REFERENCES staff_accounts (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_staff_accounts_locked_by
        FOREIGN KEY (locked_by_account_id) REFERENCES staff_accounts (id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_staff_accounts_gender CHECK (gender IN ('MALE', 'FEMALE', 'OTHER')),
    ADD CONSTRAINT ck_staff_accounts_phone_number CHECK (phone_number ~ '^0[0-9]{9}$'),
    ADD CONSTRAINT ck_staff_accounts_email_length CHECK (char_length(email) <= 100),
    ADD CONSTRAINT ck_staff_accounts_full_name_length CHECK (char_length(full_name) <= 100),
    ADD CONSTRAINT ck_staff_accounts_academic_title_length CHECK (char_length(academic_title) <= 50),
    ADD CONSTRAINT ck_staff_accounts_address_length CHECK (char_length(address) <= 255),
    ADD CONSTRAINT ck_staff_accounts_lock_reason_length CHECK (char_length(lock_reason) <= 500);

-- Emails are unique across all accounts, compared case-insensitively (AQ-06).
CREATE UNIQUE INDEX uq_staff_accounts_email ON staff_accounts (lower(email));

-- UC-6 shows the 10 latest sign-in events of one account (AQ-18).
CREATE INDEX idx_security_audit_events_target_occurred
    ON security_audit_events (target_account_id, occurred_at);

-- Organization and reference catalog

CREATE TABLE departments (
    id                    UUID         NOT NULL,
    name                  VARCHAR(100) NOT NULL,
    type                  VARCHAR(20)  NOT NULL,
    description           VARCHAR(500),
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    updated_by_account_id UUID,
    version               BIGINT       NOT NULL,
    CONSTRAINT pk_departments PRIMARY KEY (id),
    CONSTRAINT fk_departments_updated_by
        FOREIGN KEY (updated_by_account_id) REFERENCES staff_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT ck_departments_type CHECK (type IN ('CLINICAL', 'PARACLINICAL'))
);

-- Department names are unique, compared case-insensitively (I1-D04).
CREATE UNIQUE INDEX uq_departments_name ON departments (lower(name));
CREATE INDEX idx_departments_type ON departments (type);

CREATE TABLE service_groups (
    id         UUID         NOT NULL,
    code       VARCHAR(30)  NOT NULL,
    name       VARCHAR(100) NOT NULL,
    type       VARCHAR(20)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    version    BIGINT       NOT NULL,
    CONSTRAINT pk_service_groups PRIMARY KEY (id),
    CONSTRAINT uq_service_groups_code UNIQUE (code),
    CONSTRAINT ck_service_groups_type CHECK (type IN ('LAB', 'IMAGING', 'FUNCTIONAL'))
);

CREATE TABLE services (
    id               UUID          NOT NULL,
    code             VARCHAR(20)   NOT NULL,
    name             VARCHAR(200)  NOT NULL,
    type             VARCHAR(20)   NOT NULL,
    department_id    UUID,
    service_group_id UUID,
    clinic_price     NUMERIC(15,0) NOT NULL,
    insurance_price  NUMERIC(15,0),
    description      TEXT,
    status           VARCHAR(20)   NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL,
    version          BIGINT        NOT NULL,
    CONSTRAINT pk_services PRIMARY KEY (id),
    CONSTRAINT uq_services_code UNIQUE (code),
    CONSTRAINT fk_services_department
        FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_services_service_group
        FOREIGN KEY (service_group_id) REFERENCES service_groups (id) ON DELETE RESTRICT,
    CONSTRAINT ck_services_type CHECK (type IN ('EXAMINATION', 'LAB', 'IMAGING', 'FUNCTIONAL')),
    CONSTRAINT ck_services_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    -- The shared examination service has no department or group; every other service has both (I1-D06).
    CONSTRAINT ck_services_owner CHECK (
        (type = 'EXAMINATION' AND department_id IS NULL AND service_group_id IS NULL)
        OR (type <> 'EXAMINATION' AND department_id IS NOT NULL AND service_group_id IS NOT NULL)),
    -- Whole VND; the insurance price is null when the service is not covered (I1-D22).
    CONSTRAINT ck_services_clinic_price CHECK (clinic_price > 0 AND clinic_price <= 100000000),
    CONSTRAINT ck_services_insurance_price CHECK (insurance_price >= 0 AND insurance_price <= 100000000)
);

-- At most one active shared examination service (I1-D06).
CREATE UNIQUE INDEX uq_services_active_examination
    ON services (type) WHERE type = 'EXAMINATION' AND status = 'ACTIVE';
CREATE INDEX idx_services_department_status ON services (department_id, status);
CREATE INDEX idx_services_group_status ON services (service_group_id, status);

CREATE TABLE rooms (
    id               UUID         NOT NULL,
    department_id    UUID         NOT NULL,
    service_group_id UUID,
    code             VARCHAR(20)  NOT NULL,
    name             VARCHAR(100) NOT NULL,
    location         VARCHAR(255) NOT NULL,
    type             VARCHAR(20)  NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    version          BIGINT       NOT NULL,
    CONSTRAINT pk_rooms PRIMARY KEY (id),
    -- Unique including inactive rooms (BR-06, I1-D05).
    CONSTRAINT uq_rooms_code UNIQUE (code),
    CONSTRAINT fk_rooms_department
        FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_rooms_service_group
        FOREIGN KEY (service_group_id) REFERENCES service_groups (id) ON DELETE RESTRICT,
    CONSTRAINT ck_rooms_code CHECK (code ~ '^[A-Za-z0-9-]+$'),
    CONSTRAINT ck_rooms_type CHECK (type IN ('EXAMINATION', 'PARACLINICAL')),
    CONSTRAINT ck_rooms_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    -- A paraclinical room declares the service group it performs (BR-06).
    CONSTRAINT ck_rooms_service_group CHECK ((type = 'PARACLINICAL') = (service_group_id IS NOT NULL))
);

CREATE INDEX idx_rooms_department_status ON rooms (department_id, status);
CREATE INDEX idx_rooms_service_group_id ON rooms (service_group_id);

CREATE TABLE staff_room_assignments (
    id                     UUID        NOT NULL,
    staff_account_id       UUID        NOT NULL,
    room_id                UUID        NOT NULL,
    assigned_at            TIMESTAMPTZ NOT NULL,
    -- Null for rows loaded by a data migration, which has no acting Admin.
    assigned_by_account_id UUID,
    removed_at             TIMESTAMPTZ,
    removed_by_account_id  UUID,
    version                BIGINT      NOT NULL,
    CONSTRAINT pk_staff_room_assignments PRIMARY KEY (id),
    CONSTRAINT fk_staff_room_assignments_staff_account
        FOREIGN KEY (staff_account_id) REFERENCES staff_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_staff_room_assignments_room
        FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_staff_room_assignments_assigned_by
        FOREIGN KEY (assigned_by_account_id) REFERENCES staff_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_staff_room_assignments_removed_by
        FOREIGN KEY (removed_by_account_id) REFERENCES staff_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT ck_staff_room_assignments_removal
        CHECK ((removed_at IS NULL) = (removed_by_account_id IS NULL)),
    CONSTRAINT ck_staff_room_assignments_period CHECK (removed_at >= assigned_at)
);

-- A Doctor or Technician has at most one active room (I1-D09, AQ-12).
CREATE UNIQUE INDEX uq_staff_room_assignments_active_account
    ON staff_room_assignments (staff_account_id) WHERE removed_at IS NULL;
CREATE INDEX idx_staff_room_assignments_room_id ON staff_room_assignments (room_id);

-- Patient profiles and reception

-- Business codes: prefix + zero-padded sequence value (I1-D26a).
CREATE SEQUENCE patient_code_seq;
CREATE SEQUENCE registration_code_seq;
CREATE SEQUENCE invoice_number_seq;

CREATE TABLE patients (
    id                    UUID         NOT NULL,
    patient_code          VARCHAR(20)  NOT NULL,
    full_name             VARCHAR(100) NOT NULL,
    date_of_birth         DATE         NOT NULL,
    gender                VARCHAR(20)  NOT NULL,
    citizen_id            VARCHAR(12),
    phone_number          VARCHAR(10),
    address               TEXT         NOT NULL,
    occupation            TEXT,
    ethnicity             TEXT         NOT NULL,
    nationality           TEXT         NOT NULL,
    guardian_name         VARCHAR(100),
    guardian_phone_number VARCHAR(10),
    guardian_relationship TEXT,
    created_by_account_id UUID         NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    version               BIGINT       NOT NULL,
    CONSTRAINT pk_patients PRIMARY KEY (id),
    CONSTRAINT uq_patients_patient_code UNIQUE (patient_code),
    -- Unique when present (BR-12, I1-D14).
    CONSTRAINT uq_patients_citizen_id UNIQUE (citizen_id),
    CONSTRAINT fk_patients_created_by
        FOREIGN KEY (created_by_account_id) REFERENCES staff_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT ck_patients_gender CHECK (gender IN ('MALE', 'FEMALE', 'OTHER')),
    CONSTRAINT ck_patients_citizen_id CHECK (citizen_id ~ '^[0-9]{12}$'),
    CONSTRAINT ck_patients_phone_number CHECK (phone_number ~ '^0[0-9]{9}$'),
    CONSTRAINT ck_patients_guardian_phone_number CHECK (guardian_phone_number ~ '^0[0-9]{9}$'),
    -- Guardian data is absent, or has at least a name and a relationship (I1-D16). Age-dependent
    -- requiredness is checked by the application because age changes over time.
    CONSTRAINT ck_patients_guardian CHECK (
        (guardian_name IS NULL AND guardian_phone_number IS NULL AND guardian_relationship IS NULL)
        OR (guardian_name IS NOT NULL AND guardian_relationship IS NOT NULL))
);

CREATE INDEX idx_patients_phone_number ON patients (phone_number);
-- Duplicate warning on full name + date of birth (I1-D19).
CREATE INDEX idx_patients_date_of_birth ON patients (date_of_birth);
CREATE INDEX idx_patients_created_by ON patients (created_by_account_id);

CREATE TABLE visits (
    id                       UUID          NOT NULL,
    registration_code        VARCHAR(20)   NOT NULL,
    patient_id               UUID          NOT NULL,
    registered_by_account_id UUID          NOT NULL,
    visit_type               VARCHAR(20)   NOT NULL,
    priority                 BOOLEAN       NOT NULL DEFAULT FALSE,
    priority_reason          VARCHAR(20),
    reason_for_visit         VARCHAR(500)  NOT NULL,
    initial_symptoms         VARCHAR(1000),
    insurance_card_number    VARCHAR(15),
    insurance_benefit_code   SMALLINT,
    insurance_expiry_date    DATE,
    insurance_coverage_rate  SMALLINT,
    insurance_verified_at    TIMESTAMPTZ,
    status                   VARCHAR(30)   NOT NULL,
    registered_at            TIMESTAMPTZ   NOT NULL,
    completed_at             TIMESTAMPTZ,
    created_at               TIMESTAMPTZ   NOT NULL,
    updated_at               TIMESTAMPTZ   NOT NULL,
    version                  BIGINT        NOT NULL,
    CONSTRAINT pk_visits PRIMARY KEY (id),
    CONSTRAINT uq_visits_registration_code UNIQUE (registration_code),
    CONSTRAINT fk_visits_patient FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE RESTRICT,
    CONSTRAINT fk_visits_registered_by
        FOREIGN KEY (registered_by_account_id) REFERENCES staff_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT ck_visits_visit_type CHECK (visit_type IN ('INSURANCE', 'SELF_PAID')),
    CONSTRAINT ck_visits_status CHECK (status IN (
        'WAITING_FEE_PAYMENT', 'WAITING', 'SKIPPED', 'IN_EXAMINATION', 'WAITING_PARACLINICAL',
        'WAITING_CONCLUSION', 'COMPLETED', 'CANCELLED', 'ABANDONED')),
    -- A priority visit records its reason (I1-D39).
    CONSTRAINT ck_visits_priority_reason CHECK (
        (priority AND priority_reason IS NOT NULL AND priority_reason IN ('ELDERLY', 'FRAIL_DISABLED'))
        OR (NOT priority AND priority_reason IS NULL)),
    -- Verified card data is stored exactly for insurance visits (I1-D18, I1-D38).
    CONSTRAINT ck_visits_insurance CHECK (
        (visit_type = 'INSURANCE'
            AND insurance_card_number IS NOT NULL AND insurance_benefit_code IS NOT NULL
            AND insurance_expiry_date IS NOT NULL AND insurance_coverage_rate IS NOT NULL
            AND insurance_verified_at IS NOT NULL)
        OR (visit_type = 'SELF_PAID'
            AND insurance_card_number IS NULL AND insurance_benefit_code IS NULL
            AND insurance_expiry_date IS NULL AND insurance_coverage_rate IS NULL
            AND insurance_verified_at IS NULL)),
    -- 15 characters; the third is the benefit code (R3 §I.3.3).
    CONSTRAINT ck_visits_insurance_card_number CHECK (
        insurance_card_number ~ '^[A-Z]{2}[1-5][0-9]{12}$'
        AND substring(insurance_card_number FROM 3 FOR 1) = insurance_benefit_code::text),
    CONSTRAINT ck_visits_insurance_benefit_code CHECK (insurance_benefit_code BETWEEN 1 AND 5),
    CONSTRAINT ck_visits_insurance_coverage_rate CHECK (insurance_coverage_rate BETWEEN 1 AND 100)
);

-- A patient has at most one visit that is not finished (BR-17).
CREATE UNIQUE INDEX uq_visits_active_patient
    ON visits (patient_id) WHERE status NOT IN ('COMPLETED', 'CANCELLED', 'ABANDONED');
CREATE INDEX idx_visits_patient_registered_at ON visits (patient_id, registered_at);
CREATE INDEX idx_visits_status_registered_at ON visits (status, registered_at);
-- UC-35 searches by a card number used in previous visits (I1-D17).
CREATE INDEX idx_visits_insurance_card_number ON visits (insurance_card_number);
CREATE INDEX idx_visits_registered_by ON visits (registered_by_account_id);

CREATE TABLE visit_room_assignments (
    id                     UUID        NOT NULL,
    visit_id               UUID        NOT NULL,
    room_id                UUID        NOT NULL,
    queue_date             DATE        NOT NULL,
    queue_number           INTEGER     NOT NULL,
    kind                   VARCHAR(20) NOT NULL,
    assigned_by_account_id UUID        NOT NULL,
    assigned_at            TIMESTAMPTZ NOT NULL,
    ended_at               TIMESTAMPTZ,
    CONSTRAINT pk_visit_room_assignments PRIMARY KEY (id),
    CONSTRAINT fk_visit_room_assignments_visit
        FOREIGN KEY (visit_id) REFERENCES visits (id) ON DELETE RESTRICT,
    CONSTRAINT fk_visit_room_assignments_room
        FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_visit_room_assignments_assigned_by
        FOREIGN KEY (assigned_by_account_id) REFERENCES staff_accounts (id) ON DELETE RESTRICT,
    -- Queue numbers are per room per day (I1-D28).
    CONSTRAINT uq_visit_room_assignments_queue UNIQUE (room_id, queue_date, queue_number),
    CONSTRAINT ck_visit_room_assignments_queue_number CHECK (queue_number >= 1),
    CONSTRAINT ck_visit_room_assignments_kind CHECK (kind IN ('REGISTRATION')),
    CONSTRAINT ck_visit_room_assignments_period CHECK (ended_at >= assigned_at)
);

-- A visit is in one room at a time.
CREATE UNIQUE INDEX uq_visit_room_assignments_active_visit
    ON visit_room_assignments (visit_id) WHERE ended_at IS NULL;

-- One row per room and clinic-local day, incremented in the registration transaction with
-- INSERT ... ON CONFLICT DO UPDATE ... RETURNING (I1-D28a). The row has no identity of its own, so it
-- uses a composite natural key.
CREATE TABLE room_queue_counters (
    room_id           UUID    NOT NULL,
    queue_date        DATE    NOT NULL,
    last_queue_number INTEGER NOT NULL,
    CONSTRAINT pk_room_queue_counters PRIMARY KEY (room_id, queue_date),
    CONSTRAINT fk_room_queue_counters_room FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE RESTRICT,
    CONSTRAINT ck_room_queue_counters_last_queue_number CHECK (last_queue_number >= 1)
);

CREATE TABLE payments (
    id                     UUID          NOT NULL,
    invoice_number         VARCHAR(20)   NOT NULL,
    visit_id               UUID          NOT NULL,
    type                   VARCHAR(20)   NOT NULL,
    status                 VARCHAR(20)   NOT NULL,
    total_amount           NUMERIC(15,0) NOT NULL,
    insurance_total_amount NUMERIC(15,0) NOT NULL,
    patient_total_amount   NUMERIC(15,0) NOT NULL,
    paid_at                TIMESTAMPTZ,
    created_at             TIMESTAMPTZ   NOT NULL,
    updated_at             TIMESTAMPTZ   NOT NULL,
    version                BIGINT        NOT NULL,
    CONSTRAINT pk_payments PRIMARY KEY (id),
    -- Assigned at creation, kept when cancelled (I1-D26).
    CONSTRAINT uq_payments_invoice_number UNIQUE (invoice_number),
    CONSTRAINT fk_payments_visit FOREIGN KEY (visit_id) REFERENCES visits (id) ON DELETE RESTRICT,
    -- One examination-fee payment per visit; revisited when paraclinical payments are added.
    CONSTRAINT uq_payments_visit_type UNIQUE (visit_id, type),
    CONSTRAINT ck_payments_type CHECK (type IN ('EXAMINATION_FEE')),
    CONSTRAINT ck_payments_status CHECK (status IN ('PENDING', 'PAID', 'CANCELLED')),
    CONSTRAINT ck_payments_amounts CHECK (
        insurance_total_amount >= 0 AND patient_total_amount >= 0
        AND total_amount = insurance_total_amount + patient_total_amount),
    CONSTRAINT ck_payments_paid_at CHECK ((status = 'PAID') = (paid_at IS NOT NULL))
);

CREATE INDEX idx_payments_status_created_at ON payments (status, created_at);

CREATE TABLE payment_items (
    id                    UUID          NOT NULL,
    payment_id            UUID          NOT NULL,
    service_id            UUID          NOT NULL,
    service_code_snapshot VARCHAR(20)   NOT NULL,
    service_name_snapshot VARCHAR(200)  NOT NULL,
    price_type            VARCHAR(20)   NOT NULL,
    applied_price         NUMERIC(15,0) NOT NULL,
    coverage_rate         SMALLINT,
    insurance_amount      NUMERIC(15,0) NOT NULL,
    patient_amount        NUMERIC(15,0) NOT NULL,
    CONSTRAINT pk_payment_items PRIMARY KEY (id),
    -- Payments are never deleted (R3 §IV.2.3), so no cascade.
    CONSTRAINT fk_payment_items_payment
        FOREIGN KEY (payment_id) REFERENCES payments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_items_service
        FOREIGN KEY (service_id) REFERENCES services (id) ON DELETE RESTRICT,
    CONSTRAINT ck_payment_items_price_type CHECK (price_type IN ('INSURANCE', 'CLINIC')),
    -- Money calculation rule 7 (docs/iteration-1-decisions.md).
    CONSTRAINT ck_payment_items_amounts CHECK (
        insurance_amount >= 0 AND patient_amount >= 0
        AND insurance_amount + patient_amount = applied_price),
    CONSTRAINT ck_payment_items_coverage CHECK (
        (price_type = 'INSURANCE' AND coverage_rate IS NOT NULL AND coverage_rate BETWEEN 1 AND 100)
        OR (price_type = 'CLINIC' AND coverage_rate IS NULL AND insurance_amount = 0))
);

CREATE INDEX idx_payment_items_payment_id ON payment_items (payment_id);
CREATE INDEX idx_payment_items_service_id ON payment_items (service_id);

-- Business audit (I1-D35): append-only, written in the same transaction as the change.

CREATE TABLE business_audit_events (
    id               UUID        NOT NULL,
    occurred_at      TIMESTAMPTZ NOT NULL,
    actor_account_id UUID        NOT NULL,
    event_type       VARCHAR(40) NOT NULL,
    entity_type      VARCHAR(30) NOT NULL,
    entity_id        UUID        NOT NULL,
    outcome          VARCHAR(10) NOT NULL,
    changed_fields   TEXT[],
    changes          JSONB,
    correlation_id   VARCHAR(64),
    CONSTRAINT pk_business_audit_events PRIMARY KEY (id),
    CONSTRAINT fk_business_audit_events_actor
        FOREIGN KEY (actor_account_id) REFERENCES staff_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT ck_business_audit_events_event_type CHECK (event_type IN (
        'PATIENT_CREATED', 'PATIENT_UPDATED', 'VISIT_REGISTERED', 'TICKET_REPRINTED',
        'INSURANCE_VERIFIED', 'PAYMENT_CREATED', 'DEPARTMENT_UPDATED', 'ROOM_CREATED', 'ROOM_UPDATED',
        'ROOM_ACTIVATED', 'ROOM_DEACTIVATED', 'STAFF_ASSIGNED_TO_ROOM', 'STAFF_REMOVED_FROM_ROOM')),
    CONSTRAINT ck_business_audit_events_entity_type CHECK (entity_type IN (
        'PATIENT', 'VISIT', 'PAYMENT', 'DEPARTMENT', 'ROOM', 'STAFF_ROOM_ASSIGNMENT')),
    CONSTRAINT ck_business_audit_events_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE')),
    -- Old and new values only for non-personal catalog data; never patient values.
    CONSTRAINT ck_business_audit_events_changes CHECK (
        changes IS NULL OR entity_type IN ('DEPARTMENT', 'ROOM'))
);

CREATE INDEX idx_business_audit_events_entity
    ON business_audit_events (entity_type, entity_id, occurred_at);

-- Simulated insurance portal (I1-D38). Read only by the simulator adapter; no foreign keys to or from
-- domain tables.

CREATE TABLE simulated_insurance_cards (
    card_number           VARCHAR(15)  NOT NULL,
    full_name             VARCHAR(100) NOT NULL,
    date_of_birth         DATE         NOT NULL,
    benefit_code          SMALLINT     NOT NULL,
    coverage_rate         SMALLINT     NOT NULL,
    valid_from            DATE         NOT NULL,
    valid_to              DATE         NOT NULL,
    primary_care_facility TEXT         NOT NULL,
    status                VARCHAR(20)  NOT NULL,
    CONSTRAINT pk_simulated_insurance_cards PRIMARY KEY (card_number),
    CONSTRAINT ck_simulated_insurance_cards_card_number CHECK (
        card_number ~ '^[A-Z]{2}[1-5][0-9]{12}$'
        AND substring(card_number FROM 3 FOR 1) = benefit_code::text),
    CONSTRAINT ck_simulated_insurance_cards_coverage_rate CHECK (coverage_rate BETWEEN 1 AND 100),
    CONSTRAINT ck_simulated_insurance_cards_validity CHECK (valid_to >= valid_from),
    CONSTRAINT ck_simulated_insurance_cards_status CHECK (status IN ('VALID', 'REVOKED'))
);
