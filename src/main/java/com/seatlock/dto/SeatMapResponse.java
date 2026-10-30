package com.seatlock.dto;

import com.seatlock.model.MapZone;
import com.seatlock.model.SeatStatus;
import com.seatlock.model.VenueType;
import java.util.List;

/**
 * An event's seats grouped by section, with the venue layout needed to draw them: where the stage
 * is and which map zone each section is in.
 */
public record SeatMapResponse(Long eventId, VenueType venueType, MapZone stageZone, String stageLabel,
                              int availableCount, List<SectionMap> sections) {

    public record SectionMap(Long sectionId, String name, MapZone zone, long priceCents, int availableCount,
                             List<SeatView> seats) {
    }

    public record SeatView(Long eventSeatId, String label, String row, int number, SeatStatus status) {
    }
}
