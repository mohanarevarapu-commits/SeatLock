package com.seatlock.config;

import static com.seatlock.model.MapZone.CENTER;
import static com.seatlock.model.MapZone.EAST;
import static com.seatlock.model.MapZone.NORTH;
import static com.seatlock.model.MapZone.NORTH_EAST;
import static com.seatlock.model.MapZone.NORTH_WEST;
import static com.seatlock.model.MapZone.SOUTH;
import static com.seatlock.model.MapZone.SOUTH_EAST;
import static com.seatlock.model.MapZone.SOUTH_WEST;
import static com.seatlock.model.MapZone.WEST;

import com.seatlock.dto.CreateEventRequest;
import com.seatlock.dto.CreateHoldRequest;
import com.seatlock.dto.CreateVenueRequest;
import com.seatlock.dto.CreateVenueRequest.SectionRequest;
import com.seatlock.dto.HoldResponse;
import com.seatlock.dto.SeatMapResponse.SeatView;
import com.seatlock.dto.VenueResponse;
import com.seatlock.model.Account;
import com.seatlock.model.MapZone;
import com.seatlock.model.SeatStatus;
import com.seatlock.model.VenueType;
import com.seatlock.repository.AccountRepository;
import com.seatlock.service.BookingService;
import com.seatlock.service.EventService;
import com.seatlock.service.HoldService;
import com.seatlock.service.VenueService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds fictional venues of every kind, each with its own stage position, and upcoming shows at
 * them when running with the {@code demo} profile. Riverside Arena and its two shows come first, so
 * they keep ids 1 and 1-2, which the load test uses. A few shows get guest sales so the home page has
 * shows that are selling fast.
 */
