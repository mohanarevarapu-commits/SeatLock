# CLAUDE.md

SeatLock is a high-concurrency concert ticketing backend: buyers hold seats for a limited time,
then confirm them into bookings, and no seat is ever held or sold twice. See README.md for the
API and docs/ARCHITECTURE.md for the concurrency design.

## Tech stack

- Java 17
- Spring Boot 3.5 (Web, Data JPA, Validation, Security, Mail)
- MySQL 8 as the real database (`mysql` profile, `docker compose up -d`); in-memory H2 in MySQL
  mode for local runs and tests
- Web client: plain HTML/CSS/JS in `src/main/resources/static`, served at `/`, no build step
  (Node is not installed); it uses only the public REST API
- ActiveMQ Artemis via Spring JMS for the queued checkout (embedded broker by default)
- JUnit 5 for tests, with AssertJ, Mockito, MockMvc and GreenMail (in-memory SMTP); Locust (Python) for load tests

## Commands

- Build and run all tests: `./mvnw clean test` (the wrapper form of `mvn clean test`)
- Run one test class: `./mvnw test -Dtest=HoldServiceIntegrationTest`
- Run one test method: `./mvnw test -Dtest=HoldServiceIntegrationTest#cannotHoldASeatSomeoneElseHolds`
- Run the app with demo data: `./mvnw spring-boot:run -Dspring-boot.run.profiles=demo`
- Package: `./mvnw clean package`
- Load test: start the app with `-Dspring-boot.run.arguments=--seatlock.rate-limit.enabled=false`
  (all load-test traffic comes from one IP), then `locust --config load-test/locust.conf`
  (`pip install -r load-test/requirements.txt` first; 500 users for 2 minutes)

Always use `./mvnw`; Maven is not installed globally. Tests use in-memory H2 and need no
external services.

## Architecture

Layers under `src/main/java/com/seatlock`: `controller` -> `service` -> `repository` -> `model`.

- Controllers are thin: validate input (`@Valid`), call one service method, set status/Location.
- Services own transactions and return DTOs from `dto/`, never entities
  (`spring.jpa.open-in-view` is off, so lazy loading outside a service will fail).
- Domain rules live on the entities (`EventSeat.hold/book/releaseIfHeldBy`, `Hold.confirm/...`).
  Services orchestrate locking and call those methods.
- Domain errors are exceptions in `exception/`, mapped to HTTP status in `GlobalExceptionHandler`.
- Queued checkout: `PurchaseRequestService.submit` only sends to the embedded Artemis queue;
  `queue/PurchaseRequestConsumer` processes it via `HoldService`. Only `REJECTED` outcomes for
  business exceptions are final; any other exception must propagate so the broker retries and
  dead-letters it.
- `ratelimit/RateLimitingFilter` limits checkout requests per client IP (`seatlock.rate-limit.*`).
  It is off in the `test` profile because the test clock is frozen; `RateLimitingFilterTest` covers it.
- `security/SecurityConfig`: session sign-in, only `/api/account/**` needs it. CSRF is checked only
  for requests that carry a session cookie; the web client sends `X-XSRF-TOKEN`. Spring Security
  sends `X-Frame-Options: DENY`, so the page cannot be shown in a frame (drive browser tests
  through a test-only page, not an iframe).
- Web client: `static/app.js` (home page with Today's / Tomorrow's / Selling fast blocks that open lists at `#today`, `#tomorrow`, `#selling-fast`, `#all`; seat map; checkout), `static/payment.js` (the demo card/UPI payment form: accepts only test details, which never leave the browser; never send card data to the server), `static/tickets.js` (Find my tickets at `#tickets`, and the ticket card with its QR code), `static/info.js` (help and policy pages linked from the footer; keep their text true to what the app does) and `static/account.js` (sign-in,
  profile menu, My bookings, routing by `#hash`; it also starts the page).

## Concurrency rules (do not break these)

- Every database write that touches seat reservations (creating, confirming, releasing or
  expiring a hold) must run inside a service-level transaction (`@Transactional` or
  `TransactionTemplate`) and must take row locks before changing anything. Locks are held until
  commit, so a lock taken outside a transaction protects nothing.
- Transaction isolation: keep the default level. Correctness comes from the `FOR UPDATE` row
  locks below, which hold under H2's READ COMMITTED and MySQL's REPEATABLE READ. Never lower
  isolation (e.g. READ_UNCOMMITTED), and don't replace the locks with a higher isolation level
  instead.
- Lock `EventSeat` rows only through `EventSeatRepository.findAllByIdForUpdate`, which orders by id.
- When a path touches an existing hold, lock the hold (`HoldRepository.findByIdForUpdate`) before its seats.
- Check seat state only after acquiring the lock, never before.
- Read time from the injected `Clock`, never `Instant.now()`, so tests can control time.

## Conventions

- Constructor injection only; no field `@Autowired` in main code.
- DTOs are Java records; entities are plain classes with protected no-arg constructors and no setters.
- Money is `long` cents. Times are `Instant` in UTC.
- Personal data: only account holders' name, email and optional phone, and a guest's contact email
  and phone on their booking (`Booking.contactEmail/contactPhone`, given on the payment page with
  the confirm request, never returned by the API), are stored. `customerRef` must never hold personal data. Passwords only as BCrypt hashes, reset tokens only as SHA-256
  hashes; never store card details, and never log email bodies (they can hold reset links or ticket
  codes; keep codes out of subjects, which are logged). Find my tickets codes are stored only as
  BCrypt hashes (`TicketAccessCode`).
- Accounts are optional: never require sign-in for browsing or booking. Pass the signed-in account
  id from the controller (`@AuthenticationPrincipal AccountPrincipal`, null for guests) into the
  service; a hold with an account is usable only by that account (`Hold.isAccessibleBy`, 404 otherwise).
- Emails: publish an event inside the transaction and send it from an `@Async @TransactionalEventListener`
  (see `notification/AccountMailer`, `BookingMailer`), never inline. SMTP settings come from `SPRING_MAIL_*` env vars only.
- Demo outbox (`seatlock.mail.demo-outbox`, on in the `demo` profile; page `static/outbox.html`): keeps
  emails in memory when no mail server is set. It holds codes and reset links, so it must only ever
  answer loopback requests (`DemoOutboxController.isLoopback`) and must never be on for a public server.

## Tests

- `model/`: plain unit tests for entity rules.
- `controller/HoldControllerTest`: `@WebMvcTest` with mocked services, for HTTP mapping.
- Integration tests extend `support/IntegrationTest` (full context, H2, `MutableClock`).
  They are not `@Transactional`, because the race tests need real commits. Each test creates
  its own venue and event instead of cleaning up.
- `docs/TESTING.md` lists every test scenario with its test method; add a row when you add a test.
- `CHANGELOG.md` records each version (added, changed, tested, results); update it with every
  feature, and bump the version in `pom.xml`, `OpenApiConfig` and the `?v=` links in `static/index.html` when releasing.
- Move time with `clock.advance(...)`; the background sweeper is disabled in tests, so call
  `HoldExpiryService.expireLapsedHolds()` directly.
