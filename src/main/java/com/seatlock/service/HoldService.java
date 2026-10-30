package com.seatlock.service;

import com.seatlock.config.HoldProperties;
import com.seatlock.dto.CreateHoldRequest;
import com.seatlock.dto.HoldResponse;
import com.seatlock.dto.SeatLine;
import com.seatlock.exception.HoldNotActiveException;
import com.seatlock.exception.InvalidRequestException;
import com.seatlock.exception.NotFoundException;
import com.seatlock.exception.SeatUnavailableException;
import com.seatlock.model.Event;
import com.seatlock.model.EventSeat;
import com.seatlock.model.Hold;
import com.seatlock.model.HoldStatus;
import com.seatlock.repository.EventSeatRepository;
import com.seatlock.repository.HoldRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Places and releases temporary seat holds.
 *
 * <p>Concurrency model: every write path locks the affected {@link EventSeat} rows
 * with {@code SELECT ... FOR UPDATE} in ascending id order before checking their
 * state. Paths that also touch an existing hold lock the hold row first, then the
 * seats. Creating a hold never locks other holds, so the lock order is always
 * hold -> seats and transactions cannot deadlock on each other.
 */
@Service
public class HoldService {

    private final HoldRepository holdRepository;
    private final EventSeatRepository eventSeatRepository;
    private final EventService eventService;
    private final HoldProperties properties;
    private final Clock clock;

    public HoldService(HoldRepository holdRepository, EventSeatRepository eventSeatRepository,
                       EventService eventService, HoldProperties properties, Clock clock) {
        this.holdRepository = holdRepository;
        this.eventSeatRepository = eventSeatRepository;
        this.eventService = eventService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public HoldResponse createHold(Long eventId, CreateHoldRequest request) {
        Instant now = clock.instant();
        Event event = eventService.findEvent(eventId);
        if (event.hasStarted(now)) {
            throw new InvalidRequestException("Event " + eventId + " has already started");
        }
        Set<Long> seatIds = request.eventSeatIds();
        if (seatIds.size() > properties.maxSeatsPerHold()) {
            throw new InvalidRequestException(
                    "A hold may contain at most " + properties.maxSeatsPerHold() + " seats");
        }

        List<EventSeat> seats = eventSeatRepository.findAllByIdForUpdate(seatIds);
        requireAllBelongToEvent(seatIds, seats, eventId);

        List<Long> unavailable = seats.stream()
                .filter(seat -> !seat.isAvailableAt(now))
                .map(EventSeat::getId)
                .toList();
        if (!unavailable.isEmpty()) {
            throw new SeatUnavailableException(unavailable);
        }

        Instant expiresAt = earliest(now.plus(properties.ttl()), event.getStartsAt());
        Hold hold = holdRepository.save(new Hold(event, request.customerRef(), seatIds, now, expiresAt));
        for (EventSeat seat : seats) {
            seat.hold(hold.getId(), expiresAt, now);
        }
        return toResponse(hold);
    }

    @Transactional(readOnly = true)
    public HoldResponse getHold(Long holdId) {
        return toResponse(findHold(holdId));
    }

    /** Gives the seats back before the hold would have expired on its own. */
    @Transactional
    public HoldResponse releaseHold(Long holdId) {
        Hold hold = holdRepository.findByIdForUpdate(holdId)
                .orElseThrow(() -> new NotFoundException("Hold", holdId));
        if (hold.getStatus() != HoldStatus.ACTIVE) {
            throw new HoldNotActiveException(holdId, hold.getStatus());
        }
        eventSeatRepository.findAllByIdForUpdate(hold.getEventSeatIds())
                .forEach(seat -> seat.releaseIfHeldBy(holdId));
        hold.release();
        return toResponse(hold);
    }

    HoldResponse toResponse(Hold hold) {
        List<SeatLine> lines = eventSeatRepository.findAllWithSeatById(hold.getEventSeatIds()).stream()
                .map(SeatLine::from)
                .toList();
        long total = lines.stream().mapToLong(SeatLine::priceCents).sum();
        return new HoldResponse(hold.getId(), hold.getEvent().getId(), hold.getCustomerRef(), hold.getStatus(),
                hold.getExpiresAt(), total, lines);
    }

    private Hold findHold(Long holdId) {
        return holdRepository.findById(holdId).orElseThrow(() -> new NotFoundException("Hold", holdId));
    }

    private static void requireAllBelongToEvent(Set<Long> requested, List<EventSeat> found, Long eventId) {
        Set<Long> missing = new HashSet<>(requested);
        for (EventSeat seat : found) {
            if (Objects.equals(seat.getEvent().getId(), eventId)) {
                missing.remove(seat.getId());
            }
        }
        if (!missing.isEmpty()) {
            throw new NotFoundException("Event seats", missing + " for event " + eventId);
        }
    }

    private static Instant earliest(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
