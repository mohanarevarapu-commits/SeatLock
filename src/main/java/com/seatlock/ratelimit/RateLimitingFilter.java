package com.seatlock.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatlock.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limits each client IP to {@code seatlock.rate-limit.requests-per-second} requests on the
 * checkout endpoints, using one {@link TokenBucket} per IP. Requests over the limit get a
 * {@code 429 Too Many Requests} problem response with a {@code Retry-After} header and never
 * reach the database.
 *
 * <p>The client is identified by {@link HttpServletRequest#getRemoteAddr()}. Behind a proxy or
 * load balancer, set {@code server.forward-headers-strategy=native} so that is the real client
 * address; a raw {@code X-Forwarded-For} header is never trusted here because clients can forge it.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    /** Above this many tracked IPs, idle (full) buckets are dropped to bound memory. */
    static final int MAX_TRACKED_CLIENTS = 100_000;

    private final RateLimitProperties properties;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimitingFilter(RateLimitProperties properties, Clock clock, ObjectMapper objectMapper) {
        this.properties = properties;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.enabled()) {
            return true;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return properties.paths().stream().noneMatch(pattern -> pathMatcher.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Instant now = clock.instant();
        if (buckets.size() >= MAX_TRACKED_CLIENTS) {
            buckets.values().removeIf(bucket -> bucket.isFull(now));
        }
        int rate = properties.requestsPerSecond();
        TokenBucket bucket = buckets.computeIfAbsent(request.getRemoteAddr(), ip -> new TokenBucket(rate, rate, now));

        Duration wait = bucket.tryConsume(now);
        if (wait.isZero()) {
            chain.doFilter(request, response);
        } else {
            reject(request, response, wait);
        }
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, Duration wait) throws IOException {
        long retryAfterSeconds = Math.max(1, (wait.toMillis() + 999) / 1000);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Too many checkout requests from your address. The limit is %d requests per second; retry after %d second%s."
                        .formatted(properties.requestsPerSecond(), retryAfterSeconds, retryAfterSeconds == 1 ? "" : "s"));
        problem.setTitle("Too many requests");
        problem.setInstance(URI.create(request.getRequestURI()));

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
