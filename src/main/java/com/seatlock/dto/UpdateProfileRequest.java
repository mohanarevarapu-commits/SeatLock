package com.seatlock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The email is the sign-in name and cannot be changed here. */
public record UpdateProfileRequest(
        @NotBlank @Size(max = 100) String name,
        @Pattern(regexp = AccountRules.PHONE, message = AccountRules.PHONE_MESSAGE) String phone) {
}
