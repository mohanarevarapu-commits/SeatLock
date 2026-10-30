package com.seatlock.controller;

import com.seatlock.dto.BookingResponse;
import com.seatlock.dto.HoldResponse;
import com.seatlock.service.BookingService;
import com.seatlock.service.HoldService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Holds", description = "Temporary seat holds")
@RequestMapping("/api/holds")
public class HoldController {

    private final HoldService holdService;
    private final BookingService bookingService;

    public HoldController(HoldService holdService, BookingService bookingService) {
        this.holdService = holdService;
        this.bookingService = bookingService;
    }

    @Operation(summary = "Fetch a hold")
    @GetMapping("/{id}")
    public HoldResponse get(@PathVariable Long id) {
        return holdService.getHold(id);
    }

    @Operation(summary = "Release a hold early")
    @DeleteMapping("/{id}")
    public HoldResponse release(@PathVariable Long id) {
        return holdService.releaseHold(id);
    }

    @Operation(summary = "Turn an active hold into a booking; 410 if it expired")
    @ApiResponse(responseCode = "201")
    @PostMapping("/{id}/confirm")
    public ResponseEntity<BookingResponse> confirm(@PathVariable Long id) {
        BookingResponse booking = bookingService.confirmHold(id);
        return ResponseEntity.created(URI.create("/api/bookings/" + booking.reference())).body(booking);
    }
}
