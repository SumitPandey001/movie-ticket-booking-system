CREATE TABLE cancellation (
    id              UUID         PRIMARY KEY,
    booking_id      UUID         NOT NULL REFERENCES booking(id),
    reason          VARCHAR(16)  NOT NULL CHECK (reason IN ('CUSTOMER', 'SHOW_CANCELLED')),
    seat_count      INT          NOT NULL CHECK (seat_count > 0),
    refund_percent  INT          NOT NULL CHECK (refund_percent BETWEEN 0 AND 100),
    refund_paise    BIGINT       NOT NULL CHECK (refund_paise >= 0),
    created_at      TIMESTAMPTZ  NOT NULL
);
CREATE INDEX cancellation_booking_idx ON cancellation (booking_id);

-- a cancelled seat points at the cancellation that covered it; an active one never does
ALTER TABLE booking_seat
    ADD COLUMN status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'CANCELLED')),
    ADD COLUMN cancellation_id UUID REFERENCES cancellation(id),
    ADD CONSTRAINT booking_seat_cancelled_has_cancellation
        CHECK ((status = 'CANCELLED') = (cancellation_id IS NOT NULL));
