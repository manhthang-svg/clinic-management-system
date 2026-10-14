-- Staff accounts, login sessions and the security audit trail.
-- Sources: docs/use-cases/staff-account.md, docs/security.md.

CREATE TABLE staff_accounts (
    id                       UUID         NOT NULL,
    username                 VARCHAR(50)  NOT NULL,
    password_hash            TEXT         NOT NULL,
    full_name                TEXT         NOT NULL,
    academic_title           TEXT,
    phone_number             TEXT,
    email                    TEXT,
    role                     VARCHAR(20)  NOT NULL,
    status                   VARCHAR(20)  NOT NULL,
    password_change_required BOOLEAN      NOT NULL,
    failed_login_attempts    INTEGER      NOT NULL DEFAULT 0,
    login_blocked_until      TIMESTAMPTZ,
    created_at               TIMESTAMPTZ  NOT NULL,
    updated_at               TIMESTAMPTZ  NOT NULL,
    version                  BIGINT       NOT NULL,
    CONSTRAINT pk_staff_accounts PRIMARY KEY (id),
    -- Usernames are unique and compared case-insensitively; they are stored in lowercase (BR-01, AQ-05).
    CONSTRAINT uq_staff_accounts_username UNIQUE (username),
    CONSTRAINT ck_staff_accounts_username_lowercase CHECK (username = lower(username)),
    CONSTRAINT ck_staff_accounts_role
        CHECK (role IN ('ADMIN', 'RECEPTIONIST', 'DOCTOR', 'TECHNICIAN', 'CASHIER')),
    CONSTRAINT ck_staff_accounts_status CHECK (status IN ('ACTIVE', 'LOCKED')),
    CONSTRAINT ck_staff_accounts_failed_login_attempts CHECK (failed_login_attempts >= 0)
);

-- Supports the "at least one Admin exists" check used by bootstrap and the AQ-07 guards.
CREATE INDEX idx_staff_accounts_role_status ON staff_accounts (role, status);

-- One row per login: the refresh-token family.
CREATE TABLE auth_sessions (
    id                  UUID        NOT NULL,
    staff_account_id    UUID        NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    absolute_expires_at TIMESTAMPTZ NOT NULL,
    revoked_at          TIMESTAMPTZ,
    revocation_reason   VARCHAR(30),
    CONSTRAINT pk_auth_sessions PRIMARY KEY (id),
    CONSTRAINT fk_auth_sessions_staff_accounts
        FOREIGN KEY (staff_account_id) REFERENCES staff_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT ck_auth_sessions_revocation
        CHECK ((revoked_at IS NULL) = (revocation_reason IS NULL)),
    CONSTRAINT ck_auth_sessions_revocation_reason CHECK (revocation_reason IN (
        'LOGOUT', 'REUSE_DETECTED', 'ACCOUNT_LOCKED', 'ROLE_CHANGED', 'PASSWORD_RESET', 'PASSWORD_CHANGED'))
);

-- Revoking all sessions of an account.
CREATE INDEX idx_auth_sessions_staff_account_id ON auth_sessions (staff_account_id);

-- Only the SHA-256 digest of each refresh token is stored.
-- Tokens have no life outside their session, so deleting a session deletes its tokens.
CREATE TABLE refresh_tokens (
    id              UUID        NOT NULL,
    auth_session_id UUID        NOT NULL,
    token_hash      BYTEA       NOT NULL,
    issued_at       TIMESTAMPTZ NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    used_at         TIMESTAMPTZ,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT fk_refresh_tokens_auth_sessions
        FOREIGN KEY (auth_session_id) REFERENCES auth_sessions (id) ON DELETE CASCADE,
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_refresh_tokens_token_hash_length CHECK (octet_length(token_hash) = 32)
);

-- Cascade deletes from auth_sessions.
CREATE INDEX idx_refresh_tokens_auth_session_id ON refresh_tokens (auth_session_id);

-- Append-only security audit trail. Never stores passwords, tokens or medical data.
CREATE TABLE security_audit_events (
    id                UUID        NOT NULL,
    occurred_at       TIMESTAMPTZ NOT NULL,
    event_type        VARCHAR(40) NOT NULL,
    actor_account_id  UUID,
    target_account_id UUID,
    outcome           VARCHAR(10) NOT NULL,
    correlation_id    VARCHAR(64),
    CONSTRAINT pk_security_audit_events PRIMARY KEY (id),
    CONSTRAINT fk_security_audit_events_actor
        FOREIGN KEY (actor_account_id) REFERENCES staff_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_security_audit_events_target
        FOREIGN KEY (target_account_id) REFERENCES staff_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT ck_security_audit_events_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE'))
);
