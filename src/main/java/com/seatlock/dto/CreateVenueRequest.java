package com.seatlock.dto;

import com.seatlock.model.MapZone;
import com.seatlock.model.VenueType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * @param type         kind of venue; defaults to {@code ARENA}
 * @param stageZone    where the stage sits on the 3x3 map grid; defaults to {@code NORTH} (an end stage)
 * @param stageLabel   text drawn on the stage, e.g. "STAGE" or "PITCH"; defaults to "STAGE"
 */
public record CreateVenueRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 120) String city,
        VenueType type,
        MapZone stageZone,
        @Size(max = 30) String stageLabel,
        @NotEmpty @Size(max = 50) List<@Valid SectionRequest> sections) {

    public CreateVenueRequest(String name, String city, List<SectionRequest> sections) {
        this(name, city, null, null, null, sections);
    }

    /** @param zone where the section sits on the map; defaults to across from the stage */
    public record SectionRequest(
            @NotBlank @Size(max = 60) String name,
            @Min(1) @Max(100) int rows,
            @Min(1) @Max(100) int seatsPerRow,
            @PositiveOrZero long priceCents,
            MapZone zone) {

        public SectionRequest(String name, int rows, int seatsPerRow, long priceCents) {
            this(name, rows, seatsPerRow, priceCents, null);
        }
    }
}
