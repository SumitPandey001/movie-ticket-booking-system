CREATE TABLE payment (
    id               UUID         PRIMARY KEY,
    booking_id       UUID         NOT NULL REFERENCES booking(id),
    customer_id      UUID         NOT NULL,
    reference        VARCHAR(12)  NOT NULL,              -- the booking reference, as a gateway would show it
    method           VARCHAR(12)  NOT NULL CHECK (method IN ('CARD', 'UPI', 'NET_BANKING', 'WALLET')),
    amount_paise     BIGINT       NOT NULL CHECK (amount_paise > 0),
    status           VARCHAR(10)  NOT NULL CHECK (status IN ('INITIATED', 'SUCCESS', 'FAILED')),
    provider_txn_id  VARCHAR(64),
    masked_details   VARCHAR(64),                        -- '•••• 4242', 'user@okbank', 'HDFC'; never the raw details
    failure_reason   VARCHAR(200),
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL,
    completed_at     TIMESTAMPTZ
);
CREATE UNIQUE INDEX payment_one_success ON payment (booking_id) WHERE status = 'SUCCESS';   -- never charged twice
CREATE INDEX payment_booking_idx ON payment (booking_id);
