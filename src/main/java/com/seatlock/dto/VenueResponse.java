package com.seatlock.dto;

import com.seatlock.model.Section;
import com.seatlock.model.Venue;
import java.util.List;

public record VenueResponse(Long id, String name, String city, int capacity, List<SectionSummary> sections) {

    public record SectionSummary(Long id, String name, int seatCount, long priceCents) {

        static SectionSummary from(Section section) {
            return new SectionSummary(section.getId(), section.getName(),
                    section.getSeats().size(), section.getPriceCents());
        }
    }

    public static VenueResponse from(Venue venue) {
        return new VenueResponse(venue.getId(), venue.getName(), venue.getCity(), venue.capacity(),
                venue.getSections().stream().map(SectionSummary::from).toList());
    }
}
