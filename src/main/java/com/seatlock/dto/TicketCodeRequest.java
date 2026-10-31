package com.seatlock.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Find my tickets, step 1: the email and phone number the guest gave when paying. */
public record TicketCodeRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 20) @Pattern(regexp = AccountRules.PHONE, message = AccountRules.PHONE_MESSAGE) String phone) {
}
