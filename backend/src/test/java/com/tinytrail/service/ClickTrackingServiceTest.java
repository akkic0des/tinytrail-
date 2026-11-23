package com.tinytrail.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.tinytrail.config.AppProperties;
import com.tinytrail.model.ClickEvent;
import com.tinytrail.repository.ClickEventRepository;
import com.tinytrail.repository.LinkRepository;
import java.time.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClickTrackingServiceTest {

    @Mock ClickEventRepository events;
    @Mock LinkRepository links;

    ClickTrackingService service;

    @BeforeEach
    void setUp() {
        var props = new AppProperties("http://x", "*", "salt", new AppProperties.Cache(true, Duration.ofHours(1)),
                new AppProperties.RateLimit(10, Duration.ofSeconds(60)));
        service = new ClickTrackingService(events, links, props, Clock.fixed(Instant.parse("2026-01-10T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void storesHashedIpNeverTheRawOne() {
        service.record("abc", "https://google.com", "Mozilla", "203.0.113.7");

        ArgumentCaptor<ClickEvent> saved = ArgumentCaptor.forClass(ClickEvent.class);
        verify(events).save(saved.capture());
        ClickEvent e = saved.getValue();
        assertThat(e.getIpHash()).hasSize(64).doesNotContain("203.0.113.7").isEqualTo(service.hash("203.0.113.7"));
        assertThat(e.getReferrer()).isEqualTo("https://google.com");
        assertThat(e.getClickedAt()).isEqualTo(Instant.parse("2026-01-10T12:00:00Z"));
        verify(links).incrementClickCount("abc");
    }

    @Test
    void missingReferrerBecomesDirectAndLongValuesAreTruncated() {
        service.record("abc", null, "u".repeat(1000), "1.1.1.1");

        ArgumentCaptor<ClickEvent> saved = ArgumentCaptor.forClass(ClickEvent.class);
        verify(events).save(saved.capture());
        assertThat(saved.getValue().getReferrer()).isEqualTo("direct");
        assertThat(saved.getValue().getUserAgent()).hasSize(512);
    }
}
