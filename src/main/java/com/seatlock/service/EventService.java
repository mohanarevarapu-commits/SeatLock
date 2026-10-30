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
import com.seatlock.repository.EventSeatRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        return EventResponse.from(event);
    }

    @Transactional(readOnly = true)
    public EventResponse getEvent(Long id) {
        return EventResponse.from(findEvent(id));
    }

    @Transactional(readOnly = true)
    public List<EventResponse> listEvents() {
        return eventRepository.findAll().stream().map(EventResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public SeatMapResponse getSeatMap(Long eventId) {
        findEvent(eventId);
        Instant now = clock.instant();

        Map<Section, List<SeatView>> bySection = new LinkedHashMap<>();
        for (EventSeat eventSeat : eventSeatRepository.findSeatMap(eventId)) {
            Seat seat = eventSeat.getSeat();
            bySection.computeIfAbsent(seat.getSection(), s -> new ArrayList<>())
                    .add(new SeatView(eventSeat.getId(), seat.getLabel(), seat.getRowLabel(),
                            seat.getNumber(), eventSeat.effectiveStatus(now)));
        }

        List<SectionMap> sections = bySection.entrySet().stream()
                .map(e -> new SectionMap(e.getKey().getId(), e.getKey().getName(), e.getKey().getPriceCents(),
                        countAvailable(e.getValue()), e.getValue()))
                .toList();
        int available = sections.stream().mapToInt(SectionMap::availableCount).sum();
        return new SeatMapResponse(eventId, available, sections);
    }

    Event findEvent(Long id) {
        return eventRepository.findById(id).orElseThrow(() -> new NotFoundException("Event", id));
    }

    private static int countAvailable(List<SeatView> seats) {
        return (int) seats.stream().filter(s -> s.status() == SeatStatus.AVAILABLE).count();
    }
}
