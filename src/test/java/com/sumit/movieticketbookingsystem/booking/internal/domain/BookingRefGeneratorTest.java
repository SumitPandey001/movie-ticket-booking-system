package com.sumit.movieticketbookingsystem.booking.internal.domain;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BookingRefGeneratorTest {

    private final BookingRefGenerator generator = new BookingRefGenerator();

    @Test
    void refsAreShortAndAvoidLookalikeLetters() {
        for (int i = 0; i < 1000; i++) {
            assertThat(generator.next()).matches("BK[0-9A-HJKMNP-TV-Z]{6}");
        }
    }

    @Test
    void refsDontRepeatInPractice() {
        Set<String> refs = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            refs.add(generator.next());
        }
        assertThat(refs).hasSizeGreaterThan(9_990);   // ~1 billion combinations; a clash here is all but impossible
    }
}
