package com.seatlock.dto;

import com.seatlock.model.VenueType;
import java.time.Instant;
import java.util.List;

/**
 * Everything printed on a ticket: the concert, the venue and how to find it, the seats and the total.
 * Used for the booking email and for Find my tickets. Its QR code holds {@code reference}; the image
 * is at {@code GET /api/bookings/{reference}/qr}.
 */
public record TicketResponse(
        String reference,
        Long eventId,
        String eventName,
        Instant startsAt,
        String venueName,
        VenueType venueType,
        String venueAddress,
        String venueCity,
        String stageLabel,
        List<SeatLine> seats,
        long totalCents,
        Instant bookedAt) {
}
