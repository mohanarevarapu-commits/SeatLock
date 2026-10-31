package com.seatlock.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Per-client rate limit on the checkout endpoints, bound from {@code seatlock.rate-limit.*}.
 *
 * @param enabled           turn the limit off entirely (tests and load tests do this)
 * @param requestsPerSecond sustained rate allowed per client IP; also the burst size
 * @param paths             Ant-style patterns of the request paths to limit
 */
@Validated
@ConfigurationProperties("seatlock.rate-limit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @Min(1) @DefaultValue("5") int requestsPerSecond,
        @NotEmpty @DefaultValue({"/api/events/*/holds", "/api/events/*/purchase-requests", "/api/holds/*/confirm",
                "/api/auth/*", "/api/tickets/*"}) List<String> paths) {
}
