package com.seatlock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.seatlock.dto.CreateEventRequest;
import com.seatlock.dto.CreateVenueRequest;
import com.seatlock.dto.CreateVenueRequest.SectionRequest;
import com.seatlock.dto.EventResponse;
import com.seatlock.dto.SeatMapResponse;
import com.seatlock.dto.SeatMapResponse.SectionMap;
import com.seatlock.dto.VenueResponse;
import com.seatlock.model.MapZone;
import com.seatlock.model.VenueType;
import com.seatlock.support.IntegrationTest;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Venue layouts (type, stage position, section zones) are stored and reach the seat map. */
class VenueLayoutIntegrationTest extends IntegrationTest {

    @Test
    void venueWithoutLayoutIsAnArenaWithAnEndStage() {
        VenueResponse venue = venueService.createVenue(new CreateVenueRequest("Plain Hall", "Testville",
                List.of(new SectionRequest("Main", 2, 3, 5_000))));

        assertThat(venue.type()).isEqualTo(VenueType.ARENA);
        assertThat(venue.stageZone()).isEqualTo(MapZone.NORTH);
        assertThat(venue.stageLabel()).isEqualTo("STAGE");
        assertThat(venue.sections()).singleElement().extracting(VenueResponse.SectionSummary::zone)
                .isEqualTo(MapZone.SOUTH);
    }

    @Test
    void stageInTheRoundLayoutReachesTheSeatMap() {
        VenueResponse venue = venueService.createVenue(new CreateVenueRequest("Round Dome", "Testville",
                VenueType.ARENA, MapZone.CENTER, "STAGE IN THE ROUND", List.of(
                        new SectionRequest("North", 2, 4, 6_000, MapZone.NORTH),
                        new SectionRequest("West", 2, 3, 5_000, MapZone.WEST),
                        new SectionRequest("Unplaced", 1, 2, 4_000))));
        EventResponse event = eventService.createEvent(new CreateEventRequest(venue.id(), "Round Show",
                clock.instant().plus(Duration.ofDays(30))));

        SeatMapResponse map = eventService.getSeatMap(event.id());

        assertThat(event.venueType()).isEqualTo(VenueType.ARENA);
        assertThat(event.venueCity()).isEqualTo("Testville");
        assertThat(map.stageZone()).isEqualTo(MapZone.CENTER);
        assertThat(map.stageLabel()).isEqualTo("STAGE IN THE ROUND");
        assertThat(map.sections()).extracting(SectionMap::name, SectionMap::zone).containsExactly(
                tuple("North", MapZone.NORTH),
                tuple("West", MapZone.WEST),
                tuple("Unplaced", MapZone.SOUTH));
    }
}
