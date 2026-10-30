package com.seatlock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** @param token the token from the emailed reset link */
public record ResetPasswordRequest(@NotBlank String token, @NotNull @Size(min = 8, max = 72) String password) {
}
