package com.tinytrail.dto;

import jakarta.validation.constraints.*;

public record CreateLinkRequest(
        @NotNull(message = "Enter a valid URL starting with http:// or https://")
        @Size(max = 2048, message = "URL must be at most 2048 characters")
        // Only http/https: blocks javascript:/data: URLs that would turn a redirect into XSS.
        @Pattern(regexp = "(?i)^https?://[^\\s/$.?#][^\\s]*$", message = "Enter a valid URL starting with http:// or https://")
        String url,

        @Pattern(regexp = "^[a-zA-Z0-9-]{3,20}$", message = "Alias must be 3-20 characters: letters, numbers or hyphens")
        String customAlias,

        @Min(value = 1, message = "Expiry must be between 1 and 365 days")
        @Max(value = 365, message = "Expiry must be between 1 and 365 days")
        Integer expiresInDays) {}
