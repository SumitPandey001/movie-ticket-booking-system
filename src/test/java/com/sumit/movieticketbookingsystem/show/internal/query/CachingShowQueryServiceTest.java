package com.sumit.movieticketbookingsystem.show.internal.query;

import com.sumit.movieticketbookingsystem.shared.TestBookingProperties;
import com.sumit.movieticketbookingsystem.show.internal.query.ShowQueryService.ShowRow;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CachingShowQueryServiceTest {

    private static final LocalDate DAY = LocalDate.parse("2026-10-03");

    @Test
    void fallsBackToTheDatabaseWhenRedisIsDown() {
        ShowSearchRepository database = mock(ShowSearchRepository.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        List<ShowRow> rows = List.of(new ShowRow(1, 7, Instant.parse("2026-10-03T12:45:00Z"), "HI", "2D", 20000L, 100));
        when(database.showsForDay(1, 42, DAY)).thenReturn(rows);
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("Connection refused"));

        CachingShowQueryService cache = new CachingShowQueryService(database, redis, JsonMapper.builder().build(),
                TestBookingProperties.defaults());

        assertThat(cache.showsForDay(1, 42, DAY)).isEqualTo(rows);
    }
}
