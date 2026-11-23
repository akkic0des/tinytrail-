package com.tinytrail.service;

import com.tinytrail.config.AppProperties;
import com.tinytrail.dto.*;
import com.tinytrail.dto.AnalyticsResponse.DayCount;
import com.tinytrail.dto.AnalyticsResponse.ReferrerCount;
import com.tinytrail.exception.ApiException;
import com.tinytrail.model.Link;
import com.tinytrail.repository.ClickEventRepository;
import com.tinytrail.repository.LinkRepository;
import com.tinytrail.util.Base62Encoder;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LinkService {

    private final LinkRepository links;
    private final ClickEventRepository clicks;
    private final LinkCache cache;
    private final AppProperties props;
    private final Clock clock;

    @Transactional
    public LinkResponse create(CreateLinkRequest req) {
        Instant now = clock.instant();
        Link link = new Link();
        link.setOriginalUrl(req.url());
        link.setCreatedAt(now);
        if (req.expiresInDays() != null) link.setExpiresAt(now.plus(req.expiresInDays(), ChronoUnit.DAYS));

        if (req.customAlias() != null) {
            if (links.existsByShortCode(req.customAlias())) throw aliasTaken();
            link.setShortCode(req.customAlias());
            try {
                links.save(link);
            } catch (DataIntegrityViolationException e) { // two people claimed the alias at the same moment
                throw aliasTaken();
            }
        } else {
            links.save(link); // INSERT first: the DB-generated ID is the input to Base62
            String code = Base62Encoder.encode(link.getId());
            // A custom alias may already equal this code (e.g. alias "abc" vs. ID 140k). "-" never
            // appears in Base62 output, so the suffixed code cannot clash with another generated one.
            if (links.existsByShortCode(code)) code += "-";
            link.setShortCode(code); // persisted by dirty checking at commit
        }
        return new LinkResponse(link.getShortCode(), shortUrl(link.getShortCode()), link.getOriginalUrl(),
                link.getCreatedAt(), link.getExpiresAt());
    }

    /** Cache-aside: Redis first, DB on a miss, then fill Redis. Returns the target URL. */
    public String resolve(String code) {
        var cached = cache.get(code);
        if (cached.isPresent()) return cached.get();

        Link link = find(code);
        Instant now = clock.instant();
        boolean expired = link.getExpiresAt() != null && !link.getExpiresAt().isAfter(now);
        if (!link.isActive() || expired) throw new ApiException(HttpStatus.GONE, "This link has expired or been deactivated");

        // Never cache past expiry, so a cache hit is always safe to redirect without re-checking.
        Duration ttl = props.cache().ttl();
        if (link.getExpiresAt() != null) {
            Duration remaining = Duration.between(now, link.getExpiresAt());
            if (remaining.compareTo(ttl) < 0) ttl = remaining;
        }
        cache.put(code, link.getOriginalUrl(), ttl);
        return link.getOriginalUrl();
    }

    public StatsResponse stats(String code) {
        Link l = find(code);
        return new StatsResponse(l.getShortCode(), shortUrl(code), l.getOriginalUrl(), l.getCreatedAt(),
                l.getExpiresAt(), l.isActive(), l.getClickCount());
    }

    public AnalyticsResponse analytics(String code) {
        Link link = find(code);
        LocalDate today = LocalDate.now(clock); // clock is UTC, matching how click times are stored
        LocalDate from = today.minusDays(6);

        Map<LocalDate, Long> perDay = new HashMap<>();
        for (Object[] row : clicks.countPerDay(code, from.atStartOfDay().toInstant(ZoneOffset.UTC))) {
            perDay.put(LocalDate.parse(row[0].toString()), ((Number) row[1]).longValue());
        }
        // Fill empty days with 0 so the chart always has 7 points.
        List<DayCount> days = from.datesUntil(today.plusDays(1))
                .map(d -> new DayCount(d, perDay.getOrDefault(d, 0L))).toList();
        List<ReferrerCount> referrers = clicks.topReferrers(code, PageRequest.of(0, 5)).stream()
                .map(r -> new ReferrerCount((String) r[0], ((Number) r[1]).longValue())).toList();
        return new AnalyticsResponse(code, link.getClickCount(), days, referrers);
    }

    /** Soft-delete + cache eviction. No public endpoint yet: there is no auth, so anyone could kill any link. */
    @Transactional
    public void deactivate(String code) {
        Link link = find(code);
        link.setActive(false);
        cache.evict(code); // without this, the cached URL would keep redirecting for up to 24h
    }

    private Link find(String code) {
        return links.findByShortCode(code).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Short link not found"));
    }

    private String shortUrl(String code) {
        return props.baseUrl() + "/" + code;
    }

    private static ApiException aliasTaken() {
        return new ApiException(HttpStatus.CONFLICT, "That alias is already taken");
    }
}
