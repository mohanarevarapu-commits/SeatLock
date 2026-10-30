package com.seatlock.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.seatlock.dto.CreateEventRequest;
import com.seatlock.dto.CreateHoldRequest;
import com.seatlock.dto.CreateVenueRequest;
import com.seatlock.dto.CreateVenueRequest.SectionRequest;
import com.seatlock.dto.EventResponse;
import com.seatlock.dto.HoldResponse;
import com.seatlock.dto.VenueResponse;
import com.seatlock.support.IntegrationTest;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The show list carries seat totals and a "from" price, which the home page uses for Selling fast. */
class EventListingIntegrationTest extends IntegrationTest {

    @Autowired
    private HoldService holdService;

    @Autowired
    private BookingService bookingService;

    @Test
    void newEventHasEverySeatOnSaleFromTheCheapestSection() {
        VenueResponse venue = venueService.createVenue(new CreateVenueRequest("Two Tier Hall", "Testville", List.of(
                new SectionRequest("Stalls", 2, 5, 8_000),
                new SectionRequest("Balcony", 1, 4, 3_500))));

        EventResponse event = eventService.createEvent(new CreateEventRequest(venue.id(), "Fresh Show",
                clock.instant().plus(Duration.ofDays(5))));

        assertThat(event.totalSeats()).isEqualTo(14);
        assertThat(event.availableSeats()).isEqualTo(14);
        assertThat(event.fromPriceCents()).isEqualTo(3_500);
    }

    @Test
    void bookedAndHeldSeatsAreTakenButALapsedHoldIsOnSaleAgain() {
        EventResponse event = createEvent();   // 10 seats
        List<Long> seats = seatIds(event.id());
        HoldResponse booked = holdService.createHold(event.id(), hold(seats.get(0), seats.get(1)));
        bookingService.confirmHold(booked.id());
        holdService.createHold(event.id(), hold(seats.get(2)));

        assertThat(eventService.getEvent(event.id()).availableSeats()).isEqualTo(7);

        // The hold lapses; the sweeper has not run, but the seat already counts as on sale.
        clock.advance(Duration.ofMinutes(10));
        EventResponse listed = eventService.listEvents().stream()
                .filter(e -> e.id().equals(event.id())).findFirst().orElseThrow();
        assertThat(listed.totalSeats()).isEqualTo(10);
        assertThat(listed.availableSeats()).isEqualTo(8);
    }

    @Test
    void listIsSoonestFirst() {
        EventResponse later = createEvent();
        clock.advance(Duration.ofDays(-3));
        EventResponse sooner = createEvent();

        List<Long> ids = eventService.listEvents().stream().map(EventResponse::id).toList();

        assertThat(ids.indexOf(sooner.id())).isLessThan(ids.indexOf(later.id()));
    }

    private static CreateHoldRequest hold(Long... seatIds) {
        return new CreateHoldRequest("listing-test", Set.of(seatIds));
    }
}
