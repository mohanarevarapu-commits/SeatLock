package com.seatlock.dto;

import com.seatlock.model.EventSeat;

/** One seat inside a hold or booking, with its price. */
public record SeatLine(Long eventSeatId, String section, String label, long priceCents) {

    public static SeatLine from(EventSeat eventSeat) {
        var seat = eventSeat.getSeat();
        return new SeatLine(eventSeat.getId(), seat.getSection().getName(), seat.getLabel(),
                seat.getSection().getPriceCents());
    }
}
