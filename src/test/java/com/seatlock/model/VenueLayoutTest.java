package com.seatlock.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seatlock.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class VenueLayoutTest {

    @ParameterizedTest
    @CsvSource({
            "NORTH, SOUTH", "SOUTH, NORTH", "WEST, EAST", "EAST, WEST",
            "NORTH_WEST, SOUTH_EAST", "SOUTH_EAST, NORTH_WEST", "CENTER, SOUTH"})
    void sectionsFaceTheStageFromAcrossTheMap(MapZone stage, MapZone expected) {
        assertThat(stage.facing()).isEqualTo(expected);
    }

    @Test
    void sectionWithoutZoneGoesAcrossFromTheStage() {
        Venue venue = new Venue("Side Hall", "City", VenueType.FUNCTION_HALL, MapZone.EAST, "STAGE");

        assertThat(venue.addSection("Floor", 2, 2, 1_000).getZone()).isEqualTo(MapZone.WEST);
    }

    @Test
    void sectionCannotSitWhereTheStageIs() {
        Venue venue = new Venue("Dome", "City", VenueType.ARENA, MapZone.CENTER, "STAGE IN THE ROUND");

        assertThatThrownBy(() -> venue.addSection("Floor", 2, 2, 1_000, MapZone.CENTER))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("where the stage is");
    }
}
