package com.tinytrail.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "click_events", indexes = @Index(name = "idx_click_code_time", columnList = "shortCode, clickedAt"))
@Getter
@Setter
@NoArgsConstructor
public class ClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin")
    private String shortCode;

    @Column(nullable = false)
    private Instant clickedAt;

    @Column(length = 512)
    private String referrer;

    @Column(length = 512)
    private String userAgent;

    // SHA-256 hex of (salt + IP). The raw IP is never stored.
    @Column(nullable = false, length = 64)
    private String ipHash;
}
