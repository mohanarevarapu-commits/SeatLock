package com.seatlock.dto;

import com.seatlock.model.PurchaseRequest;
import com.seatlock.model.PurchaseRequestStatus;
import java.time.Instant;

/**
 * Where a queued purchase request stands. {@code holdId} is set once it is {@code HELD};
 * {@code reason} explains a {@code REJECTED} or {@code FAILED} outcome.
 */
public record PurchaseRequestResponse(
        String requestId,
        Long eventId,
        PurchaseRequestStatus status,
        Long holdId,
        String reason,
        Instant processedAt) {

    public static PurchaseRequestResponse queued(String requestId, Long eventId) {
        return new PurchaseRequestResponse(requestId, eventId, PurchaseRequestStatus.QUEUED, null, null, null);
    }

    public static PurchaseRequestResponse from(PurchaseRequest request) {
        return new PurchaseRequestResponse(request.getId(), request.getEventId(), request.getStatus(),
                request.getHoldId(), request.getReason(), request.getProcessedAt());
    }
}
