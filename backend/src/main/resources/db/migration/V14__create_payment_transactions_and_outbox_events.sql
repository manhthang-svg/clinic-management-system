-- Persistence required by the electronic-payment entities introduced after V13.

CREATE TABLE payment_transactions (
    id                     UUID          NOT NULL,
    payment_id             UUID          NOT NULL,
    provider_order_code    BIGINT        NOT NULL,
    amount                 NUMERIC(15,0) NOT NULL,
    status                 VARCHAR(20)   NOT NULL,
    checkout_url           VARCHAR(2048),
    provider_reference     VARCHAR(255),
    paid_at                TIMESTAMPTZ,
    last_error             VARCHAR(1000),
    created_at             TIMESTAMPTZ   NOT NULL,
    updated_at             TIMESTAMPTZ   NOT NULL,
    version                BIGINT        NOT NULL,
    CONSTRAINT pk_payment_transactions PRIMARY KEY (id),
    CONSTRAINT uq_payment_transactions_order_code UNIQUE (provider_order_code),
    CONSTRAINT fk_payment_transactions_payment
        FOREIGN KEY (payment_id) REFERENCES payments (id) ON DELETE RESTRICT,
    CONSTRAINT ck_payment_transactions_amount CHECK (amount > 0),
    CONSTRAINT ck_payment_transactions_status
        CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'CANCELLED', 'EXPIRED'))
);

CREATE INDEX idx_payment_transactions_payment_id ON payment_transactions (payment_id);

CREATE TABLE outbox_events (
    id             UUID          NOT NULL,
    event_type     VARCHAR(100)  NOT NULL,
    aggregate_type VARCHAR(100)  NOT NULL,
    aggregate_id   UUID          NOT NULL,
    payload        TEXT          NOT NULL,
    status         VARCHAR(20)   NOT NULL,
    attempts       INTEGER       NOT NULL,
    published_at   TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ,
    last_error     VARCHAR(2000),
    created_at     TIMESTAMPTZ   NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL,
    version        BIGINT        NOT NULL,
    CONSTRAINT pk_outbox_events PRIMARY KEY (id),
    CONSTRAINT ck_outbox_events_status CHECK (status IN ('PENDING', 'PUBLISHED')),
    CONSTRAINT ck_outbox_events_attempts CHECK (attempts >= 0)
);

CREATE INDEX idx_outbox_events_status_created ON outbox_events (status, created_at);
