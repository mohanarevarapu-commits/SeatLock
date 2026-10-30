package com.seatlock.config;

import com.seatlock.dto.CreateEventRequest;
import com.seatlock.dto.CreateVenueRequest;
import com.seatlock.dto.CreateVenueRequest.SectionRequest;
import com.seatlock.dto.VenueResponse;
import com.seatlock.service.EventService;
import com.seatlock.service.VenueService;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Seeds a fictional venue and two events when running with the {@code demo} profile. */
@Component
@Profile("demo")
public class DemoDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    private final VenueService venueService;
    private final EventService eventService;
    private final Clock clock;

    public DemoDataLoader(VenueService venueService, EventService eventService, Clock clock) {
        this.venueService = venueService;
        this.eventService = eventService;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        VenueResponse arena = venueService.createVenue(new CreateVenueRequest("Riverside Arena", "Example City", List.of(
                new SectionRequest("Floor", 20, 25, 12_000),
                new SectionRequest("Lower Bowl", 30, 50, 8_500),
                new SectionRequest("Upper Bowl", 40, 75, 4_500))));

        eventService.createEvent(new CreateEventRequest(arena.id(), "The Placeholder Tour",
                clock.instant().plus(Duration.ofDays(30))));
        eventService.createEvent(new CreateEventRequest(arena.id(), "Synthwave Sample Night",
                clock.instant().plus(Duration.ofDays(45))));

        log.info("Seeded demo venue '{}' with {} seats and 2 events", arena.name(), arena.capacity());
    }
}
