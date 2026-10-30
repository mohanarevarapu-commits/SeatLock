package com.seatlock.controller;

import com.seatlock.dto.CreateEventRequest;
import com.seatlock.dto.CreateHoldRequest;
import com.seatlock.dto.EventResponse;
import com.seatlock.dto.HoldResponse;
import com.seatlock.dto.SeatMapResponse;
import com.seatlock.service.EventService;
import com.seatlock.service.HoldService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Events", description = "Events, seat maps and placing holds")
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;
    private final HoldService holdService;

    public EventController(EventService eventService, HoldService holdService) {
        this.eventService = eventService;
        this.holdService = holdService;
    }

    @Operation(summary = "Create an event at a venue, with its own seat inventory")
    @ApiResponse(responseCode = "201")
    @PostMapping
    public ResponseEntity<EventResponse> create(@Valid @RequestBody CreateEventRequest request) {
        EventResponse event = eventService.createEvent(request);
        return ResponseEntity.created(URI.create("/api/events/" + event.id())).body(event);
    }

    @Operation(summary = "List events")
    @GetMapping
    public List<EventResponse> list() {
        return eventService.listEvents();
    }

    @Operation(summary = "Fetch an event")
    @GetMapping("/{id}")
    public EventResponse get(@PathVariable Long id) {
        return eventService.getEvent(id);
    }

    @Operation(summary = "Seats grouped by section with live status")
    @GetMapping("/{id}/seat-map")
    public SeatMapResponse seatMap(@PathVariable Long id) {
        return eventService.getSeatMap(id);
    }

    @Operation(summary = "Hold seats for a customer; 409 lists any seats already taken")
    @ApiResponse(responseCode = "201")
    @PostMapping("/{id}/holds")
    public ResponseEntity<HoldResponse> hold(@PathVariable Long id, @Valid @RequestBody CreateHoldRequest request) {
        HoldResponse hold = holdService.createHold(id, request);
        return ResponseEntity.created(URI.create("/api/holds/" + hold.id())).body(hold);
    }
}
