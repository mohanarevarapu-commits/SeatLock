# SeatLock architecture

This guide explains how SeatLock is put together and why it can never sell one seat twice. For
endpoint details see [API.md](API.md); for setup see the [README](../README.md).

## Layers

```mermaid
flowchart LR
    client([HTTP client]) --> controller
    subgraph app [Spring Boot app]
        controller[controller<br/>validate input, set status] --> service[service<br/>transactions, locking, DTOs]
        service --> repository[repository<br/>queries and row locks]
        service --> model[model<br/>entities and domain rules]
        repository --> model
        scheduler[HoldExpiryScheduler<br/>every 30 s] --> service
    end
    repository --> db[(H2 or MySQL)]
```

| Layer | Package | Responsibility |
|---|---|---|
| Controller | `controller` | Validates the request (`@Valid`), calls one service method, sets status and `Location`. No logic. |
| Service | `service` | Owns transactions, takes locks in the right order, calls entity methods, returns DTO records. |
| Repository | `repository` | Spring Data interfaces, including the `FOR UPDATE` locking queries. |
| Model | `model` | JPA entities. Domain rules live here (`EventSeat.hold/book/releaseIfHeldBy`, `Hold.confirm/release/expire`). |
| DTO | `dto` | Request and response records. Entities never leave a service (`open-in-view` is off). |
| Errors | `exception` | Domain exceptions, mapped to RFC 9457 responses in `GlobalExceptionHandler`. |

## Data model

```mermaid
erDiagram
    VENUE ||--o{ SECTION : has
    SECTION ||--o{ SEAT : has
    VENUE ||--o{ EVENT : hosts
    EVENT ||--o{ EVENT_SEAT : "one per seat"
    SEAT ||--o{ EVENT_SEAT : "sold per event"
    EVENT ||--o{ HOLD : has
    HOLD }o--o{ EVENT_SEAT : "hold_seats"
    HOLD ||--o| BOOKING : "confirmed into"

    EVENT_SEAT {
        long id PK
        string status "AVAILABLE, HELD, BOOKED"
        long hold_id "current hold, if any"
        instant held_until
        long version "optimistic lock"
    }
    HOLD {
        long id PK
        string customer_ref
        string status "ACTIVE, CONFIRMED, RELEASED, EXPIRED"
        instant expires_at
    }
    BOOKING {
        long id PK
        string reference "SL-XXXXXX"
        long total_cents
    }
```

- **Venue → Section → Seat** is the physical layout. It is shared by every event at the venue.
- **EventSeat** is the state of one seat for one event. This is the row that buyers compete for,
  and the only row that gets locked when seats change hands.
- **Hold** is a time-limited claim on a set of event seats. It stores the seat ids it covers.
- **Booking** is the confirmed result of exactly one hold (`hold_id` is unique).

## Hold lifecycle

```mermaid
stateDiagram-v2
    [*] --> ACTIVE: POST /events/{id}/holds
    ACTIVE --> CONFIRMED: POST /holds/{id}/confirm
    ACTIVE --> RELEASED: DELETE /holds/{id}
    ACTIVE --> EXPIRED: TTL or event start passes,<br/>then the sweeper runs
    CONFIRMED --> [*]
    RELEASED --> [*]
    EXPIRED --> [*]
```

The matching seat states:

| Hold becomes | Its seats become |
|---|---|
| `ACTIVE` | `HELD`, with `hold_id` and `held_until` set |
| `CONFIRMED` | `BOOKED` |
| `RELEASED` or `EXPIRED` | `AVAILABLE`, but only if they are still held by *this* hold |

A hold's expiry is `min(now + ttl, event.startsAt)`. Every terminal state is final;
`Hold.transitionTo` throws if you try to leave one.

## Concurrency design

### The rules

1. **Lock seats only through `EventSeatRepository.findAllByIdForUpdate`.** It runs
   `SELECT ... FOR UPDATE ... ORDER BY id`, so every transaction locks seats in ascending id order.
   Two requests with overlapping seats queue on the lowest shared id instead of deadlocking.
