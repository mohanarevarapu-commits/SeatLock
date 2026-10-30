package com.seatlock.dto;

import com.seatlock.model.HoldStatus;
import java.time.Instant;
import java.util.List;

public record HoldResponse(
        Long id,
        Long eventId,
        String customerRef,
        HoldStatus status,
        Instant expiresAt,
        long totalCents,
        List<SeatLine> seats) {
}
