package com.seatlock.controller;

import com.seatlock.dto.AccountBookingsResponse;
import com.seatlock.dto.AccountResponse;
import com.seatlock.dto.UpdateProfileRequest;
import com.seatlock.security.AccountPrincipal;
import com.seatlock.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in buyer's own data. Every endpoint here needs a session; without one it answers 401. */
@RestController
@Tag(name = "My account", description = "Profile and bookings of the signed-in buyer")
@RequestMapping("/api/account")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Operation(summary = "The signed-in buyer's profile")
    @GetMapping
    public AccountResponse get(@AuthenticationPrincipal AccountPrincipal buyer) {
        return accountService.getAccount(buyer.id());
    }

    @Operation(summary = "Change name and phone number")
    @PutMapping
    public AccountResponse update(@AuthenticationPrincipal AccountPrincipal buyer,
                                  @Valid @RequestBody UpdateProfileRequest body) {
        return accountService.updateProfile(buyer.id(), body);
    }

    @Operation(summary = "Bookings for shows still to come, and for past shows")
    @GetMapping("/bookings")
    public AccountBookingsResponse bookings(@AuthenticationPrincipal AccountPrincipal buyer) {
        return accountService.getBookings(buyer.id());
    }
}
