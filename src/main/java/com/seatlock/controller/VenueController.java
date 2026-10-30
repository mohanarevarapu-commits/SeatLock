package com.seatlock.controller;

import com.seatlock.dto.CreateVenueRequest;
import com.seatlock.dto.VenueResponse;
import com.seatlock.service.VenueService;
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
@Tag(name = "Venues", description = "Venues and their seating layout")
@RequestMapping("/api/venues")
public class VenueController {

    private final VenueService venueService;

    public VenueController(VenueService venueService) {
        this.venueService = venueService;
    }

    @Operation(summary = "Create a venue with sections laid out as rows x seats")
    @ApiResponse(responseCode = "201")
    @PostMapping
    public ResponseEntity<VenueResponse> create(@Valid @RequestBody CreateVenueRequest request) {
        VenueResponse venue = venueService.createVenue(request);
        return ResponseEntity.created(URI.create("/api/venues/" + venue.id())).body(venue);
    }

    @Operation(summary = "List venues")
    @GetMapping
    public List<VenueResponse> list() {
        return venueService.listVenues();
    }

    @Operation(summary = "Fetch a venue")
    @GetMapping("/{id}")
    public VenueResponse get(@PathVariable Long id) {
        return venueService.getVenue(id);
    }
}
