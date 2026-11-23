package com.tinytrail.dto;

import java.time.Instant;

public record StatsResponse(String shortCode, String shortUrl, String originalUrl, Instant createdAt,
                            Instant expiresAt, boolean active, long clickCount) {}
