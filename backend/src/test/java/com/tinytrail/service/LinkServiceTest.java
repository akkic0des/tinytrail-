package com.tinytrail.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.tinytrail.config.AppProperties;
import com.tinytrail.dto.*;
import com.tinytrail.exception.ApiException;
import com.tinytrail.model.Link;
import com.tinytrail.repository.ClickEventRepository;
import com.tinytrail.repository.LinkRepository;
import java.time.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class LinkServiceTest {

    static final Instant NOW = Instant.parse("2026-01-10T12:00:00Z");

    @Mock LinkRepository links;
    @Mock ClickEventRepository clicks;
    @Mock LinkCache cache;

    LinkService service;

    @BeforeEach
    void setUp() {
        var props = new AppProperties("http://short.test", "*", "salt",
                new AppProperties.Cache(true, Duration.ofHours(24)), new AppProperties.RateLimit(10, Duration.ofMinutes(1)));
        service = new LinkService(links, clicks, cache, props, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /** Simulates the DB: assigns an auto-increment ID on save. */
    private void saveAssignsId(long id) {
        when(links.save(any(Link.class))).thenAnswer(inv -> {
            Link l = inv.getArgument(0);
            l.setId(id);
            return l;
        });
    }

    private Link link(String url, Instant expiresAt, boolean active) {
        Link l = new Link();
        l.setShortCode("abc");
        l.setOriginalUrl(url);
        l.setCreatedAt(NOW);
        l.setExpiresAt(expiresAt);
        l.setActive(active);
        return l;
    }

    // ---- create ----

    @Test
    void createGeneratesBase62CodeFromId() {
        saveAssignsId(125L);
        LinkResponse r = service.create(new CreateLinkRequest("https://example.com", null, null));
        assertThat(r.shortCode()).isEqualTo("21"); // 125 = 2*62 + 1
        assertThat(r.shortUrl()).isEqualTo("http://short.test/21");
        assertThat(r.expiresAt()).isNull();
    }

    @Test
    void createSuffixesGeneratedCodeThatClashesWithAnAlias() {
        saveAssignsId(125L);
        when(links.existsByShortCode("21")).thenReturn(true);
        assertThat(service.create(new CreateLinkRequest("https://example.com", null, null)).shortCode()).isEqualTo("21-");
    }

    @Test
    void createUsesCustomAliasAndComputesExpiry() {
        saveAssignsId(1L);
        LinkResponse r = service.create(new CreateLinkRequest("https://example.com", "my-link", 7));
        assertThat(r.shortCode()).isEqualTo("my-link");
        assertThat(r.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
    }

    @Test
    void createRejectsTakenAlias() {
        when(links.existsByShortCode("taken")).thenReturn(true);
        assertThatThrownBy(() -> service.create(new CreateLinkRequest("https://example.com", "taken", null)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(links, never()).save(any());
    }

    @Test
    void createMapsRaceOnUniqueIndexToConflict() {
        when(links.save(any())).thenThrow(new DataIntegrityViolationException("dup"));
        assertThatThrownBy(() -> service.create(new CreateLinkRequest("https://example.com", "race", null)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    // ---- resolve ----

    @Test
    void resolveReturnsCachedUrlWithoutTouchingDb() {
        when(cache.get("abc")).thenReturn(Optional.of("https://cached.com"));
        assertThat(service.resolve("abc")).isEqualTo("https://cached.com");
        verifyNoInteractions(links);
    }

    @Test
    void resolveMissLoadsFromDbAndFillsCacheWithDefaultTtl() {
        when(cache.get("abc")).thenReturn(Optional.empty());
        when(links.findByShortCode("abc")).thenReturn(Optional.of(link("https://example.com", null, true)));
        assertThat(service.resolve("abc")).isEqualTo("https://example.com");
        verify(cache).put("abc", "https://example.com", Duration.ofHours(24));
    }

    @Test
    void resolveCapsCacheTtlAtExpiry() {
        when(cache.get("abc")).thenReturn(Optional.empty());
        when(links.findByShortCode("abc")).thenReturn(Optional.of(link("https://example.com", NOW.plusSeconds(90), true)));
        service.resolve("abc");
        verify(cache).put("abc", "https://example.com", Duration.ofSeconds(90));
    }

    @Test
    void resolveUnknownCodeIs404() {
        when(cache.get(anyString())).thenReturn(Optional.empty());
        when(links.findByShortCode("nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.resolve("nope"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void resolveExpiredLinkIs410AndNotCached() {
        when(cache.get("abc")).thenReturn(Optional.empty());
        when(links.findByShortCode("abc")).thenReturn(Optional.of(link("https://example.com", NOW.minusSeconds(1), true)));
        assertThatThrownBy(() -> service.resolve("abc"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.GONE));
        verify(cache, never()).put(any(), any(), any());
    }

    @Test
    void resolveInactiveLinkIs410() {
        when(cache.get("abc")).thenReturn(Optional.empty());
        when(links.findByShortCode("abc")).thenReturn(Optional.of(link("https://example.com", null, false)));
        assertThatThrownBy(() -> service.resolve("abc"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.GONE));
    }

    // ---- deactivate / stats / analytics ----

    @Test
    void deactivateMarksInactiveAndEvictsCache() {
        Link l = link("https://example.com", null, true);
        when(links.findByShortCode("abc")).thenReturn(Optional.of(l));
        service.deactivate("abc");
        assertThat(l.isActive()).isFalse();
        verify(cache).evict("abc");
    }

    @Test
    void statsMapsLinkToDto() {
        Link l = link("https://example.com", null, true);
        l.setClickCount(42);
        when(links.findByShortCode("abc")).thenReturn(Optional.of(l));
        StatsResponse s = service.stats("abc");
        assertThat(s.clickCount()).isEqualTo(42);
        assertThat(s.shortUrl()).isEqualTo("http://short.test/abc");
        assertThat(s.active()).isTrue();
    }

    @Test
    void analyticsFillsSevenDaysAndMapsReferrers() {
        Link l = link("https://example.com", null, true);
        l.setClickCount(5);
        when(links.findByShortCode("abc")).thenReturn(Optional.of(l));
        when(clicks.countPerDay(eq("abc"), any())).thenReturn(List.<Object[]>of(
                new Object[] {java.sql.Date.valueOf("2026-01-10"), 3L},
                new Object[] {java.sql.Date.valueOf("2026-01-08"), 2L}));
        when(clicks.topReferrers(eq("abc"), any())).thenReturn(List.<Object[]>of(new Object[] {"direct", 4L}));

        AnalyticsResponse a = service.analytics("abc");

        assertThat(a.totalClicks()).isEqualTo(5);
        assertThat(a.clicksPerDay()).hasSize(7);
        assertThat(a.clicksPerDay().get(0).date()).isEqualTo(LocalDate.parse("2026-01-04"));
        assertThat(a.clicksPerDay().get(4).clicks()).isEqualTo(2);  // Jan 8
        assertThat(a.clicksPerDay().get(5).clicks()).isZero();      // Jan 9, no clicks
        assertThat(a.clicksPerDay().get(6).clicks()).isEqualTo(3);  // Jan 10
        assertThat(a.topReferrers()).containsExactly(new AnalyticsResponse.ReferrerCount("direct", 4));

        ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
        verify(clicks).countPerDay(eq("abc"), since.capture());
        assertThat(since.getValue()).isEqualTo(Instant.parse("2026-01-04T00:00:00Z"));
    }
}
