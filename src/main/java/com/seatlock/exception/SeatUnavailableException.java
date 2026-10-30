package com.seatlock.exception;

import java.util.List;

public class SeatUnavailableException extends RuntimeException {

    private final List<Long> eventSeatIds;

    public SeatUnavailableException(List<Long> eventSeatIds) {
        super("Seats no longer available: " + eventSeatIds);
        this.eventSeatIds = List.copyOf(eventSeatIds);
    }

    public List<Long> getEventSeatIds() {
        return eventSeatIds;
    }
}
