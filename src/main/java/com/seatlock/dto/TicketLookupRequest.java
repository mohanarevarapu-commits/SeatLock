package com.seatlock.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Find my tickets, step 2: the same email and phone, and the 6-digit code that was emailed. */
public record TicketLookupRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 20) @Pattern(regexp = AccountRules.PHONE, message = AccountRules.PHONE_MESSAGE) String phone,
        @NotBlank @Pattern(regexp = "\\s*\\d{6}\\s*", message = "must be the 6-digit code from the email") String code) {
}
