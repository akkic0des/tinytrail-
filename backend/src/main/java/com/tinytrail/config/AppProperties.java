package com.tinytrail.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed view of the "app.*" block in application.yml. */
@ConfigurationProperties("app")
public record AppProperties(String baseUrl, String corsOrigin, String ipHashSalt, Cache cache, RateLimit rateLimit) {
    public record Cache(boolean enabled, Duration ttl) {}

    public record RateLimit(int maxRequests, Duration window) {}
}
