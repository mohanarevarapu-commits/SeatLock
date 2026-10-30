package com.seatlock.controller;

import com.seatlock.dto.CreateEventRequest;
import com.seatlock.dto.CreateHoldRequest;
import com.seatlock.dto.EventResponse;
import com.seatlock.dto.HoldResponse;
import com.seatlock.dto.SeatMapResponse;
import com.seatlock.service.EventService;
import com.seatlock.service.HoldService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;
    private final HoldService holdService;

    public EventController(EventService eventService, HoldService holdService) {
        this.eventService = eventService;
        this.holdService = holdService;
    }

    @PostMapping
    public ResponseEntity<EventResponse> create(@Valid @RequestBody CreateEventRequest request) {
        EventResponse event = eventService.createEvent(request);
        return ResponseEntity.created(URI.create("/api/events/" + event.id())).body(event);
    }

    @GetMapping
    public List<EventResponse> list() {
        return eventService.listEvents();
    }

    @GetMapping("/{id}")
    public EventResponse get(@PathVariable Long id) {
        return eventService.getEvent(id);
    }

    @GetMapping("/{id}/seat-map")
    public SeatMapResponse seatMap(@PathVariable Long id) {
        return eventService.getSeatMap(id);
    }

    @PostMapping("/{id}/holds")
    public ResponseEntity<HoldResponse> hold(@PathVariable Long id, @Valid @RequestBody CreateHoldRequest request) {
        HoldResponse hold = holdService.createHold(id, request);
        return ResponseEntity.created(URI.create("/api/holds/" + hold.id())).body(hold);
    }
}
