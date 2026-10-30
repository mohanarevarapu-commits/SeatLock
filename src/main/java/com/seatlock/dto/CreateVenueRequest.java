package com.seatlock.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateVenueRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 120) String city,
        @NotEmpty @Size(max = 50) List<@Valid SectionRequest> sections) {

    public record SectionRequest(
            @NotBlank @Size(max = 60) String name,
            @Min(1) @Max(100) int rows,
            @Min(1) @Max(100) int seatsPerRow,
            @PositiveOrZero long priceCents) {
    }
}
