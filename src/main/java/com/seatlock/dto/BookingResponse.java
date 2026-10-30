package com.seatlock.dto;

import java.time.Instant;
import java.util.List;

public record BookingResponse(
        String reference,
        Long holdId,
        Long eventId,
        long totalCents,
        Instant createdAt,
        List<SeatLine> seats) {
}
