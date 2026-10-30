# SeatLock API reference

Base URL: `http://localhost:8080`. All bodies are JSON. Interactive docs for the running app
are at [`/swagger-ui.html`](http://localhost:8080/swagger-ui.html), and the raw OpenAPI spec is at
`/v3/api-docs`.

Conventions:

- Money is integer **cents** (`priceCents`, `totalCents`). `5000` = 50.00.
- Times are ISO-8601 instants in **UTC**, e.g. `2026-12-31T20:00:00Z`.
- `POST` requests that create something return `201 Created` with a `Location` header.
- Send `Accept-Encoding: gzip` to get compressed responses. A 5,000-seat map drops from about
  391 KB to 42 KB.
- Customers are identified by `customerRef`, an opaque token you choose (for example a
  checkout session id). Never put personal data in it.
- Accounts are optional. Without a session every endpoint works as a guest, as before. A
  signed-in buyer (see [Accounts](#accounts)) sends the session cookie, and their holds and
  bookings are tied to their account.

## Contents

- [Typical flow](#typical-flow)
- [Venues](#venues)
- [Events and seat maps](#events-and-seat-maps)
- [Holds](#holds)
- [Bookings](#bookings)
- [Accounts](#accounts)
- [Errors](#errors)
- [Queued checkout](#queued-checkout)
- [Rate limiting](#rate-limiting)

## Typical flow

1. `GET /api/events` to find an event.
2. `GET /api/events/{id}/seat-map` to see which seats are free and get their `eventSeatId`s.
3. `POST /api/events/{id}/holds` to hold up to 8 seats for 10 minutes.
4. Take payment (outside SeatLock).
5. `POST /api/holds/{id}/confirm` to turn the hold into a booking, or
   `DELETE /api/holds/{id}` to give the seats back.

If you do neither, the hold expires and its seats become available again.

---

## Venues

### `POST /api/venues`

Creates a venue. Each section is a grid of `rows` × `seatsPerRow`; rows are labelled `A`, `B`, …,
`Z`, `AA`, `AB`, and so on.

The layout is described on a 3×3 grid with north at the top. Each cell is a **map zone**:
`NORTH_WEST`, `NORTH`, `NORTH_EAST`, `WEST`, `CENTER`, `EAST`, `SOUTH_WEST`, `SOUTH`,
`SOUTH_EAST`. The stage takes one zone and sections go in the others, so the same API describes:

| Layout | Stage zone | Sections in |
|---|---|---|
| End stage (arena, theatre, opera house) | `NORTH` | `SOUTH`, plus `WEST`/`EAST` for side boxes |
| Stage in the round | `CENTER` | all eight zones around it |
| Thrust stage | `CENTER` | `WEST`, `EAST`, `SOUTH` and the south corners |
| Stadium concert | `NORTH` | `CENTER` for pitch seats, stands in the other zones |
| Side or corner stage (ground, function hall, club) | e.g. `WEST`, `SOUTH_WEST` | the rest of the room |

Several sections may share a zone: tiers stack away from the stage in the order given, and
sections beside the stage line up along the wall (like opera boxes). Clients draw each section
facing the stage, with row `A` nearest to it.

| Field | Type | Rules |
|---|---|---|
| `name` | string | required, max 120 chars |
| `city` | string | required, max 120 chars |
| `type` | string | optional: `ARENA` (default), `STADIUM`, `GROUND`, `OPERA_HOUSE`, `THEATRE`, `CONCERT_HALL`, `AMPHITHEATRE`, `FUNCTION_HALL`, `CLUB` |
| `stageZone` | string | optional map zone, default `NORTH` |
| `stageLabel` | string | optional, max 30 chars, text drawn on the stage; default `STAGE` |
| `sections` | array | 1 to 50 sections |
| `sections[].name` | string | required, max 60 chars |
| `sections[].rows` | int | 1 to 100 |
| `sections[].seatsPerRow` | int | 1 to 100 |
| `sections[].priceCents` | long | 0 or more |
| `sections[].zone` | string | optional map zone, default across from the stage (`SOUTH` for a `CENTER` stage); must not be the stage's zone (`400`) |

```bash
curl -s -X POST localhost:8080/api/venues -H 'Content-Type: application/json' -d '{
  "name": "Harbor Hall",
  "city": "Example City",
  "type": "THEATRE",
  "stageZone": "NORTH",
  "sections": [
    {"name": "Stalls", "rows": 2, "seatsPerRow": 3, "priceCents": 5000, "zone": "SOUTH"},
    {"name": "Box A", "rows": 1, "seatsPerRow": 2, "priceCents": 9000, "zone": "WEST"}
  ]
}'
```

`201 Created`, `Location: /api/venues/2`

```json
{
  "id": 2,
  "name": "Harbor Hall",
  "city": "Example City",
  "type": "THEATRE",
  "stageZone": "NORTH",
  "stageLabel": "STAGE",
  "capacity": 8,
  "sections": [
    { "id": 4, "name": "Stalls", "zone": "SOUTH", "seatCount": 6, "priceCents": 5000 },
    { "id": 5, "name": "Box A", "zone": "WEST", "seatCount": 2, "priceCents": 9000 }
  ]
}
```

### `GET /api/venues`

Lists all venues, in the same shape as above.

### `GET /api/venues/{id}`

Fetches one venue. `404` if it does not exist.

---

## Events and seat maps

### `POST /api/events`

Creates an event at a venue. This also creates the event's own seat inventory: one entry per seat
in the venue, each with its own `eventSeatId`.

| Field | Type | Rules |
|---|---|---|
| `venueId` | long | required, must exist |
| `name` | string | required, max 160 chars |
| `startsAt` | instant | required, must be in the future |

```bash
curl -s -X POST localhost:8080/api/events -H 'Content-Type: application/json' -d '{
  "venueId": 2, "name": "Example Night", "startsAt": "2026-12-31T20:00:00Z"
}'
```

`201 Created`, `Location: /api/events/3`

```json
{
  "id": 3,
  "name": "Example Night",
  "venueId": 2,
  "venueName": "Harbor Hall",
  "venueCity": "Example City",
  "venueType": "THEATRE",
  "startsAt": "2026-12-31T20:00:00Z",
  "totalSeats": 120,
  "availableSeats": 120,
  "fromPriceCents": 4500
}
```

Every event response carries seat totals: `availableSeats` of `totalSeats` are still on sale (a
hold that has run out counts as on sale, even before the cleanup job has released it), and
`fromPriceCents` is the cheapest section's price. The web client uses them for **Selling fast**
and "Only N left" on its home page.

### `GET /api/events`

Lists all events, soonest first, each with its seat totals. The totals are counted in one query
for the whole list.

### `GET /api/events/{id}`

Fetches one event. `404` if it does not exist.

### `GET /api/events/{id}/seat-map`

Every seat for the event, grouped by section, with its live status: `AVAILABLE`, `HELD` or
`BOOKED`. A seat whose hold has run out shows as `AVAILABLE` immediately, even before the
background cleanup has run. It also carries the venue layout (`venueType`, `stageZone`,
`stageLabel` and each section's `zone`), so a client can draw the map without another request.

```json
{
  "eventId": 3,
  "venueType": "THEATRE",
  "stageZone": "NORTH",
  "stageLabel": "STAGE",
  "availableCount": 8,
  "sections": [
    {
      "sectionId": 4,
      "name": "Stalls",
      "zone": "SOUTH",
      "priceCents": 5000,
      "availableCount": 6,
      "seats": [
        { "eventSeatId": 10001, "label": "A1", "row": "A", "number": 1, "status": "AVAILABLE" },
        { "eventSeatId": 10002, "label": "A2", "row": "A", "number": 2, "status": "AVAILABLE" }
      ]
    }
  ]
}
```

Use `eventSeatId` (not the label) when placing a hold.

---

## Holds

A hold reserves seats for one customer for a limited time (10 minutes by default, and never past
the event's start). Its `status` is `ACTIVE` until it ends as `CONFIRMED`, `RELEASED` or `EXPIRED`.

### `POST /api/events/{id}/holds`

| Field | Type | Rules |
|---|---|---|
| `customerRef` | string | required, max 64 chars, letters, digits, `_` and `-` only |
| `eventSeatIds` | array of long | required, 1 to 8 ids, all from this event's seat map |

```bash
curl -s -X POST localhost:8080/api/events/3/holds -H 'Content-Type: application/json' -d '{
  "customerRef": "checkout-123", "eventSeatIds": [10001, 10002]
}'
```

`201 Created`, `Location: /api/holds/1`

```json
{
  "id": 1,
  "eventId": 3,
  "customerRef": "checkout-123",
  "status": "ACTIVE",
  "expiresAt": "2026-10-30T11:05:33.663534Z",
  "totalCents": 10000,
  "seats": [
    { "eventSeatId": 10001, "section": "Stalls", "label": "A1", "priceCents": 5000 },
    { "eventSeatId": 10002, "section": "Stalls", "label": "A2", "priceCents": 5000 }
  ]
}
```

The hold is all or nothing. If any requested seat is taken, nothing is held and you get a `409`
whose `eventSeatIds` lists exactly the seats that were unavailable:

```json
{
  "type": "about:blank",
  "title": "Seats unavailable",
  "status": 409,
  "detail": "Seats no longer available: [10002]",
  "instance": "/api/events/3/holds",
  "eventSeatIds": [10002]
}
```

Other failures:

| Status | When |
|---|---|
| `400` | Validation failed, more than 8 seats, or the event has already started |
| `404` | Unknown event, or a seat id that does not belong to this event |
| `409` | A seat is taken (above), or a concurrent update; retry the request |
| `429` | More than 5 checkout requests per second from your IP; see [Rate limiting](#rate-limiting) |

### `GET /api/holds/{id}`

Fetches a hold, in the same shape as above. `404` if it does not exist.

### `DELETE /api/holds/{id}`

Releases an active hold early and frees its seats. Returns the hold with `status: "RELEASED"`.
`409` if the hold is no longer active (already confirmed, released or expired).

### `POST /api/holds/{id}/confirm`

Turns an active hold into a booking. Call this after payment succeeds; SeatLock does not take
payment itself.

`201 Created`, `Location: /api/bookings/SL-ZR59YX`

```json
{
  "reference": "SL-ZR59YX",
  "holdId": 1,
  "eventId": 3,
  "totalCents": 10000,
  "createdAt": "2026-10-30T10:55:33.691589Z",
  "seats": [
    { "eventSeatId": 10001, "section": "Stalls", "label": "A1", "priceCents": 5000 },
    { "eventSeatId": 10002, "section": "Stalls", "label": "A2", "priceCents": 5000 }
  ]
}
```

| Status | When |
|---|---|
| `404` | Unknown hold |
| `409` | Hold is not active (already confirmed, released or expired) |
| `410` | Hold's time ran out before you confirmed; place a new hold |
| `429` | More than 5 checkout requests per second from your IP; see [Rate limiting](#rate-limiting) |

---

## Bookings

### `GET /api/bookings/{reference}`

Fetches a booking by its reference, in the same shape as the confirm response. References are
`SL-` plus 6 characters from `23456789ABCDEFGHJKMNPQRSTUVWXYZ` (no `0`, `O`, `1`, `I` or `L`, so
they are easy to read out). `404` if not found.

---

## Accounts

Optional buyer accounts. A signed-in buyer is identified by the `JSESSIONID` session cookie
(HTTP-only, `SameSite=Lax`, ends after 2 hours without activity).

**CSRF.** Any `POST`, `PUT` or `DELETE` that carries a session cookie must also send the
`X-XSRF-TOKEN` header with the value of the `XSRF-TOKEN` cookie, which every response sets.
Requests without a session cookie (guests, scripts, the load test) need nothing.

**Who can use a hold.** A guest hold works for anyone who has its id, as before. A hold placed by
a signed-in buyer can only be fetched, released or confirmed in that buyer's session; anyone else
gets `404`, as if it did not exist.

### `POST /api/auth/register`

Creates an account and signs in (sets the session cookie). Sends a welcome email.

| Field | Type | Rules |
|---|---|---|
| `name` | string | required, max 100 chars |
| `email` | string | required, valid email; stored lower-case, unique |
| `password` | string | required, 8 to 72 chars |
| `phone` | string | optional; 7 to 20 digits, spaces, brackets and dashes, may start with `+` |

```bash
curl -s -c jar -b jar -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' -d '{
  "name": "Asha Rao", "email": "asha@example.com", "password": "correct-horse-1", "phone": "+44 20 7946 0000"
}'
```

`201 Created`, `Location: /api/account`

```json
{ "id": 2, "name": "Asha Rao", "email": "asha@example.com", "phone": "+44 20 7946 0000",
  "createdAt": "2026-10-31T09:00:00Z" }
```

`409 Email already registered` if the email has an account, in any letter case.

### `POST /api/auth/login`

`{"email": "...", "password": "..."}`. Signs in and returns the profile (as above). The email is
not case-sensitive. A wrong password and an unknown email both get the same
`401 Sign-in failed`, so the endpoint does not reveal who is registered.

### `POST /api/auth/logout`

Ends the session. `204 No Content`.

### `POST /api/auth/forgot-password`

`{"email": "..."}`. Always `202 Accepted`, whether or not the email has an account. If it has
one, SeatLock emails a link `<base-url>/#reset/<token>` that works once, for 30 minutes. Asking
again cancels the previous link.

### `POST /api/auth/reset-password`

`{"token": "<from the link>", "password": "<new, 8 to 72 chars>"}`. `204 No Content`, and a
"password changed" email is sent. `400 Invalid request` if the link is unknown, already used or
expired.

### `GET /api/account`

The signed-in buyer's profile. `401 Sign-in required` without a session.

### `PUT /api/account`

`{"name": "...", "phone": "..."}` (an empty phone removes it). Returns the updated profile.
The email cannot be changed.

### `GET /api/account/bookings`

The signed-in buyer's bookings, split by whether the show has started:

```json
{
  "upcoming": [
    {
      "reference": "SL-B9W6GV",
      "eventId": 9,
      "eventName": "The Glass Garden: An Opera in Three Acts",
      "venueName": "Royal Crescent Opera House",
      "venueCity": "Example City",
      "startsAt": "2026-12-05T13:30:00Z",
      "totalCents": 30000,
      "bookedAt": "2026-10-31T09:00:00Z",
      "seats": [
        { "eventSeatId": 18001, "section": "Stalls", "label": "A1", "priceCents": 15000 }
      ]
    }
  ],
  "past": []
}
```

`upcoming` is soonest show first; `past` is most recent first. Guest bookings never appear here.

## Errors

Every error is an [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem response with
`Content-Type: application/problem+json`:

```json
{
  "type": "about:blank",
  "title": "Not found",
  "status": 404,
  "detail": "Hold 999999 not found",
  "instance": "/api/holds/999999"
}
```

| Status | `title` | Meaning | What to do |
|---|---|---|---|
| `400` | `Bad Request` | Body failed validation | Fix the request |
| `400` | `Invalid request` | Too many seats, the event has started, or a reset link is invalid or expired | Fix the request; ask for a new reset link |
| `401` | `Sign-in failed` | Wrong email or password (same answer for both) | Check and retry |
| `401` | `Sign-in required` | `/api/account/**` without a session | Sign in first |
| `403` | `Forbidden` | A request with a session cookie but no valid `X-XSRF-TOKEN` header | Send the CSRF header (see [Accounts](#accounts)) |
| `404` | `Not found` | Unknown venue, event, hold, seat or booking, or another buyer's hold | Check the id |
| `409` | `Email already registered` | Sign-up with an email that has an account (any letter case) | Sign in, or reset the password |
| `409` | `Seats unavailable` | Someone else holds or bought a seat; see `eventSeatIds` | Pick other seats |
| `409` | `Hold not active` | Hold already confirmed, released or expired | Fetch the hold to see its status |
| `409` | `Concurrent update` | Another request changed the same seats at the same moment | Retry |
| `410` | `Hold expired` | Confirm arrived after the hold's time ran out | Place a new hold |
| `429` | `Too many requests` | Over the checkout rate limit | Wait for `Retry-After` seconds, then retry |

## Queued checkout

An asynchronous alternative to `POST /api/events/{id}/holds` for peak traffic. The request is
queued and the client polls for the outcome.

### `POST /api/events/{id}/purchase-requests`

Same body and rules as placing a hold. Returns `202 Accepted` immediately; nothing is checked
against the database yet, so an unknown event or a taken seat shows up in the outcome, not here.

```bash
curl -s -X POST localhost:8080/api/events/1/purchase-requests -H 'Content-Type: application/json' -d '{
  "customerRef": "fan-a", "eventSeatIds": [1, 2]
}'
```

`202 Accepted`, `Location: /api/purchase-requests/f682085c-b199-4d26-b250-2da6ca88b009`

```json
{
  "requestId": "f682085c-b199-4d26-b250-2da6ca88b009",
  "eventId": 1,
  "status": "QUEUED",
  "holdId": null,
  "reason": null,
  "processedAt": null
}
```

### `GET /api/purchase-requests/{requestId}`

| `status` | Meaning | Next step |
|---|---|---|
| `QUEUED` | Not processed yet (also returned for an unknown id) | Poll again shortly |
| `HELD` | Seats are held; `holdId` is set | Confirm with `POST /api/holds/{holdId}/confirm` within 10 minutes |
| `REJECTED` | Refused; `reason` says why, e.g. `Seats no longer available: [2]` | Pick other seats |
| `FAILED` | Kept failing for a technical reason and was given up after retries | Submit a new request |

```json
{
  "requestId": "f682085c-b199-4d26-b250-2da6ca88b009",
  "eventId": 1,
  "status": "HELD",
  "holdId": 1,
  "reason": null,
  "processedAt": "2026-10-30T12:15:49.617902Z"
}
```

## Rate limiting

The checkout endpoints, `POST /api/events/{id}/holds`, `POST /api/events/{id}/purchase-requests`
and `POST /api/holds/{id}/confirm`, and the sign-in endpoints `POST /api/auth/*`, together allow **5 requests per second per client IP**, with bursts of up to 5. Each IP has a
token bucket that refills continuously, so one request is allowed again every 200 ms. Other
endpoints, including the seat map, are not limited.

Over the limit, the request is rejected before it touches the database:

```
HTTP/1.1 429
Retry-After: 1
Content-Type: application/problem+json

{"type":"about:blank","title":"Too many requests","status":429,"detail":"Too many checkout requests from your address. The limit is 5 requests per second; retry after 1 second.","instance":"/api/events/1/holds"}
```

Wait the number of seconds in `Retry-After` before retrying.

Configured under `seatlock.rate-limit` (`enabled`, `requests-per-second`, `paths`). Behind a proxy
or load balancer, set `server.forward-headers-strategy=native` so the limit applies to the real
client IP rather than the proxy's.
