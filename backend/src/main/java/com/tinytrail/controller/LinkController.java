package com.tinytrail.controller;

import com.tinytrail.dto.*;
import com.tinytrail.service.LinkService;
import com.tinytrail.service.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/links")
@RequiredArgsConstructor
public class LinkController {

    private final LinkService linkService;
    private final RateLimiter rateLimiter;

    @PostMapping
    public ResponseEntity<LinkResponse> create(@Valid @RequestBody CreateLinkRequest request, HttpServletRequest http) {
        rateLimiter.check(http.getRemoteAddr()); // only creation is limited; redirects must stay fast and open
        return ResponseEntity.status(HttpStatus.CREATED).body(linkService.create(request));
    }

    @GetMapping("/{shortCode}/stats")
    public StatsResponse stats(@PathVariable String shortCode) {
        return linkService.stats(shortCode);
    }

    @GetMapping("/{shortCode}/analytics")
    public AnalyticsResponse analytics(@PathVariable String shortCode) {
        return linkService.analytics(shortCode);
    }
}
