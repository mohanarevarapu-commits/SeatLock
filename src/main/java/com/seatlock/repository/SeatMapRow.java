package com.seatlock.repository;

import com.seatlock.model.SeatStatus;
import java.time.Instant;

/** One seat of an event's seat map, read as plain columns by {@link EventSeatRepository#findSeatMap}. */
public record SeatMapRow(Long eventSeatId, Long sectionId, String sectionName, long priceCents,
                         String rowLabel, int number, SeatStatus status, Instant heldUntil) {
}
