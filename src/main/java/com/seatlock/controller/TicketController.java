package com.seatlock.controller;

import com.seatlock.dto.TicketCodeRequest;
import com.seatlock.dto.TicketLookupRequest;
import com.seatlock.dto.TicketResponse;
import com.seatlock.service.TicketAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Tickets", description = "Find my tickets: guests see their tickets with a code sent by email")
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketAccessService ticketAccessService;

    public TicketController(TicketAccessService ticketAccessService) {
        this.ticketAccessService = ticketAccessService;
    }

    @Operation(summary = "Email a 6-digit code to a guest who booked with this email and phone number. "
            + "Always 202, whether or not there are bookings for them")
    @ApiResponse(responseCode = "202")
    @PostMapping("/code")
    public ResponseEntity<Void> sendCode(@Valid @RequestBody TicketCodeRequest request) {
        ticketAccessService.sendCode(request);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "The guest's tickets, for the code from the email; 400 if the code is wrong, used or expired")
    @PostMapping("/lookup")
    public List<TicketResponse> lookUp(@Valid @RequestBody TicketLookupRequest request) {
        return ticketAccessService.lookUp(request);
    }
}
