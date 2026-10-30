package com.seatlock.ratelimit;

import java.time.Duration;
import java.time.Instant;

/**
 * Token bucket for one client: holds up to {@code capacity} tokens and refills continuously at
 * {@code refillPerSecond}. Each request takes one token; an empty bucket means "slow down".
 * Thread-safe.
 */
final class TokenBucket {

    private static final double NANOS_PER_SECOND = 1_000_000_000d;

    private final double capacity;
    private final double refillPerNano;
    private double tokens;
    private Instant lastRefill;

    TokenBucket(int capacity, int refillPerSecond, Instant now) {
        this.capacity = capacity;
        this.refillPerNano = refillPerSecond / NANOS_PER_SECOND;
        this.tokens = capacity;
        this.lastRefill = now;
    }

    /**
     * Takes one token if one is available. Returns {@link Duration#ZERO} on success, otherwise
     * how long the caller must wait until the next token arrives.
     */
    synchronized Duration tryConsume(Instant now) {
        refill(now);
        if (tokens >= 1) {
            tokens -= 1;
            return Duration.ZERO;
        }
        return Duration.ofNanos((long) Math.ceil((1 - tokens) / refillPerNano));
    }

    /** A full bucket is indistinguishable from a new one, so it can be dropped without losing state. */
    synchronized boolean isFull(Instant now) {
        refill(now);
        return tokens >= capacity;
    }

    private void refill(Instant now) {
        if (now.isAfter(lastRefill)) {
            long elapsedNanos = Duration.between(lastRefill, now).toNanos();
            tokens = Math.min(capacity, tokens + elapsedNanos * refillPerNano);
            lastRefill = now;
        }
    }
}
