package com.sumit.movieticketbookingsystem.inventory.internal;

import com.sumit.movieticketbookingsystem.shared.TestBookingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SeatCounterTest {

    private static final Instant NOW = Instant.parse("2026-10-03T10:00:00Z");

    @Test
    void countsInTheDatabaseWhenRedisIsDown() {
        SeatInventoryRepository seats = mock(SeatInventoryRepository.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(seats.availableCounts(List.of(5L, 6L), NOW)).thenReturn(Map.of(5L, 40, 6L, 0));
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("Connection refused"));

        SeatCounter counter = new SeatCounter(seats, redis, Clock.fixed(NOW, ZoneOffset.UTC),
                TestBookingProperties.defaults());

        assertThat(counter.seatsLeft(List.of(5L, 6L))).containsOnly(Map.entry(5L, 40), Map.entry(6L, 0));
    }
}
