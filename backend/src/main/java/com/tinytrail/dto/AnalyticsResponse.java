package com.tinytrail.dto;

import java.time.LocalDate;
import java.util.List;

public record AnalyticsResponse(String shortCode, long totalClicks, List<DayCount> clicksPerDay,
                                List<ReferrerCount> topReferrers) {
    public record DayCount(LocalDate date, long clicks) {}

    public record ReferrerCount(String referrer, long clicks) {}
}
