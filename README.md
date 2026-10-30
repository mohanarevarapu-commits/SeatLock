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
- ActiveMQ Artemis (Spring JMS) for the queued checkout, embedded by default
- springdoc-openapi for the OpenAPI spec and Swagger UI
- JUnit 5, AssertJ, Mockito, MockMvc
- Locust (Python) for load testing
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

While the app is running, interactive docs are at
[`http://localhost:8080/swagger-ui.html`](http://localhost:8080/swagger-ui.html). See
[docs/API.md](docs/API.md) for request and response examples, and
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the data model and locking design.

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
| `POST` | `/api/events/{id}/purchase-requests` | Queue a hold request during peak traffic; returns `202` and a request id |
| `GET` | `/api/purchase-requests/{requestId}` | Outcome of a queued request: `QUEUED`, `HELD`, `REJECTED` or `FAILED` |

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
- **Rate limiting.** Each client IP may send 5 checkout requests per second (token bucket).
  Extra requests get `429 Too Many Requests` with `Retry-After` before reaching the database.

`ConcurrentHoldIntegrationTest` fires 200 simultaneous requests at the same seats, at
overlapping seats, and as a mix of holds, confirms and releases, and checks that each seat goes
to exactly one customer.

## Queued checkout

For peak traffic there is a second, asynchronous way to hold seats. `POST
/api/events/{id}/purchase-requests` puts the request on `ticket-purchase-queue` and answers
`202 Accepted` at once, without touching the database. A consumer then places the hold through the
normal locked path, one request at a time per event, and the client polls
`GET /api/purchase-requests/{requestId}` for the outcome.

- **Broker:** ActiveMQ Artemis, embedded in the app by default, so there is nothing extra to run.
  It keeps messages in memory; for production set `spring.artemis.mode=native` and point it at a
  standalone broker.
- **Retries:** a technical failure (for example a lock timeout) is retried after 0.5 s, 1 s and
  2 s. After 4 attempts the message moves to `DLQ.ticket-purchase-queue` and the request is
  marked `FAILED`. A business refusal (seat taken, unknown event) is `REJECTED` at once and never
  retried.
- **Exactly-once outcome:** each request id is processed once; a redelivered message whose
  request already has an outcome is skipped.
- The 10-minute hold timer starts when the request is processed, not when it was queued.

The direct `POST /api/events/{id}/holds` endpoint is unchanged and answers immediately.

## Load testing

`load-test/locustfile.py` is a [Locust](https://locust.io) script that simulates concert fans.
Each virtual user loads the seat map, picks seats it saw as available (mostly in the front
sections, where everyone competes), holds them, then checks out, or abandons and releases the
hold 15% of the time. About 1 in 10 orders goes through the queued checkout instead. When the run
ends, the script checks that no seat was sold twice and compares with the seat map.

All virtual users share one IP address, so turn the per-IP rate limit off for load tests:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo -Dspring-boot.run.arguments=--seatlock.rate-limit.enabled=false

pip install -r load-test/requirements.txt
locust --config load-test/locust.conf       # 500 users for 2 minutes, headless
```

`load-test/locust.conf` sets 500 users, a spawn rate of 50 per second and a 2-minute run, and
writes `load-test/results/report.html` plus CSV files. For the interactive web UI instead, run
`locust -f load-test/locustfile.py --host http://localhost:8080` and open http://localhost:8089.

Result of the default run on a laptop (Apple M5, in-memory H2, demo profile, Locust on the same
machine). The 500 fans sold out all 5,000 seats within the 2 minutes:

| Request | Count | Median | 95th pct | 99th pct | Failures |
|---|---|---|---|---|---|
| `GET` seat map (5,000 seats) | 22,222 | 12 ms | 41 ms | 200 ms | 0 |
| `POST` hold | 4,952 | 2 ms | 86 ms | 140 ms | 0 |
| `POST` checkout (confirm) | 2,273 | 2 ms | 86 ms | 140 ms | 0 |
| `POST` purchase request (queued) | 1,254 | 2 ms | 100 ms | 150 ms | 0 |
| **All requests** | **32,778** (273/s) | **10 ms** | **55 ms** | **160 ms** | **0** |

2,884 hold attempts lost a seat to another fan (`409`, counted as expected, not as failures).
Seats sold twice: 0, and the seat map showed exactly 5,000 booked. Throughput here is set by the
fans' think time (0.5 to 2 s between actions), not by the server; a no-pause stress run peaked at
about 3,300 requests/s.

Performance notes:

- The seat map is read as plain columns (`EventSeatRepository.findSeatMap`) instead of loading
  5,000 entities. This made it 3 to 4 times faster (19 ms to 6 ms per request).
- JSON responses are gzip-compressed for clients that accept it. A 5,000-seat map shrinks from
  391 KB to 42 KB.
- A larger database connection pool did not help: holds on the same seats queue on row locks
  either way, so the default pool of 10 is kept.

## Project layout

```
src/main/java/com/seatlock
├── config/        Clock, OpenAPI, hold/queue/rate-limit settings, demo data seeder
├── controller/    REST endpoints
├── dto/           Request and response records
├── exception/     Domain exceptions and the HTTP error mapping
├── model/         JPA entities: Venue, Section, Seat, Event, EventSeat, Hold, Booking, PurchaseRequest
├── queue/         Artemis queue setup, retry/dead-letter policy, purchase consumer
├── ratelimit/     Per-IP token bucket filter for checkout endpoints
├── repository/    Spring Data repositories, including the locking queries
└── service/       Business logic: holds, bookings, expiry, queued checkout
src/test/java/com/seatlock
├── controller/    MockMvc tests and an end-to-end HTTP flow
├── model/         Unit tests for entity rules
├── ratelimit/     Rate limiter tests with a controllable clock
├── service/       Integration tests, including race tests and the queued checkout
└── support/       Test base class and a controllable clock
docs/              API reference and architecture guide
load-test/         Locust load test (500 virtual fans)
```

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `seatlock.holds.ttl` | `PT10M` | How long a hold lasts |
| `seatlock.holds.max-seats-per-hold` | `8` | Seat limit per hold |
| `seatlock.holds.sweep-interval` | `PT30S` | Delay between expiry sweeps |
| `seatlock.holds.sweep-batch-size` | `200` | Holds expired per sweep |
| `seatlock.holds.sweeper-enabled` | `true` | Turn the background sweeper off (tests do this) |
| `seatlock.rate-limit.enabled` | `true` | Per-IP rate limit on checkout (off in tests and load tests) |
| `seatlock.rate-limit.requests-per-second` | `5` | Checkout requests allowed per second per IP, also the burst size |
| `seatlock.rate-limit.paths` | `/api/events/*/holds`, `/api/events/*/purchase-requests`, `/api/holds/*/confirm` | Paths the limit applies to |
| `seatlock.queue.consumers` | `1-4` | Queue consumer threads (one event's requests are still processed one at a time) |
| `seatlock.queue.max-delivery-attempts` | `4` | Tries per queued request before it goes to the dead-letter queue |
| `seatlock.queue.redelivery-delay` | `PT0.5S` | Wait before the first retry |
| `seatlock.queue.redelivery-multiplier` | `2.0` | Growth of the wait on each further retry (0.5 s, 1 s, 2 s) |

## License

Apache License 2.0. See [LICENSE](LICENSE).
