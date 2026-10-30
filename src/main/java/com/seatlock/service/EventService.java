package com.seatlock.service;

import com.seatlock.dto.CreateEventRequest;
import com.seatlock.dto.EventResponse;
import com.seatlock.dto.SeatMapResponse;
import com.seatlock.dto.SeatMapResponse.SeatView;
import com.seatlock.dto.SeatMapResponse.SectionMap;
import com.seatlock.exception.NotFoundException;
import com.seatlock.model.Event;
import com.seatlock.model.EventSeat;
import com.seatlock.model.Seat;
import com.seatlock.model.SeatStatus;
import com.seatlock.model.Section;
import com.seatlock.model.Venue;
import com.seatlock.repository.EventRepository;
import com.seatlock.repository.EventSeatCounts;
import com.seatlock.repository.EventSeatRepository;
import com.seatlock.repository.SeatMapRow;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final EventSeatRepository eventSeatRepository;
    private final VenueService venueService;
    private final Clock clock;

    public EventService(EventRepository eventRepository, EventSeatRepository eventSeatRepository,
                        VenueService venueService, Clock clock) {
        this.eventRepository = eventRepository;
        this.eventSeatRepository = eventSeatRepository;
        this.venueService = venueService;
        this.clock = clock;
    }

    /** Creates the event and one {@link EventSeat} per physical seat in the venue. */
    @Transactional
    public EventResponse createEvent(CreateEventRequest request) {
        Venue venue = venueService.findVenue(request.venueId());
        Event event = eventRepository.save(new Event(venue, request.name(), request.startsAt()));

        List<EventSeat> eventSeats = new ArrayList<>(venue.capacity());
        for (Section section : venue.getSections()) {
            for (Seat seat : section.getSeats()) {
                eventSeats.add(new EventSeat(event, seat));
            }
        }
        eventSeatRepository.saveAll(eventSeats);
        return withCounts(List.of(event)).get(0);
    }

    @Transactional(readOnly = true)
    public EventResponse getEvent(Long id) {
        return withCounts(List.of(findEvent(id))).get(0);
    }

    /** All events, soonest first, each with its seat totals (one counting query for the whole list). */
    @Transactional(readOnly = true)
    public List<EventResponse> listEvents() {
        return withCounts(eventRepository.findAll(Sort.by("startsAt", "id")));
    }

    @Transactional(readOnly = true)
    public SeatMapResponse getSeatMap(Long eventId) {
        Venue venue = findEvent(eventId).getVenue();
        Instant now = clock.instant();

        Map<Long, SeatMapRow> sectionInfo = new LinkedHashMap<>();
        Map<Long, List<SeatView>> bySection = new LinkedHashMap<>();
        for (SeatMapRow row : eventSeatRepository.findSeatMap(eventId)) {
            sectionInfo.putIfAbsent(row.sectionId(), row);
            bySection.computeIfAbsent(row.sectionId(), id -> new ArrayList<>())
                    .add(new SeatView(row.eventSeatId(), row.rowLabel() + row.number(), row.rowLabel(),
                            row.number(), EventSeat.effectiveStatus(row.status(), row.heldUntil(), now)));
        }

        List<SectionMap> sections = bySection.entrySet().stream()
                .map(e -> {
                    SeatMapRow info = sectionInfo.get(e.getKey());
                    return new SectionMap(e.getKey(), info.sectionName(), info.zone(), info.priceCents(),
                            countAvailable(e.getValue()), e.getValue());
                })
                .toList();
        int available = sections.stream().mapToInt(SectionMap::availableCount).sum();
        return new SeatMapResponse(eventId, venue.getType(), venue.getStageZone(), venue.getStageLabel(),
                available, sections);
    }

    private List<EventResponse> withCounts(List<Event> events) {
        if (events.isEmpty()) {
            return List.of();
        }
        Map<Long, EventSeatCounts> counts = eventSeatRepository
                .countSeatsByEvent(events.stream().map(Event::getId).toList(), clock.instant()).stream()
                .collect(Collectors.toMap(EventSeatCounts::eventId, c -> c));
        EventSeatCounts none = new EventSeatCounts(null, 0L, 0L, 0L);
        return events.stream().map(e -> EventResponse.from(e, counts.getOrDefault(e.getId(), none))).toList();
    }

    Event findEvent(Long id) {
        return eventRepository.findById(id).orElseThrow(() -> new NotFoundException("Event", id));
    }

    private static int countAvailable(List<SeatView> seats) {
        return (int) seats.stream().filter(s -> s.status() == SeatStatus.AVAILABLE).count();
    }
}
