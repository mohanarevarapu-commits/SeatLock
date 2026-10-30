package com.seatlock.dto;

import com.seatlock.model.SeatStatus;
import java.util.List;

public record SeatMapResponse(Long eventId, int availableCount, List<SectionMap> sections) {

    public record SectionMap(Long sectionId, String name, long priceCents, int availableCount, List<SeatView> seats) {
    }

    public record SeatView(Long eventSeatId, String label, String row, int number, SeatStatus status) {
    }
}
