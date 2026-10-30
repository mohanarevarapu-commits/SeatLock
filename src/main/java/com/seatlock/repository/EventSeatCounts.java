package com.seatlock.repository;

/** How many seats an event has, how many are still on sale, and its cheapest price, read by {@link EventSeatRepository#countSeatsByEvent}. */
public record EventSeatCounts(Long eventId, Long totalSeats, Long availableSeats, Long fromPriceCents) {
}
