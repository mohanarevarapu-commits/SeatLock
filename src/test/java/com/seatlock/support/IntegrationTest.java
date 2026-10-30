package com.seatlock.support;

import com.seatlock.dto.CreateEventRequest;
import com.seatlock.dto.CreateVenueRequest;
import com.seatlock.dto.CreateVenueRequest.SectionRequest;
import com.seatlock.dto.EventResponse;
import com.seatlock.dto.SeatMapResponse;
import com.seatlock.dto.VenueResponse;
import com.seatlock.service.EventService;
import com.seatlock.service.VenueService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for tests that run against the full application and an in-memory database.
 * Tests are deliberately not {@code @Transactional}: concurrency tests need real commits.
 * Each test creates its own venue and event, so tests stay independent without cleanup.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTest.ClockOverride.class)
public abstract class IntegrationTest {

    protected static final Instant START = Instant.parse("2030-01-01T18:00:00Z");

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected VenueService venueService;

    @Autowired
    protected EventService eventService;

    @BeforeEach
    void resetClock() {
        clock.setInstant(START);
    }

    /** Creates a small venue (one 2x5 section at $50) and an event 30 days from now. */
    protected EventResponse createEvent() {
        VenueResponse venue = venueService.createVenue(new CreateVenueRequest("Test Hall", "Testville",
                List.of(new SectionRequest("Main", 2, 5, 5_000))));
        return eventService.createEvent(new CreateEventRequest(venue.id(), "Test Show",
                clock.instant().plus(Duration.ofDays(30))));
    }

    protected List<Long> seatIds(Long eventId) {
        return eventService.getSeatMap(eventId).sections().stream()
                .flatMap(section -> section.seats().stream())
                .map(SeatMapResponse.SeatView::eventSeatId)
                .toList();
    }

    @TestConfiguration
    static class ClockOverride {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(START);
        }
    }
}
