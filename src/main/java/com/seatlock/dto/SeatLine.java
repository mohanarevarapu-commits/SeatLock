package com.seatlock.dto;

import com.seatlock.model.EventSeat;

/**
 * One seat inside a hold or booking, with its price.
 *
 * @param label  row and seat number together, e.g. "A12"
 * @param row    the row on its own, e.g. "A"
 * @param number the seat number within the row, e.g. 12
 */
public record SeatLine(Long eventSeatId, String section, String label, long priceCents, String row, int number) {

    public static SeatLine from(EventSeat eventSeat) {
        var seat = eventSeat.getSeat();
        return new SeatLine(eventSeat.getId(), seat.getSection().getName(), seat.getLabel(),
                seat.getSection().getPriceCents(), seat.getRowLabel(), seat.getNumber());
    }
}
