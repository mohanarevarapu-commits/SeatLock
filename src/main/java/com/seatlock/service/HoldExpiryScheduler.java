package com.seatlock.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs {@link HoldExpiryService} on a fixed delay. Disabled in tests so they can drive expiry directly. */
@Component
@ConditionalOnProperty(name = "seatlock.holds.sweeper-enabled", havingValue = "true", matchIfMissing = true)
public class HoldExpiryScheduler {

    private final HoldExpiryService holdExpiryService;

    public HoldExpiryScheduler(HoldExpiryService holdExpiryService) {
        this.holdExpiryService = holdExpiryService;
    }

    @Scheduled(fixedDelayString = "${seatlock.holds.sweep-interval:PT30S}")
    public void sweep() {
        holdExpiryService.expireLapsedHolds();
    }
}
