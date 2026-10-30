package com.seatlock.dto;

import java.time.Instant;
import java.util.List;

/**
 * A signed-in buyer's bookings, split into shows still to come (soonest first) and shows that
 * have started (most recent first).
 */
public record AccountBookingsResponse(List<AccountBooking> upcoming, List<AccountBooking> past) {

    public record AccountBooking(String reference, Long eventId, String eventName, String venueName, String venueCity,
                                 Instant startsAt, long totalCents, Instant bookedAt, List<SeatLine> seats) {
    }
}
