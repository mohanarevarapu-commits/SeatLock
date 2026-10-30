# SeatLock

A concert ticket reservation backend built for heavy concurrent demand. Customers place a
temporary **hold** on seats (10 minutes by default), then **confirm** it into a booking.
SeatLock guarantees a seat is never held or sold to two customers at once, even when many
requests arrive at the same moment.

All venues, events and customers in this project are fictional. Customers are identified
only by an opaque `customerRef` token, never by personal data.

## Tech stack

- Java 17, Spring Boot 3.5 (Web, Data JPA, Validation)
- H2 (in-memory, MySQL mode) for development and tests; MySQL 8 for a real database
- JUnit 5, AssertJ, Mockito, MockMvc
- Maven (via the included `./mvnw` wrapper, so no local Maven install is needed)

## Getting started

```bash
./mvnw clean test                                            # run all tests
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo       # run with a seeded demo venue
```

The API listens on `http://localhost:8080`. The `demo` profile creates "Riverside Arena"
(5,000 seats in 3 sections) and two upcoming events.

### Using MySQL instead of H2

```bash
docker compose up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=mysql,demo
```

Connection settings can be overridden with `SEATLOCK_DB_URL`, `SEATLOCK_DB_USER` and
`SEATLOCK_DB_PASSWORD`.

## API

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/venues` | Create a venue with sections laid out as rows x seats |
| `GET` | `/api/venues`, `/api/venues/{id}` | List or fetch venues |
| `POST` | `/api/events` | Create an event at a venue (creates per-event seat inventory) |
| `GET` | `/api/events`, `/api/events/{id}` | List or fetch events |
| `GET` | `/api/events/{id}/seat-map` | Seats grouped by section with live status |
| `POST` | `/api/events/{id}/holds` | Hold seats: `{"customerRef": "...", "eventSeatIds": [..]}` |
| `GET` | `/api/holds/{id}` | Fetch a hold |
| `DELETE` | `/api/holds/{id}` | Release a hold early |
| `POST` | `/api/holds/{id}/confirm` | Turn a hold into a booking |
| `GET` | `/api/bookings/{reference}` | Fetch a booking, e.g. `SL-7KQ2MX` |

Errors use RFC 9457 problem responses:

| Status | When |
|---|---|
| `400` | Validation failed, too many seats, or the event has started |
| `404` | Unknown venue, event, hold, seat or booking |
| `409` | Seats already taken (`eventSeatIds` lists which), hold no longer active, or a concurrent update (retry) |
| `410` | Confirming a hold after it expired |

### Example

```bash
curl -s localhost:8080/api/events/1/seat-map | jq '.sections[0].seats[:3]'

curl -s -X POST localhost:8080/api/events/1/holds \
  -H 'Content-Type: application/json' \
  -d '{"customerRef": "checkout-123", "eventSeatIds": [1, 2]}'

curl -s -X POST localhost:8080/api/holds/1/confirm
```

## How concurrency is handled

- **Row locks in a fixed order.** Every write locks the affected `event_seats` rows with
  `SELECT ... FOR UPDATE`, sorted by id. Two requests for overlapping seats queue up instead
  of deadlocking.
- **Hold before seats.** Release, confirm and expiry lock the hold row first, then its seats.
  Creating a hold never locks other holds, so the lock order is always the same.
- **Optimistic versioning as a backstop.** `EventSeat` has a `@Version` column. If a stale
  write ever slips through, it fails with `409 Concurrent update` instead of overwriting.
- **Lazy expiry.** A seat whose hold has lapsed counts as available immediately, even
  before the background sweeper (every 30 s) marks the hold `EXPIRED`. When the sweeper does
  run, it only frees seats still owned by that hold, so it never undoes a newer hold.
- **Holds never outlive the show.** A hold's expiry is capped at the event start time.

`ConcurrentHoldIntegrationTest` fires 16 simultaneous requests at the same and at
overlapping seats and checks that each seat goes to exactly one customer.

## Project layout

```
src/main/java/com/seatlock
├── config/        Clock, hold settings, demo data seeder
├── controller/    REST endpoints
├── dto/           Request and response records
├── exception/     Domain exceptions and the HTTP error mapping
├── model/         JPA entities: Venue, Section, Seat, Event, EventSeat, Hold, Booking
├── repository/    Spring Data repositories, including the locking queries
└── service/       Business logic: holds, bookings, expiry
src/test/java/com/seatlock
├── controller/    MockMvc tests and an end-to-end HTTP flow
├── model/         Unit tests for entity rules
├── service/       Integration tests, including multi-threaded race tests
└── support/       Test base class and a controllable clock
```

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `seatlock.holds.ttl` | `PT10M` | How long a hold lasts |
| `seatlock.holds.max-seats-per-hold` | `8` | Seat limit per hold |
| `seatlock.holds.sweep-interval` | `PT30S` | Delay between expiry sweeps |
| `seatlock.holds.sweep-batch-size` | `200` | Holds expired per sweep |
| `seatlock.holds.sweeper-enabled` | `true` | Turn the background sweeper off (tests do this) |

## License

Apache License 2.0. See [LICENSE](LICENSE).
