package com.seatlock.controller;

import com.seatlock.dto.BookingResponse;
import com.seatlock.service.BookingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping("/{reference}")
    public BookingResponse get(@PathVariable String reference) {
        return bookingService.getBooking(reference);
    }
}
