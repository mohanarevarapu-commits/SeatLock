package com.seatlock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * @param customerRef  opaque caller-chosen token (e.g. a checkout session id); must not contain personal data
 * @param eventSeatIds ids from the event's seat map
 */
public record CreateHoldRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9_-]+") String customerRef,
        @NotEmpty Set<@NotNull Long> eventSeatIds) {
}
