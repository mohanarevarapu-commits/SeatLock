package com.seatlock.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateEventRequest(
        @NotNull Long venueId,
        @NotBlank @Size(max = 160) String name,
        @NotNull @Future Instant startsAt) {
}
