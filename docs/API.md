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
- Customers are identified only by `customerRef`, an opaque token you choose (for example a
  checkout session id). Never put personal data in it.

## Contents

- [Typical flow](#typical-flow)
- [Venues](#venues)
- [Events and seat maps](#events-and-seat-maps)
- [Holds](#holds)
- [Bookings](#bookings)
- [Queued checkout](#queued-checkout)
- [Errors](#errors)
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

| Field | Type | Rules |
|---|---|---|
| `name` | string | required, max 120 chars |
| `city` | string | required, max 120 chars |
| `sections` | array | 1 to 50 sections |
| `sections[].name` | string | required, max 60 chars |
| `sections[].rows` | int | 1 to 100 |
| `sections[].seatsPerRow` | int | 1 to 100 |
| `sections[].priceCents` | long | 0 or more |

```bash
curl -s -X POST localhost:8080/api/venues -H 'Content-Type: application/json' -d '{
  "name": "Harbor Hall",
  "city": "Example City",
  "sections": [{"name": "Stalls", "rows": 2, "seatsPerRow": 3, "priceCents": 5000}]
}'
```

`201 Created`, `Location: /api/venues/2`

```json
{
  "id": 2,
  "name": "Harbor Hall",
  "city": "Example City",
  "capacity": 6,
  "sections": [
    { "id": 4, "name": "Stalls", "seatCount": 6, "priceCents": 5000 }
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
  "startsAt": "2026-12-31T20:00:00Z"
}
```

### `GET /api/events`

Lists all events.

### `GET /api/events/{id}`

Fetches one event. `404` if it does not exist.

### `GET /api/events/{id}/seat-map`

Every seat for the event, grouped by section, with its live status: `AVAILABLE`, `HELD` or
`BOOKED`. A seat whose hold has run out shows as `AVAILABLE` immediately, even before the
background cleanup has run.

```json
{
  "eventId": 3,
  "availableCount": 6,
  "sections": [
    {
      "sectionId": 4,
      "name": "Stalls",
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
| `400` | `Invalid request` | Too many seats, or the event has started | Fix the request |
| `404` | `Not found` | Unknown venue, event, hold, seat or booking | Check the id |
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
and `POST /api/holds/{id}/confirm`, together allow **5 requests per second per client IP**, with bursts of up to 5. Each IP has a
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
