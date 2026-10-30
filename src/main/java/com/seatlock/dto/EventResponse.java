package com.seatlock.dto;

import com.seatlock.model.Event;
import com.seatlock.model.VenueType;
import java.time.Instant;

public record EventResponse(Long id, String name, Long venueId, String venueName, String venueCity,
                            VenueType venueType, Instant startsAt) {

    public static EventResponse from(Event event) {
        var venue = event.getVenue();
        return new EventResponse(event.getId(), event.getName(), venue.getId(), venue.getName(), venue.getCity(),
                venue.getType(), event.getStartsAt());
    }
}
