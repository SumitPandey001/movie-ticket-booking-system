-- Spring Modulith's event publication registry (the outbox), copied from spring-modulith-events-jdbc 2.1.1
-- (schemas/v2/schema-postgresql.sql). Modulith's own schema initialisation is off; Flyway owns the schema.
CREATE TABLE event_publication (
    id                      UUID NOT NULL,
    listener_id             TEXT NOT NULL,
    event_type              TEXT NOT NULL,
    serialized_event        TEXT NOT NULL,
    publication_date        TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date         TIMESTAMP WITH TIME ZONE,
    status                  TEXT,
    completion_attempts     INT,
    last_resubmission_date  TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);
CREATE INDEX event_publication_serialized_event_hash_idx ON event_publication USING hash (serialized_event);
CREATE INDEX event_publication_by_completion_date_idx ON event_publication (completion_date);

-- Never refund more than was paid; changed only by the conditional update that requests a refund.
ALTER TABLE payment ADD COLUMN refunded_paise BIGINT NOT NULL DEFAULT 0;
ALTER TABLE payment ADD CONSTRAINT payment_refund_within_amount CHECK (refunded_paise BETWEEN 0 AND amount_paise);

CREATE TABLE refund (
    id                  UUID         PRIMARY KEY,
    payment_id          UUID         NOT NULL REFERENCES payment(id),
    booking_id          UUID         NOT NULL,
    cancellation_id     UUID         UNIQUE,              -- one refund per cancellation; NULL for LATE_PAYMENT
    amount_paise        BIGINT       NOT NULL CHECK (amount_paise > 0),
    reason              VARCHAR(16)  NOT NULL CHECK (reason IN ('CUSTOMER', 'SHOW_CANCELLED', 'LATE_PAYMENT')),
    status              VARCHAR(10)  NOT NULL CHECK (status IN ('INITIATED', 'COMPLETED', 'FAILED')),
    provider_refund_id  VARCHAR(64),
    created_at          TIMESTAMPTZ  NOT NULL,
    completed_at        TIMESTAMPTZ
);
CREATE UNIQUE INDEX refund_one_late_payment ON refund (payment_id) WHERE reason = 'LATE_PAYMENT';
CREATE INDEX refund_booking_idx ON refund (booking_id);
