package com.sumit.movieticketbookingsystem.booking.internal.domain;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Short references customers can read out over the phone: "BK" plus 6 Crockford base-32 characters,
 * which leave out I, L, O and U so nothing is mistaken for 1, 0 or V. About a billion combinations;
 * a rare collision is caught by the unique index and the caller retries.
 */
@Component
public class BookingRefGenerator {

    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int LENGTH = 6;

    private final SecureRandom random = new SecureRandom();

    public String next() {
        StringBuilder ref = new StringBuilder("BK");
        for (int i = 0; i < LENGTH; i++) {
            ref.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return ref.toString();
    }
}
