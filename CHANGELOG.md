# Changelog

What each version of SeatLock added, how it was tested, and what it achieved. Newest first.

SeatLock follows [semantic versioning](https://semver.org): while the version is below 1.0, a
minor bump (0.3 → 0.4) adds features and may change the API in a backwards-compatible way, and a
patch bump (0.4.0 → 0.4.1) only fixes bugs. See [Releasing a version](#releasing-a-version) for
the steps.

| Version | Date | Theme | Automated tests | Commits |
|---|---|---|---|---|
| [0.5.0](#050--2026-10-31) | 2026-10-31 | Buyer accounts: sign-up, log-in, forgot password by email, profile menu, My bookings | 119 | tag `v0.5.0` |
| [0.4.0](#040--2026-10-31) | 2026-10-31 | Web client, venue layouts, Book now checkout, test scenarios | 99 | tag `v0.4.0` |
| [0.3.0](#030--2026-10-30) | 2026-10-30 | Traffic spikes: rate limiting, queued checkout, load testing | 86 | `085ece7`, `36a7108` |
| [0.2.0](#020--2026-10-30) | 2026-10-30 | API documentation and Swagger UI | 73 | `1bd34eb` |
| [0.1.0](#010--2026-10-30) | 2026-10-30 | Core reservation backend | 73 | `7950665` |

---

## 0.5.0 — 2026-10-31

Optional buyer accounts. Phase 1 of 2: watchlist, "booking opens" alerts, show reminders and
"selling fast" emails come in 0.6.0.

### Added

- **Accounts** (Spring Security): sign up with name, email, optional phone and password; log in
  and out; the session cookie keeps the buyer signed in (HTTP-only, `SameSite=Lax`, 2 hours idle).
  - Passwords stored only as BCrypt hashes; emails stored lower-case, so sign-in is
    case-insensitive; one account per email.
  - Wrong password and unknown email get the same answer, so sign-in does not reveal who is
    registered.
  - CSRF protection for any change that carries a session cookie; guests and API scripts are
    unaffected.
  - Sign-in endpoints share the per-IP rate limit (5 per second) with checkout.
- **Forgot password by email:** a one-time link valid for 30 minutes; a newer link cancels the
  older one; only a SHA-256 hash of the token is stored; the answer is the same whether or not the
  email has an account. A "password changed" email follows every reset.
- **Email over SMTP** (Spring Mail): welcome, reset link and password changed. Sent after the
  database commit on a background thread. The server is set with `SPRING_MAIL_*` environment
  variables (Gmail app passwords supported); without one, emails are skipped with a warning and
  their content is never logged.
- **My bookings:** a signed-in buyer's bookings, split into **Active** (shows to come) and
  **Past**, with show, venue, date, seats, total and reference. `GET /api/account/bookings`.
- **Profile:** change name and phone (`PUT /api/account`); the email is fixed.
- **Hold ownership:** a hold placed while signed in (directly or through the queue) can only be
  viewed, released or confirmed by that buyer; others get `404`. Guest holds work as before.
- **Web client:** **Log in** / **Sign up** in the top-right corner; once signed in, the corner
  shows the buyer's initials and first name with a menu (name, email, phone, **My bookings**,
  **Profile**, **Log out**). Log-in, sign-up, forgot-password and reset-password pages; My bookings
  page with Active/Past tabs and the profile form. Links such as `#login`, `#bookings` and the
  emailed `#reset/<token>` open the right page directly.
- **Demo account** in the `demo` profile: demo@example.com / seatlock-demo, with two bookings.

### Changed

- The "no personal data" rule is replaced: account holders' name, email and optional phone are
  stored; guests stay anonymous; card details are never stored.
- Holds have an optional `account_id`; the queued checkout message carries it too.
- Spring Security now sends `X-Frame-Options: DENY`, so SeatLock cannot be framed by other sites.
- Version 0.5.0 in `pom.xml` and the OpenAPI spec.

### Tested

| What | How | Result |
|---|---|---|
| Full test suite | `./mvnw clean test` | 119 tests, 0 failures (20 new) |
| Accounts over HTTP | `AccountIntegrationTest` (16), sessions and CSRF sent as a browser does | Sign-up, duplicate email, validation, same answer for wrong password and unknown email, log-out, `401` without a session, forged request `403`, profile edit, My bookings split into upcoming and past, hold ownership, guest holds unchanged |
| Password reset emails | GreenMail in-memory SMTP server | Welcome, reset-link and password-changed emails arrive; link works once, expires after 30 minutes, newest link only; no email for unknown addresses |
| Queued checkout while signed in | `QueuedCheckoutIntegrationTest` | The hold belongs to the buyer's account |
| Entity rules | `AccountRulesTest` (3) | Email normalised; token expiry and single use; guest vs account hold access |
| Existing tests | All 99 from 0.4.0 | Still pass; `HoldControllerTest` now runs with SeatLock's real security rules |
| Running app | `curl` | Demo login returns the profile; My bookings returns the 2 seeded bookings; a signed-in change without the CSRF header gets `403`; a guest hold still gets `201` |
| Web client | Headless Chrome through a test-only copy of the page | Log-in page, wrong-password message, profile menu, My bookings with both bookings, sign-up of a new buyer, forgot-password confirmation |
| No mail server set | Running app without `SPRING_MAIL_HOST` | Emails skipped with a warning naming only the subject; app keeps working |
| Bugs found and fixed | During the checks above | Opening `#login` (or an emailed reset link) showed the show list, because selecting a show overwrote the address; the seat map zoom showed "NaN%" after logging in, because it was measured while hidden; the email tests could count a previous test's late email, so each test now checks only its own address |

---

## 0.4.0 — 2026-10-31

A browser front end, venues of every shape, and a checkout that works like a real ticket site.

### Added

- **Web client** at `http://localhost:8080`: plain HTML, CSS and JavaScript in
  `src/main/resources/static`, served by Spring Boot, with no build step and no Node needed. It uses
  only the public REST API.
  - Show list as a scrolling strip sorted by date, with filters by venue type.
  - Live seat map, refreshed every 5 seconds: available, your selection, held by others, sold.
  - Select up to 8 seats, then **Book now**.
  - Payment page with a 10-minute countdown (red in the last minute), order summary, **Pay** and
    **Cancel and go back**. Payment is a demo: no money is taken and no card details are asked for.
  - Confirmation page with the booking reference.
  - When the countdown ends, the buyer goes back to the seat map and the seats go back on sale.
  - Cancel, or the browser's Back button, frees the seats at once; reloading keeps the payment
    page and its countdown.
  - **Join the queue instead** uses the queued checkout and opens the same payment page.
  - **Find a booking** by reference.
  - Zoom (−, +, Fit) for large venues; works at phone width; follows light and dark mode.
- **Venue layouts.** A venue now has a `type` (arena, stadium, ground, opera house, theatre,
  concert hall, amphitheatre, function hall, club), a `stageZone` and a `stageLabel`; each section
  has a `zone`. Zones are the cells of a 3×3 grid with north at the top, which covers end stages,
  stages in the round, thrust stages, side and corner stages, stadium pitch seating and opera boxes.
  - The fields are optional, so existing API clients keep working: a venue without them is an
    arena with an end stage at the north and its sections facing it.
  - A section may not sit in the stage's zone (`400`).
  - Event responses add `venueCity` and `venueType`; the seat map adds `venueType`, `stageZone`,
    `stageLabel` and each section's `zone`, so the page draws the venue without another request.
- **Demo data: 12 venues and 16 shows**, each venue with its own stage position:

  | Venue | Type | Stage | Seats |
  |---|---|---|---|
  | Riverside Arena | Arena | End stage | 5,000 |
  | Meridian Dome | Arena | In the round | 1,192 |
  | Harbourline Stadium | Stadium | Main stage at one end, pitch seats | 5,240 |
  | Oakmoor Cricket Ground | Ground | Pitch stage in the middle | 2,000 |
  | Old Mill Fairground | Ground | Side stage | 1,380 |
  | Royal Crescent Opera House | Opera house | End stage, boxes on both walls | 1,140 |
  | Teatro Lumen | Opera house | End stage at the south | 720 |
  | Lantern Street Theatre | Theatre | Thrust stage | 464 |
  | Lakeview Concert Hall | Concert hall | Orchestra in the middle, choir behind | 916 |
  | Sunset Bowl Amphitheatre | Amphitheatre | End stage, seats fanning out | 1,632 |
  | Grand Pearl Banquet Hall | Function hall | Stage on the east wall | 392 |
  | The Velvet Room | Club | Corner stage | 290 |

  Riverside Arena keeps venue id 1 and events 1–2, so the load test and its published results
  still apply.
- **`docs/TESTING.md`**: every automated scenario with the test that checks it, 26 manual
  scenarios for the web client, the load test, and what is not covered yet.
- This changelog, and the project version in `pom.xml` and the OpenAPI spec set to 0.4.0.

### Changed

- The web client no longer shows a separate "Hold seats" step: **Book now** places the hold and
  opens the payment page. The backend is unchanged; the hold is what keeps the seats while the
  buyer pays.

### Tested

| What | How | Result |
|---|---|---|
| Full test suite | `./mvnw clean test` | 99 tests, 0 failures (13 new) |
| Venue layout rules | `VenueLayoutTest`, 9 runs | Default zone faces the stage for every stage position; a section in the stage's zone is refused |
| Layout through the API | `VenueLayoutIntegrationTest` | Old-style venue requests get the defaults; a stage in the round reaches the seat map with every zone |
| Page is served | `WebClientIntegrationTest` | `/`, `app.js` and `app.css` are served |
| All 12 venue layouts | Headless Chrome screenshots of each show | Stage in the right place and every section facing it; fixed along the way: empty gap in front of the opera stage, opera boxes in a row instead of along the walls, side stands facing the wrong way, corner stage pointing away from the audience |
| Book now → Pay | Driven in headless Chrome | Booking confirmed (`SL-GUMGX3`); API shows the seats `BOOKED` |
| Cancel and browser Back | Driven in headless Chrome | Back on the seat map; API shows the seats `AVAILABLE` |
| Payment time runs out | Second app with `--seatlock.holds.ttl=PT20S` | Page returns to the seat map with "Time's up"; API shows the seats `AVAILABLE` |
| Two buyers, same seats | Two browser profiles | The second buyer cannot select the seats the first holds |
| Rate limit from the page's point of view | 10 rapid checkout requests with `curl` | 5 × `404`, then `429` |
| Layout at desktop and phone widths, light and dark | Screenshots | Fixed: the widest seat row pushed price labels out of the panel and caused sideways scrolling on phones; sold seats were nearly invisible in dark mode |
| Bug found and fixed | Driving the payment page | **Pay** and **Cancel** rendered disabled when the page first opened |

---

## 0.3.0 — 2026-10-30

Handling a traffic spike when a popular show goes on sale. Commits `085ece7` (features) and
`36a7108` (architecture guide).

### Added

- **Rate limiting:** a token bucket per client IP on the checkout endpoints (holds, queued
  purchase requests, confirm), 5 requests per second. Over the limit gets `429 Too Many Requests`
  with `Retry-After` and a problem body, before any database work. Configurable under
  `seatlock.rate-limit.*`.
- **Queued checkout:** `POST /api/events/{id}/purchase-requests` puts the request on
  `ticket-purchase-queue` (embedded ActiveMQ Artemis) and answers `202` at once;
  `GET /api/purchase-requests/{id}` reports `QUEUED`, `HELD`, `REJECTED` or `FAILED`.
  - One consumer at a time per event (message groups), up to 4 consumer threads in total.
  - Technical failures are retried after 0.5 s, 1 s and 2 s; after 4 attempts the message moves
    to `DLQ.ticket-purchase-queue` and the request is marked `FAILED`.
  - Business refusals (seat taken, unknown event) are `REJECTED` at once and never retried.
  - Each request id is processed once, even if the broker delivers it again.
- **Locust load test** in `load-test/`: 500 virtual fans for 2 minutes, browsing, holding,
  paying or abandoning, and using the queue; checks at the end that no seat was sold twice.
- **Performance work:** the seat map is read as plain columns instead of 5,000 entities, and JSON
  responses are gzip-compressed.
- `CLAUDE.md` with the tech stack, commands and the concurrency rules (transactions, row locks,
  default isolation).

### Tested

| What | How | Result |
|---|---|---|
| Full test suite | `./mvnw clean test` | 86 tests, 0 failures |
| Bigger races | `ConcurrentHoldIntegrationTest` raised from 16 to 200 threads, plus a new mixed hold/confirm/release race on 40 seats | Every seat ends in at most one booking or active hold; the seat map agrees |
| Queue, retries and dead-letter | `QueuedCheckoutIntegrationTest` (6 tests) with a Mockito spy injecting lock timeouts | 20 competing requests → 1 `HELD`, 19 `REJECTED`; 2 failures then success → `HELD` after 3 tries; constant failure → `FAILED` after 4 tries; duplicate delivery processed once |
| Rate limiter | `RateLimitingFilterTest` (6 tests) with a controllable clock | 6th request in a second refused; refills at 5/s; per IP; non-checkout paths never limited |
| Load: 500 fans, 2 minutes | Locust against the demo app, rate limit off | 32,778 requests (273/s, limited by fans' think time), median 10 ms, p99 160 ms, **0 failures, all 5,000 seats sold, 0 seats sold twice** |
| Throughput without pauses | Stress run | Peaked at about 3,300 requests/s (up from 1,775 before the seat-map change) |
| Seat map speed | Timed before and after | 19 ms → 6 ms per request; 391 KB → 42 KB with gzip |
| Connection pool size | Load runs with a bigger pool | No gain (holds on the same seats queue on row locks), so the default of 10 is kept |

---

## 0.2.0 — 2026-10-30

API documentation. Commit `1bd34eb`.

### Added

- OpenAPI spec at `/v3/api-docs` and Swagger UI at `/swagger-ui.html` (springdoc), with a summary
  on every endpoint.
- `docs/API.md`: request and response examples for every endpoint, error statuses and what to do
  about them.
- `docs/ARCHITECTURE.md`: layers, data model, hold lifecycle and the locking design.
- A shareable Claude Doc version of the documentation.

### Tested

| What | How | Result |
|---|---|---|
| Full test suite | `./mvnw clean test` | 73 tests, 0 failures (no code behaviour changed) |
| API examples | Every documented request sent to the running app | Responses match the examples in `docs/API.md` |
| Swagger UI | Opened against the running app | Loads and lists every endpoint |

---

## 0.1.0 — 2026-10-30

The core: hold seats for a limited time, confirm them into bookings, never sell a seat twice.
Commit `7950665`.

### Added

- REST API for venues, events, seat maps, holds and bookings (Spring Boot 3.5, Java 17).
- Venues made of sections laid out as rows × seats; each event gets its own seat inventory.
- **Holds:** up to 8 seats for 10 minutes, never past the show's start; release early, or confirm
  into a booking with a short reference such as `SL-7KQ2MX`.
- **No double selling:**
  - every write locks the affected seat rows with `SELECT ... FOR UPDATE`, sorted by id, so
    competing requests queue instead of deadlocking;
  - paths that touch an existing hold lock the hold first, then its seats;
  - an `@Version` column fails any stale write with `409` instead of overwriting.
- **Expiry:** a lapsed hold stops blocking its seats immediately (lazy expiry); a sweeper marks
  lapsed holds `EXPIRED` every 30 seconds and only frees seats the hold still owns.
- RFC 9457 problem responses: `400`, `404`, `409` (with the taken seat ids), `410`.
- H2 in MySQL mode for development and tests; MySQL 8 profile with Docker Compose.
- `demo` profile: Riverside Arena (5,000 seats in 3 sections) and two shows.
- An injected `Clock` everywhere, so tests control time.

### Tested

| What | How | Result |
|---|---|---|
| Full test suite | `./mvnw clean test` | 73 tests, 0 failures |
| Entity rules | `EventSeatTest`, `HoldTest`, `SeatTest` | Holding, takeover after lapse, booking and release rules; row labels A…Z, AA… |
| Services | `HoldServiceIntegrationTest`, `BookingServiceIntegrationTest`, `HoldExpiryServiceIntegrationTest` | Holds, limits, expiry capped at show start, confirm once, sweeper never undoes a newer hold |
| Races | `ConcurrentHoldIntegrationTest`, 16 threads | Exactly one winner for the same seats; overlapping requests never double-hold or deadlock |
| HTTP | `HoldControllerTest`, `ReservationFlowIntegrationTest` | Status codes and the full venue → event → hold → booking journey |

---

## Known limitations

These are known and documented; none is a bug in the current scope.

- **Real email delivery is checked by hand.** The tests use GreenMail; sending through a real
  SMTP account needs the `SPRING_MAIL_*` settings.
- **Accounts:** no email verification at sign-up, no account deletion, no social sign-in; other
  sessions stay signed in after a password reset until they time out.
- **Single instance.** Seat locking is in the database and works across instances, but the rate
  limit and the embedded broker are per instance. Several instances need a shared limiter (for
  example Redis) and a standalone broker (`spring.artemis.mode=native`).
- **Queued requests live in memory.** The embedded broker loses waiting requests if the app stops
  before processing them.
- **An unknown purchase request id reports `QUEUED`,** because the outcome row is only written
  once the request is processed.
- **MySQL lock wait** is MySQL's default (50 seconds); H2 uses 10 seconds.
- **Seating model:** numbered seats in straight rows of equal length, one price per section. Not
  supported yet: standing areas, round-table booking, rows of different lengths, curved rows,
  per-seat prices.
- **Payment** is a demo step; no payment provider is connected.
- **The web client** has no automated browser tests; its scenarios in `docs/TESTING.md` are
  checked by hand.

## Releasing a version

1. Run `./mvnw clean test` and the manual scenarios in `docs/TESTING.md` that the change touches.
   For changes to locking or checkout, also run the load test.
2. Add a section at the top of this file: what was added, changed and fixed, and a **Tested** table
   with the results. Add the version to the summary table.
3. Set the version in `pom.xml` (`<version>`) and in `config/OpenApiConfig.java` (`.version(...)`).
4. Commit, then tag the commit: `git tag -a v0.4.0 -m "SeatLock 0.4.0"` and
   `git push origin v0.4.0`.