2. **Lock the hold before its seats.** Confirm, release and expiry call
   `HoldRepository.findByIdForUpdate` first. Creating a hold never locks another hold. So the
   global lock order is always *hold, then seats*.
3. **Check state only after the lock.** `isAvailableAt`, `isHeldBy` and the hold's status are read
   from the locked rows, never from an earlier unlocked read.
4. **`@Version` on `EventSeat` is a backstop.** If a stale write ever got through, it fails with
   `409 Concurrent update` instead of overwriting.
5. **Read time from the injected `Clock`,** never `Instant.now()`, so tests can move time.

### Two buyers, one seat

```mermaid
sequenceDiagram
    participant A as Buyer A
    participant B as Buyer B
    participant S as HoldService
    participant DB as event_seats

    A->>S: hold seats [7, 8]
    B->>S: hold seats [8, 9]
    S->>DB: A: lock 7, 8 (FOR UPDATE, by id)
    S->>DB: B: lock 8, 9
    Note over DB: B waits on row 8
    S->>DB: A: 7, 8 available → mark HELD, commit
    DB-->>S: B: lock on 8 granted
    S->>DB: B: 8 is HELD → nothing changed
    S-->>A: 201 hold created
    S-->>B: 409 Seats unavailable, eventSeatIds [8]
```

Because B only checks seat 8 after acquiring its lock, it always sees A's committed result.

### Expiry without races

- **Lazy expiry.** `EventSeat.isAvailableAt(now)` treats a `HELD` seat whose `held_until` has
  passed as available. New holds can take it at once, without waiting for cleanup.
- **The sweeper** (`HoldExpiryScheduler`, every `sweep-interval`) asks `HoldExpiryService` for up
  to `sweep-batch-size` lapsed `ACTIVE` holds. Each one is expired in its own short transaction:
  lock the hold, re-check it is still active and lapsed, lock its seats, and free only those still
  held by that hold (`releaseIfHeldBy`). A seat already re-held by a newer buyer is left alone.
- **Confirm vs. expiry.** Confirm locks the hold, rejects it with `410` if its time has passed,
  then locks the seats and checks each is still held by this hold. Whichever of confirm or the
  sweeper gets the hold lock first wins; the other sees a non-active hold and stops.

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `seatlock.holds.ttl` | `PT10M` | How long a hold lasts |
| `seatlock.holds.max-seats-per-hold` | `8` | Seat limit per hold |
| `seatlock.holds.sweep-interval` | `PT30S` | Delay between expiry sweeps |
| `seatlock.holds.sweep-batch-size` | `200` | Holds expired per sweep |
| `seatlock.holds.sweeper-enabled` | `true` | Turn the background sweeper off (tests do this) |

Profiles: default uses in-memory H2 in MySQL mode; `mysql` points at MySQL 8 (see
`docker-compose.yml`); `demo` seeds a 5,000-seat venue and two events.

## Testing strategy

| Kind | Where | How |
|---|---|---|
| Entity rules | `src/test/.../model` | Plain unit tests, no Spring |
| HTTP mapping | `controller/HoldControllerTest` | `@WebMvcTest` with mocked services |
| End-to-end flow | `controller/ReservationFlowIntegrationTest` | Full app over MockMvc |
| Services and races | `src/test/.../service` | Full context on H2, extending `support/IntegrationTest` |

Integration tests are **not** `@Transactional`, because the race tests need real commits across
threads. Each test creates its own venue and event instead of cleaning up. Time is controlled with
`MutableClock.advance(...)`, and since the sweeper is disabled in tests, they call
`HoldExpiryService.expireLapsedHolds()` directly.

`ConcurrentHoldIntegrationTest` runs 16 threads at once against the same seats (exactly one must
win) and against overlapping seats (no seat may be held twice, and nothing may deadlock).
