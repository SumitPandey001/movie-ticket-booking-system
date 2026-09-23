-- Seats can now be held or booked by exactly one booking at a time.
ALTER TABLE show_seat
    ADD COLUMN booking_id      UUID,          -- the single owner of a HELD or BOOKED seat
    ADD COLUMN hold_expires_at TIMESTAMPTZ;

ALTER TABLE show_seat DROP CONSTRAINT show_seat_status_check;
ALTER TABLE show_seat ADD CONSTRAINT show_seat_status_check CHECK (
       (status IN ('AVAILABLE', 'BLOCKED') AND booking_id IS NULL     AND hold_expires_at IS NULL)
    OR (status = 'HELD'                    AND booking_id IS NOT NULL AND hold_expires_at IS NOT NULL)
    OR (status = 'BOOKED'                  AND booking_id IS NOT NULL AND hold_expires_at IS NULL)
);

-- Deferred: a hold claims its seats first and inserts the booking row afterwards, in the same transaction.
ALTER TABLE show_seat ADD CONSTRAINT show_seat_booking_fk
    FOREIGN KEY (booking_id) REFERENCES booking(id) DEFERRABLE INITIALLY DEFERRED;

CREATE INDEX show_seat_booking_idx ON show_seat (booking_id) WHERE booking_id IS NOT NULL;
