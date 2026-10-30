package com.seatlock.dto;

import com.seatlock.model.Event;
import java.time.Instant;

public record EventResponse(Long id, String name, Long venueId, String venueName, Instant startsAt) {

    public static EventResponse from(Event event) {
        return new EventResponse(event.getId(), event.getName(), event.getVenue().getId(),
                event.getVenue().getName(), event.getStartsAt());
    }
}
