package com.tinytrail.service;

import com.tinytrail.config.AppProperties;
import com.tinytrail.model.ClickEvent;
import com.tinytrail.repository.ClickEventRepository;
import com.tinytrail.repository.LinkRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClickTrackingService {

    private final ClickEventRepository events;
    private final LinkRepository links;
    private final AppProperties props;
    private final Clock clock;

    /**
     * Runs on the "clickExecutor" pool so the 302 is returned without waiting for two DB writes.
     * Must be called from another bean (Spring's @Async works through a proxy).
     * shortcut: every click UPDATEs the same link row, so one viral link serializes on a row lock;
     * batch counts in Redis and flush periodically if that ever shows up in load tests.
     */
    @Async("clickExecutor")
    @Transactional
    public void record(String shortCode, String referrer, String userAgent, String clientIp) {
        ClickEvent e = new ClickEvent();
        e.setShortCode(shortCode);
        e.setClickedAt(clock.instant());
        e.setReferrer(referrer == null || referrer.isBlank() ? "direct" : cut(referrer));
        e.setUserAgent(cut(userAgent));
        e.setIpHash(hash(clientIp));
        events.save(e);
        links.incrementClickCount(shortCode);
    }

    /** Salted so the ~4 billion IPv4 addresses can't be reversed with a precomputed table. */
    String hash(String ip) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((props.ipHashSalt() + ip).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex); // SHA-256 is mandatory in every JVM
        }
    }

    private static String cut(String s) {
        return s == null ? null : s.length() <= 512 ? s : s.substring(0, 512);
    }
}
