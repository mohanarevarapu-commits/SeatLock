package com.seatlock.controller;

import com.seatlock.dto.BookingResponse;
import com.seatlock.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Bookings", description = "Confirmed bookings")
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Operation(summary = "Fetch a booking by reference, e.g. SL-7KQ2MX")
    @GetMapping("/{reference}")
    public BookingResponse get(@PathVariable String reference) {
        return bookingService.getBooking(reference);
    }

    @Operation(summary = "The ticket's QR code as a PNG image; it holds the booking reference")
    @GetMapping(value = "/{reference}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public byte[] qrCode(@PathVariable String reference) {
        return bookingService.ticketQrCode(reference);
    }
}
