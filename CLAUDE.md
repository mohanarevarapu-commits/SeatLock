# CLAUDE.md

SeatLock is a Spring Boot 3.5 / Java 17 ticket reservation backend. See README.md for the API
and the concurrency design.

## Commands

- Build and run all tests: `./mvnw clean test`
- Run one test class: `./mvnw test -Dtest=HoldServiceIntegrationTest`
- Run one test method: `./mvnw test -Dtest=HoldServiceIntegrationTest#cannotHoldASeatSomeoneElseHolds`
- Run the app with demo data: `./mvnw spring-boot:run -Dspring-boot.run.profiles=demo`
- Package: `./mvnw clean package`

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

## Concurrency rules (do not break these)

- Lock `EventSeat` rows only through `EventSeatRepository.findAllByIdForUpdate`, which orders by id.
- When a path touches an existing hold, lock the hold (`HoldRepository.findByIdForUpdate`) before its seats.
- Check seat state only after acquiring the lock, never before.
- Read time from the injected `Clock`, never `Instant.now()`, so tests can control time.

## Conventions

- Constructor injection only; no field `@Autowired` in main code.
- DTOs are Java records; entities are plain classes with protected no-arg constructors and no setters.
- Money is `long` cents. Times are `Instant` in UTC.
- No personal data: customers are identified only by an opaque `customerRef`.

## Tests

- `model/`: plain unit tests for entity rules.
- `controller/HoldControllerTest`: `@WebMvcTest` with mocked services, for HTTP mapping.
- Integration tests extend `support/IntegrationTest` (full context, H2, `MutableClock`).
  They are not `@Transactional`, because the race tests need real commits. Each test creates
  its own venue and event instead of cleaning up.
- Move time with `clock.advance(...)`; the background sweeper is disabled in tests, so call
  `HoldExpiryService.expireLapsedHolds()` directly.