@Component
@Profile("demo")
public class DemoDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    /** Demo sign-in, shown in the README. The address is on example.com, which can never receive mail. */
    static final String DEMO_EMAIL = "demo@example.com";
    static final String DEMO_PASSWORD = "seatlock-demo";

    /** Seats per seeded guest sale; must not exceed seatlock.holds.max-seats-per-hold. */
    private static final int SEATS_PER_SALE = 4;

    private final VenueService venueService;
    private final EventService eventService;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final HoldService holdService;
    private final BookingService bookingService;
    private final Clock clock;
    private int events;

    public DemoDataLoader(VenueService venueService, EventService eventService, AccountRepository accountRepository,
                          PasswordEncoder passwordEncoder, HoldService holdService, BookingService bookingService,
                          Clock clock) {
        this.venueService = venueService;
        this.eventService = eventService;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.holdService = holdService;
        this.bookingService = bookingService;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        // End stage, seats in front: the classic arena concert.
        VenueResponse riverside = venue("Riverside Arena", "Example City", "1 Riverside Way",
                VenueType.ARENA, NORTH, "STAGE", List.of(
                section("Floor", SOUTH, 20, 25, 12_000),
                section("Lower Bowl", SOUTH, 30, 50, 8_500),
                section("Upper Bowl", SOUTH, 40, 75, 4_500)));
        event(riverside, "The Placeholder Tour", 30, 19, 30);
        event(riverside, "Synthwave Sample Night", 45, 20, 0);

        // Stage in the round: seats on every side.
        VenueResponse dome = venue("Meridian Dome", "Northvale", "200 Dome Boulevard",
                VenueType.ARENA, CENTER, "STAGE IN THE ROUND", List.of(
                section("North Stand", NORTH, 10, 30, 9_500),
                section("South Stand", SOUTH, 10, 30, 9_500),
                section("West Stand", WEST, 10, 20, 8_000),
                section("East Stand", EAST, 10, 20, 8_000),
                section("North-West Corner", NORTH_WEST, 6, 8, 5_500),
                section("North-East Corner", NORTH_EAST, 6, 8, 5_500),
                section("South-West Corner", SOUTH_WEST, 6, 8, 5_500),
                section("South-East Corner", SOUTH_EAST, 6, 8, 5_500)));
        event(dome, "Echo Circle Live", 12, 20, 0);
        event(dome, "Neon Orbit 360", 60, 19, 30);

        // Stadium concert: main stage at one end, seats on the pitch and stands around it.
        VenueResponse stadium = venue("Harbourline Stadium", "Port Ellis", "Harbour Road",
                VenueType.STADIUM, NORTH, "MAIN STAGE", List.of(
                section("Pitch Floor", CENTER, 20, 40, 15_000),
                section("South Stand", SOUTH, 25, 60, 7_500),
                section("West Stand", WEST, 25, 45, 6_500),
                section("East Stand", EAST, 25, 45, 6_500),
                section("South-West Corner", SOUTH_WEST, 15, 15, 4_000),
                section("South-East Corner", SOUTH_EAST, 15, 15, 4_000),
                section("North-West Terrace", NORTH_WEST, 10, 12, 5_500),
                section("North-East Terrace", NORTH_EAST, 10, 12, 5_500)));
        event(stadium, "Summer Stadium Fest", 20, 18, 0);
        event(stadium, "Big Sky Anthems", 75, 19, 0);

        // Cricket ground: stage on the square, stands all the way round the boundary.
        VenueResponse cricket = venue("Oakmoor Cricket Ground", "Westbrook", "Oakmoor Lane",
                VenueType.GROUND, CENTER, "PITCH STAGE", List.of(
                section("Pavilion End", NORTH, 12, 40, 9_000),
                section("Nursery End", SOUTH, 12, 40, 7_000),
                section("Members' Stand", WEST, 12, 30, 11_000),
                section("East Mound", EAST, 12, 30, 6_000),
                section("North-West Stand", NORTH_WEST, 8, 10, 5_000),
                section("North-East Stand", NORTH_EAST, 8, 10, 5_000),
                section("South-West Stand", SOUTH_WEST, 8, 10, 5_000),
                section("South-East Stand", SOUTH_EAST, 8, 10, 5_000)));
        event(cricket, "Boundary Beats Open Air", 25, 17, 30);

        // Open-air ground with the stage on the west side.
        VenueResponse fairground = venue("Old Mill Fairground", "Lakeside", "Mill Field Road",
                VenueType.GROUND, WEST, "SIDE STAGE", List.of(
                section("Front Lawn", CENTER, 20, 30, 6_000),
                section("Back Lawn", EAST, 20, 30, 4_000),
                section("North Bank", NORTH_WEST, 6, 15, 5_000),
                section("South Bank", SOUTH_WEST, 6, 15, 5_000)));
        event(fairground, "Fairground Folk Weekend", 18, 16, 0);

        // Horseshoe opera house: stalls in front of the stage, boxes along both sides, tiers behind.
        List<SectionRequest> crescent = new ArrayList<>(List.of(
                section("Stalls", CENTER, 16, 28, 15_000),
                section("Dress Circle", SOUTH, 8, 30, 18_000),
                section("Grand Tier", SOUTH, 6, 32, 11_000),
                section("Gallery", SOUTH, 6, 34, 4_500),
                section("Stage Box Left", NORTH_WEST, 2, 2, 30_000),
                section("Stage Box Right", NORTH_EAST, 2, 2, 30_000)));
        crescent.addAll(boxes('A', 4, WEST, 2, 3, 25_000));
        crescent.addAll(boxes('E', 4, EAST, 2, 3, 25_000));
        VenueResponse crescentHouse = venue("Royal Crescent Opera House", "Example City", "12 Crescent Square",
                VenueType.OPERA_HOUSE, NORTH,
                "STAGE · ORCHESTRA PIT", crescent);
        event(crescentHouse, "The Glass Garden: An Opera in Three Acts", 35, 19, 0);
        event(crescentHouse, "Midwinter Gala Night", 90, 19, 30);

        // A smaller opera house with the stage at the south end.
        List<SectionRequest> lumen = new ArrayList<>(List.of(
                section("Stalls", CENTER, 14, 24, 13_000),
                section("Royal Tier", NORTH, 6, 26, 16_000),
                section("Upper Gallery", NORTH, 6, 30, 5_000)));
        lumen.addAll(boxes('1', 3, WEST, 2, 4, 22_000));
        lumen.addAll(boxes('4', 3, EAST, 2, 4, 22_000));
        VenueResponse lumenHouse = venue("Teatro Lumen", "Port Ellis", "8 Lumen Quay",
                VenueType.OPERA_HOUSE, SOUTH, "STAGE", lumen);
        event(lumenHouse, "Requiem for a Paper Moon", 40, 19, 30);

        // Thrust stage: the audience sits on three sides, backstage is behind it.
        VenueResponse lantern = venue("Lantern Street Theatre", "Northvale", "34 Lantern Street",
                VenueType.THEATRE, CENTER, "THRUST STAGE", List.of(
                section("Front Stalls", SOUTH, 10, 20, 7_500),
                section("Left Side", WEST, 8, 12, 6_500),
                section("Right Side", EAST, 8, 12, 6_500),
                section("Left Corner", SOUTH_WEST, 6, 6, 5_000),
                section("Right Corner", SOUTH_EAST, 6, 6, 5_000)));
        event(lantern, "Tidewater: A New Play", 15, 19, 45);

        // Concert hall with choir seats behind the orchestra and terraces around it.
        VenueResponse lakeview = venue("Lakeview Concert Hall", "Lakeside", "2 Lakeview Promenade",
                VenueType.CONCERT_HALL, CENTER, "ORCHESTRA", List.of(
                section("Choir Seats", NORTH, 4, 24, 4_000),
                section("Stalls", SOUTH, 18, 30, 9_000),
                section("West Terrace", WEST, 8, 10, 7_000),
                section("East Terrace", EAST, 8, 10, 7_000),
                section("South-West Terrace", SOUTH_WEST, 6, 10, 6_000),
                section("South-East Terrace", SOUTH_EAST, 6, 10, 6_000)));
        event(lakeview, "Strings Under Glass", 28, 19, 30);

        // Open-air amphitheatre fanning out from the stage.
        VenueResponse bowl = venue("Sunset Bowl Amphitheatre", "Westbrook", "Sunset Hill Park",
                VenueType.AMPHITHEATRE, NORTH, "STAGE", List.of(
                section("Lower Arc", SOUTH, 15, 40, 6_500),
                section("Upper Arc", SOUTH, 12, 50, 3_500),
                section("West Fan", WEST, 10, 12, 5_000),
                section("East Fan", EAST, 10, 12, 5_000),
                section("West Lawn Wing", SOUTH_WEST, 8, 12, 2_500),
                section("East Lawn Wing", SOUTH_EAST, 8, 12, 2_500)));
        event(bowl, "Starlight Sessions", 22, 20, 0);

        // Function hall with the stage and dance floor along the east wall.
        VenueResponse pearl = venue("Grand Pearl Banquet Hall", "Example City", "77 Pearl Avenue",
                VenueType.FUNCTION_HALL, EAST,
                "STAGE & DANCE FLOOR", List.of(
                        section("Front Floor", CENTER, 10, 16, 9_000),
                        section("Rear Floor", WEST, 10, 16, 6_000),
                        section("North Gallery", NORTH, 3, 12, 5_000),
                        section("South Gallery", SOUTH, 3, 12, 5_000)));
        event(pearl, "Golden Hour Gala Dinner", 16, 19, 0);

        // Small club with the stage in a corner.
        VenueResponse velvet = venue("The Velvet Room", "Lakeside", "9 Velvet Lane",
                VenueType.CLUB, SOUTH_WEST, "CORNER STAGE", List.of(
                section("Front Tables", CENTER, 8, 12, 4_500),
                section("Side Bench", WEST, 8, 4, 3_000),
                section("Stage Side", SOUTH, 4, 8, 4_000),
                section("Bar Rail", NORTH, 3, 14, 3_000),
                section("Lounge", EAST, 8, 8, 3_500),
                section("Booths", NORTH_EAST, 4, 6, 6_000)));
        event(velvet, "Late Night Jazz Sessions", 8, 22, 0);

        // Two shows later today, so the home page has something under Today.
        eventLaterToday(lantern, "Open Mic Night", 3);
        eventLaterToday(velvet, "Vinyl After Dark", 5);

        // And two tomorrow evening, for the home page's Tomorrow box.
        event(bowl, "Sunset Acoustic Sessions", 1, 19, 30);
        event(dome, "Neon Nights Live", 1, 20, 30);

        seedDemoAccount();
        seedTicketSales();
        log.info("Seeded {} demo venues, {} events and the demo account {}", venueService.listVenues().size(), events,
                DEMO_EMAIL);
    }

    /** A demo buyer with two bookings, so My bookings has something to show. Created directly, so no email is sent. */
    private void seedDemoAccount() {
        Account demo = accountRepository.save(new Account(DEMO_EMAIL, passwordEncoder.encode(DEMO_PASSWORD),
                "Demo Fan", "+44 20 7946 0000", clock.instant()));
        book(demo, 9, 2);   // The Glass Garden, Royal Crescent Opera House
        book(demo, 16, 4);  // Late Night Jazz Sessions, The Velvet Room
    }

    /** Guest sales on a few shows, so some are close to selling out and show under Selling fast. */
    private void seedTicketSales() {
        sell(16, 0.85);   // Late Night Jazz Sessions
        sell(12, 0.72);   // Tidewater: A New Play
        sell(15, 0.60);   // Golden Hour Gala Dinner
        sell(11, 0.45);   // Requiem for a Paper Moon
        sell(17, 0.55);   // Open Mic Night (today)
        sell(19, 0.50);   // Sunset Acoustic Sessions (tomorrow)
    }

    /** Books a share of the event's free seats, picked at random (fixed seed), in holds of the allowed size. */
    private void sell(long eventId, double share) {
        List<Long> free = new ArrayList<>(eventService.getSeatMap(eventId).sections().stream()
                .flatMap(section -> section.seats().stream())
                .filter(seat -> seat.status() == SeatStatus.AVAILABLE)
                .map(SeatView::eventSeatId)
                .toList());
        Collections.shuffle(free, new Random(eventId));
        List<Long> sold = free.subList(0, (int) (free.size() * share));
        for (int from = 0; from < sold.size(); from += SEATS_PER_SALE) {
            Set<Long> seatIds = Set.copyOf(sold.subList(from, Math.min(from + SEATS_PER_SALE, sold.size())));
            HoldResponse hold = holdService.createHold(eventId, new CreateHoldRequest("demo-crowd", seatIds), null);
            bookingService.confirmHold(hold.id(), null);
        }
    }

    private void book(Account account, long eventId, int seats) {
        Set<Long> seatIds = eventService.getSeatMap(eventId).sections().get(0).seats().stream()
                .limit(seats)
                .map(SeatView::eventSeatId)
                .collect(Collectors.toSet());
        HoldResponse hold = holdService.createHold(eventId, new CreateHoldRequest("demo", seatIds), account.getId());
        bookingService.confirmHold(hold.id(), account.getId());
    }

    /** @param address a fictional street address, shown on tickets */
    private VenueResponse venue(String name, String city, String address, VenueType type, MapZone stageZone,
                                String stageLabel, List<SectionRequest> sections) {
        return venueService.createVenue(new CreateVenueRequest(name, city, type, stageZone, stageLabel, sections,
                address));
    }

    private static SectionRequest section(String name, MapZone zone, int rows, int seatsPerRow, long priceCents) {
        return new SectionRequest(name, rows, seatsPerRow, priceCents, zone);
    }

    /** Opera boxes: {@code count} small sections named "Box A", "Box B", ... (or "Box 1", ...). */
    private static List<SectionRequest> boxes(char first, int count, MapZone zone, int rows, int seatsPerRow,
                                              long priceCents) {
        List<SectionRequest> boxes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            boxes.add(section("Box " + (char) (first + i), zone, rows, seatsPerRow, priceCents));
        }
        return boxes;
    }

    /** A show starting a few hours from now, on the hour. Late at night this falls on tomorrow. */
    private void eventLaterToday(VenueResponse venue, String name, int hoursFromNow) {
        Instant at = clock.instant().plus(Duration.ofHours(hoursFromNow)).truncatedTo(ChronoUnit.HOURS);
        eventService.createEvent(new CreateEventRequest(venue.id(), name, at));
        events++;
    }

    /** Shows start in the evening, in the time zone of the machine running the demo. */
    private void event(VenueResponse venue, String name, int daysAhead, int hour, int minute) {
        ZoneId zone = ZoneId.systemDefault();
        var startsAt = LocalDate.now(clock.withZone(zone)).plusDays(daysAhead).atTime(hour, minute).atZone(zone).toInstant();
        eventService.createEvent(new CreateEventRequest(venue.id(), name, startsAt));
        events++;
    }
}
