-- Remembers what a keyed request answered, so a retry gets the same answer instead of doing the work twice.
CREATE TABLE idempotency_record (
    user_id          UUID         NOT NULL,
    idem_key         VARCHAR(80)  NOT NULL,
    request_hash     CHAR(64)     NOT NULL,          -- SHA-256 of method + path + body
    status           VARCHAR(12)  NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    response_status  INT,
    response_body    JSONB,
    created_at       TIMESTAMPTZ  NOT NULL,
    expires_at       TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (user_id, idem_key)
);
