-- One row per seat per show. Label and category are copied from the layout version when the show is created,
-- so inventory never joins catalog tables. Holds and bookings (HELD, BOOKED, the owner column) come with booking.
CREATE TABLE show_seat (
    show_id         BIGINT       NOT NULL REFERENCES show(id),
    layout_seat_id  BIGINT       NOT NULL REFERENCES layout_seat(id),
    seat_label      VARCHAR(8)   NOT NULL,
    category_id     BIGINT       NOT NULL,
    status          VARCHAR(10)  NOT NULL CHECK (status IN ('AVAILABLE', 'BLOCKED')),
    version         BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (show_id, layout_seat_id)
);
