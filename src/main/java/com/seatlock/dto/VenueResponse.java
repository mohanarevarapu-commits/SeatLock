package com.seatlock.dto;

import com.seatlock.model.MapZone;
import com.seatlock.model.Section;
import com.seatlock.model.Venue;
import com.seatlock.model.VenueType;
import java.util.List;

public record VenueResponse(Long id, String name, String city, String address, VenueType type, MapZone stageZone,
                            String stageLabel, int capacity, List<SectionSummary> sections) {

    public record SectionSummary(Long id, String name, MapZone zone, int seatCount, long priceCents) {

        static SectionSummary from(Section section) {
            return new SectionSummary(section.getId(), section.getName(), section.getZone(),
                    section.getSeats().size(), section.getPriceCents());
        }
    }

    public static VenueResponse from(Venue venue) {
        return new VenueResponse(venue.getId(), venue.getName(), venue.getCity(), venue.getAddress(), venue.getType(),
                venue.getStageZone(), venue.getStageLabel(), venue.capacity(),
                venue.getSections().stream().map(SectionSummary::from).toList());
    }
}
