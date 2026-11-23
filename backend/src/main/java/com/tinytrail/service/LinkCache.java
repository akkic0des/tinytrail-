package com.tinytrail.service;

import com.tinytrail.config.AppProperties;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Cache-aside for redirects: the service reads here first and fills it on a miss. Redis problems
 *  are logged and treated as a miss, so a Redis outage slows redirects down but never breaks them. */
@Component
@RequiredArgsConstructor
@Slf4j
public class LinkCache {

    private static final String PREFIX = "link:";

    private final StringRedisTemplate redis;
    private final AppProperties props;

    public Optional<String> get(String code) {
        if (!props.cache().enabled()) return Optional.empty();
        try {
            return Optional.ofNullable(redis.opsForValue().get(PREFIX + code));
        } catch (DataAccessException e) {
            log.warn("Redis read failed, falling back to DB: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public void put(String code, String url, Duration ttl) {
        if (!props.cache().enabled()) return;
        try {
            redis.opsForValue().set(PREFIX + code, url, ttl);
        } catch (DataAccessException e) {
            log.warn("Redis write failed: {}", e.getMessage());
        }
    }

    public void evict(String code) {
        if (!props.cache().enabled()) return;
        try {
            redis.delete(PREFIX + code);
        } catch (DataAccessException e) {
            log.warn("Redis evict failed: {}", e.getMessage());
        }
    }
}
