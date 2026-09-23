package com.sumit.movieticketbookingsystem.shared;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A seat by row and number, written as a label like {@code F7} or {@code AA12}.
 */
public record SeatRef(String rowLabel, int seatNumber) {

    private static final Pattern ROW = Pattern.compile("[A-Z]{1,4}");
    private static final Pattern LABEL = Pattern.compile("(\\D+)(\\d+)");

    public SeatRef {
        if (rowLabel == null || !ROW.matcher(rowLabel).matches()) {
            throw new IllegalArgumentException("Row label must be 1-4 upper-case letters: " + rowLabel);
        }
        if (seatNumber < 1) {
            throw new IllegalArgumentException("Seat number must be positive: " + seatNumber);
        }
    }

    public static SeatRef parse(String label) {
        Matcher matcher = LABEL.matcher(label == null ? "" : label.strip());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Not a seat label: " + label);
        }
        return new SeatRef(matcher.group(1), Integer.parseInt(matcher.group(2)));
    }

    public String label() {
        return rowLabel + seatNumber;
    }
}
