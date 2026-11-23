package com.tinytrail.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.tinytrail.config.AppProperties;
import com.tinytrail.exception.RateLimitExceededException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RateLimiterTest {

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> ops;

    RateLimiter limiter;

    @BeforeEach
    void setUp() {
        var props = new AppProperties("http://x", "*", "salt", new AppProperties.Cache(true, Duration.ofHours(1)),
                new AppProperties.RateLimit(10, Duration.ofSeconds(60)));
        limiter = new RateLimiter(redis, props);
        when(redis.opsForValue()).thenReturn(ops);
    }

    @Test
    void firstRequestStartsTheWindow() {
        when(ops.increment("rate:create:1.2.3.4")).thenReturn(1L);
        limiter.check("1.2.3.4");
        verify(redis).expire("rate:create:1.2.3.4", Duration.ofSeconds(60));
    }

    @Test
    void requestsUpToTheLimitPassWithoutResettingExpiry() {
        when(ops.increment(anyString())).thenReturn(10L);
        limiter.check("1.2.3.4");
        verify(redis, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void requestOverTheLimitIsRejectedWithRemainingTtl() {
        when(ops.increment(anyString())).thenReturn(11L);
        when(redis.getExpire(anyString())).thenReturn(42L);
        assertThatThrownBy(() -> limiter.check("1.2.3.4"))
                .isInstanceOfSatisfying(RateLimitExceededException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(42));
    }

    @Test
    void keyWithoutExpiryGetsHealedAndFullWindowIsReported() {
        when(ops.increment(anyString())).thenReturn(11L);
        when(redis.getExpire(anyString())).thenReturn(-1L);
        assertThatThrownBy(() -> limiter.check("1.2.3.4"))
                .isInstanceOfSatisfying(RateLimitExceededException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(60));
        verify(redis).expire("rate:create:1.2.3.4", Duration.ofSeconds(60));
    }

    @Test
    void failsOpenWhenRedisIsDown() {
        when(ops.increment(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        assertThatCode(() -> limiter.check("1.2.3.4")).doesNotThrowAnyException();
    }
}
