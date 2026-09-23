ALTER TABLE booking ADD COLUMN reminder_sent_at TIMESTAMPTZ;

-- what the reminder job looks for: confirmed bookings not reminded yet, by show time
CREATE INDEX booking_reminder_idx ON booking (show_start_time) WHERE status = 'CONFIRMED' AND reminder_sent_at IS NULL;
