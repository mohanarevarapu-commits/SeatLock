package com.seatlock.controller;

import com.seatlock.dto.CreateVenueRequest;
import com.seatlock.dto.VenueResponse;
import com.seatlock.service.VenueService;
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
@RequestMapping("/api/venues")
public class VenueController {

    private final VenueService venueService;

    public VenueController(VenueService venueService) {
        this.venueService = venueService;
    }

    @PostMapping
    public ResponseEntity<VenueResponse> create(@Valid @RequestBody CreateVenueRequest request) {
        VenueResponse venue = venueService.createVenue(request);
        return ResponseEntity.created(URI.create("/api/venues/" + venue.id())).body(venue);
    }

    @GetMapping
    public List<VenueResponse> list() {
        return venueService.listVenues();
    }

    @GetMapping("/{id}")
    public VenueResponse get(@PathVariable Long id) {
        return venueService.getVenue(id);
    }
}
