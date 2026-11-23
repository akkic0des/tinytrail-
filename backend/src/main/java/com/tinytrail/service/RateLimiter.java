package com.tinytrail.service;

import com.tinytrail.config.AppProperties;
import com.tinytrail.exception.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Fixed-window limiter: INCR a per-IP counter, start the window with EXPIRE on the first hit.
 * Redis (not in-memory) so the limit holds across several app instances.
 * shortcut: fixed windows allow a 2x burst at the window edge, use a sliding window/token bucket if that matters.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimiter {

    private final StringRedisTemplate redis;
    private final AppProperties props;

    public void check(String clientIp) {
        String key = "rate:create:" + clientIp;
        var limit = props.rateLimit();
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) redis.expire(key, limit.window());
            if (count != null && count > limit.maxRequests()) {
                Long ttl = redis.getExpire(key); // seconds; -1 means a crash left the key without expiry
                if (ttl == null || ttl < 0) {
                    redis.expire(key, limit.window());
                    ttl = limit.window().toSeconds();
                }
                throw new RateLimitExceededException(Math.max(ttl, 1));
            }
        } catch (DataAccessException e) {
            // Fail open: better to let people shorten links than to take the feature down with Redis.
            log.warn("Rate limiter unavailable, allowing request: {}", e.getMessage());
        }
    }
}
