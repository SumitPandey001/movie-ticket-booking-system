CREATE TABLE booking (
    id               UUID         PRIMARY KEY,
    booking_ref      VARCHAR(12)  NOT NULL UNIQUE,          -- e.g. BK7X9Q2M, what the customer quotes
    user_id          UUID         NOT NULL,
    show_id          BIGINT       NOT NULL REFERENCES show(id),
    show_start_time  TIMESTAMPTZ  NOT NULL,                 -- copied so refunds and reminders don't ask show
    status           VARCHAR(16)  NOT NULL CHECK (status IN
                        ('HELD', 'PAYMENT_PENDING', 'CONFIRMED', 'CANCELLED', 'RELEASED', 'EXPIRED', 'FAILED')),
    hold_expires_at  TIMESTAMPTZ  NOT NULL,
    seat_count       INT          NOT NULL CHECK (seat_count > 0),
    subtotal_paise   BIGINT       NOT NULL,                 -- tier prices (+ day-of-week adjustments later)
    discount_paise   BIGINT       NOT NULL DEFAULT 0,
    fee_paise        BIGINT       NOT NULL,
    tax_paise        BIGINT       NOT NULL,
    total_paise      BIGINT       NOT NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL,
    closed_at        TIMESTAMPTZ,                           -- set once the booking reaches a terminal status
    CHECK (total_paise = subtotal_paise - discount_paise + fee_paise + tax_paise)
);

-- A customer has at most one live hold per show; a second parallel hold fails here at commit.
CREATE UNIQUE INDEX booking_one_active_hold ON booking (user_id, show_id) WHERE status IN ('HELD', 'PAYMENT_PENDING');
CREATE INDEX booking_user_idx   ON booking (user_id, show_start_time DESC);
CREATE INDEX booking_expiry_idx ON booking (hold_expires_at) WHERE status IN ('HELD', 'PAYMENT_PENDING');

-- The frozen price of each seat at hold time.
CREATE TABLE booking_seat (
    booking_id      UUID         NOT NULL REFERENCES booking(id),
    layout_seat_id  BIGINT       NOT NULL,
    seat_label      VARCHAR(8)   NOT NULL,
    category_id     BIGINT       NOT NULL,
    base_paise      BIGINT       NOT NULL,             -- tier price (+ day-of-week adjustment later)
    discount_paise  BIGINT       NOT NULL DEFAULT 0,   -- this seat's share of the discount
    fee_paise       BIGINT       NOT NULL,             -- convenience fee + GST on it
    amount_paise    BIGINT       NOT NULL,             -- what this seat costs in total
    PRIMARY KEY (booking_id, layout_seat_id),
    CHECK (amount_paise >= fee_paise)
);
