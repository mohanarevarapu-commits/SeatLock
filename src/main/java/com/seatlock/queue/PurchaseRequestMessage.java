package com.seatlock.queue;

import java.time.Instant;
import java.util.Set;

/**
 * A buyer's request to hold seats, as it travels on {@link TicketQueueConfig#PURCHASE_QUEUE}.
 * {@code accountId} is the signed-in buyer, or null for a guest.
 */
public record PurchaseRequestMessage(
        String requestId,
        Long eventId,
        String customerRef,
        Set<Long> eventSeatIds,
        Instant requestedAt,
        Long accountId) {
}
