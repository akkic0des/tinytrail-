package com.tinytrail.controller;

import com.tinytrail.service.ClickTrackingService;
import com.tinytrail.service.LinkService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RedirectController {

    private final LinkService linkService;
    private final ClickTrackingService clickTracking;

    // 302 (not 301): browsers must keep asking us, otherwise they cache the redirect and we lose clicks/expiry.
    @GetMapping("/{shortCode:[a-zA-Z0-9-]+}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode, HttpServletRequest request) {
        String url = linkService.resolve(shortCode);
        // Values are read here: the request object must not be touched from the async thread.
        clickTracking.record(shortCode, request.getHeader(HttpHeaders.REFERER),
                request.getHeader(HttpHeaders.USER_AGENT), request.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, url).build();
    }
}
