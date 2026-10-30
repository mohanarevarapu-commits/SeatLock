package com.seatlock.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** @param password 8 to 72 characters (72 is the most BCrypt uses) */
public record RegisterRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull @Size(min = 8, max = 72) String password,
        @Pattern(regexp = AccountRules.PHONE, message = AccountRules.PHONE_MESSAGE) String phone) {
}
