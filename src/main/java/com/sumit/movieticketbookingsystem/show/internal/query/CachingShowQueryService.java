package com.sumit.movieticketbookingsystem.show.internal.query;

import com.sumit.movieticketbookingsystem.shared.BookingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Decorator that keeps each unfiltered browse day in Redis. Everything else goes straight to Postgres.
 * Redis is only a speed-up: if it's down, reads fall back to the database and nothing fails.
 */
@Primary
@Service
class CachingShowQueryService implements ShowQueryService {

    private static final Logger log = LoggerFactory.getLogger(CachingShowQueryService.class);
    private static final TypeReference<List<ShowRow>> ROWS = new TypeReference<>() {
    };

    private final ShowSearchRepository database;
    private final StringRedisTemplate redis;
    private final JsonMapper json;
    private final Duration ttl;

    CachingShowQueryService(ShowSearchRepository database, StringRedisTemplate redis, JsonMapper json,
            BookingProperties properties) {
        this.database = database;
        this.redis = redis;
        this.json = json;
        this.ttl = properties.cache().showDayTtl();
    }

    @Override
    public List<Long> moviesShowing(long cityId, LocalDate from, LocalDate to, Instant bookableAfter) {
        return database.moviesShowing(cityId, from, to, bookableAfter);
    }

    @Override
    public List<LocalDate> dates(long cityId, long movieId, LocalDate from, LocalDate to, Instant bookableAfter) {
        return database.dates(cityId, movieId, from, to, bookableAfter);
    }

    @Override
    public List<ShowRow> showsForDay(long cityId, long movieId, LocalDate date) {
        String key = key(cityId, movieId, date);
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                return json.readValue(cached, ROWS);
            }
            List<ShowRow> rows = database.showsForDay(cityId, movieId, date);
            redis.opsForValue().set(key, json.writeValueAsString(rows), ttl);
            return rows;
        } catch (DataAccessException e) {
            log.warn("Show cache unavailable, reading {} from the database: {}", key, e.getMessage());
            return database.showsForDay(cityId, movieId, date);
        }
    }

    // After commit only, so a rolled-back change leaves the cache alone. A reader that loaded the old rows just
    // before the commit can still put them back; the TTL bounds how long that lasts.
    @TransactionalEventListener
    void evict(ShowListingChanged change) {
        String key = key(change.cityId(), change.movieId(), change.showDate());
        try {
            redis.delete(key);
        } catch (DataAccessException e) {
            log.warn("Couldn't evict {}; it expires within {}: {}", key, ttl, e.getMessage());
        }
    }

    private static String key(long cityId, long movieId, LocalDate date) {
        return "show-day:" + cityId + ":" + movieId + ":" + date;
    }
}
