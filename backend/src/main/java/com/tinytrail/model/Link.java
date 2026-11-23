package com.tinytrail.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "links")
@Getter
@Setter
@NoArgsConstructor
public class Link {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // the auto-increment ID is what we Base62-encode
    private Long id;

    // ascii_bin = case-SENSITIVE. MySQL's default collation is case-insensitive, which would make
    // "aB" and "ab" collide on the unique index even though Base62 treats them as different codes.
    // Nullable because a generated code can only be computed after the INSERT returns the ID.
    @Column(unique = true, length = 20, columnDefinition = "VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin")
    private String shortCode;

    @Column(nullable = false, length = 2048)
    private String originalUrl;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant expiresAt;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private long clickCount;
}
