package com.seatlock.dto;

import com.seatlock.model.Event;
import com.seatlock.model.VenueType;
import com.seatlock.repository.EventSeatCounts;
import java.time.Instant;

/**
 * An event with its seat totals: {@code availableSeats} of {@code totalSeats} are still on sale,
 * from {@code fromPriceCents} (the cheapest section).
 */
public record EventResponse(Long id, String name, Long venueId, String venueName, String venueCity,
                            String venueAddress, VenueType venueType, Instant startsAt, int totalSeats,
                            int availableSeats, long fromPriceCents) {

    public static EventResponse from(Event event, EventSeatCounts counts) {
        var venue = event.getVenue();
        return new EventResponse(event.getId(), event.getName(), venue.getId(), venue.getName(), venue.getCity(),
                venue.getAddress(), venue.getType(), event.getStartsAt(), counts.totalSeats().intValue(),
                counts.availableSeats().intValue(), counts.fromPriceCents());
    }
}
