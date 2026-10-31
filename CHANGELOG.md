# Changelog

What each version of SeatLock added, how it was tested, and what it achieved. Newest first.

SeatLock follows [semantic versioning](https://semver.org): while the version is below 1.0, a
minor bump (0.3 → 0.4) adds features and may change the API in a backwards-compatible way, and a
patch bump (0.4.0 → 0.4.1) only fixes bugs. See [Releasing a version](#releasing-a-version) for
the steps.

| Version | Date | Theme | Automated tests | Commits |
|---|---|---|---|---|
| [0.9.0](#090--2026-10-31) | 2026-10-31 | Ticket email with QR code and venue details; Find my tickets with email, phone and a one-time code; demo outbox; release jar run locally | 140 | tag `v0.9.0` |
| [0.8.0](#080--2026-10-31) | 2026-10-31 | Log in, sign up or continue as guest; guest email and phone on the payment page, then Pay now; booking confirmation emails; always start on the home page | 127 | tag `v0.8.0` |
| [0.7.0](#070--2026-10-31) | 2026-10-31 | Home page blocks that open their lists; demo payment page with card and UPI (test details only) | 122 | released in the `v0.8.0` commit (no separate tag) |
| [0.6.0](#060--2026-10-31) | 2026-10-31 | Minimal home page: description, search, Today / Tomorrow / Selling fast boxes, What we offer, footer with help and policy pages; live seat status; seat totals in the event API | 122 | tag `v0.6.0` |
| [0.5.0](#050--2026-10-31) | 2026-10-31 | Buyer accounts: sign-up, log-in, forgot password by email, profile menu, My bookings | 119 | tag `v0.5.0` |
| [0.4.0](#040--2026-10-31) | 2026-10-31 | Web client, venue layouts, Book now checkout, test scenarios | 99 | tag `v0.4.0` |
| [0.3.0](#030--2026-10-30) | 2026-10-30 | Traffic spikes: rate limiting, queued checkout, load testing | 86 | `085ece7`, `36a7108` |
| [0.2.0](#020--2026-10-30) | 2026-10-30 | API documentation and Swagger UI | 73 | `1bd34eb` |
| [0.1.0](#010--2026-10-30) | 2026-10-30 | Core reservation backend | 73 | `7950665` |

---

## 0.9.0 — 2026-10-31

Tickets: a QR code and the venue's details in every booking email, and Find my tickets for guests.

### Added

- **Ticket email:** "Your SeatLock booking SL-…" is now a ticket, in HTML with a plain-text
  version: the concert and its time (in `seatlock.accounts.time-zone`, default the server's zone,
  with the zone named), a **QR code** shown inside the email (an inline PNG holding the booking
  reference), the **venue** (name, street address, city, type and stage), every seat by section,
  row and seat number with its price, the total, who it is booked for, and how to see it again
  (Find my tickets for guests, My bookings for accounts).
- **QR codes:** `GET /api/bookings/{reference}/qr` (320×320 PNG), drawn with ZXing `core` 3.5.3
  (`QrCodeGenerator`). Shown on the booking confirmation, on Find my tickets, and as a **Ticket QR
  code** link in My bookings.
- **Find my tickets** (`#tickets`, in the header and footer): a guest enters the email and phone
  number they paid with, clicks **Send code**, gets a **6-digit code by email** and sees their tickets.
  - `POST /api/tickets/code` (always `202`) and `POST /api/tickets/lookup`.
  - A code works once, for 10 minutes; 5 wrong tries end it; a new code replaces the old one and is
    sent at most every 30 seconds. Only a BCrypt hash of the code is stored (`TicketAccessCode`).
  - The same answer, and the same hashing work, whether or not bookings match, so nobody can find
    out who has booked. The code is only in the email body, never the subject (subjects are logged).
  - Email matched in any case, phone with any spacing, brackets or dashes; rate-limited with the
    checkout paths (`/api/tickets/*`).
- **Venue address:** optional `address` on venues (create request, venue and event responses),
  printed on tickets; the 12 demo venues have fictional addresses.
- **Seat rows:** seat lines (in holds, bookings and tickets) also give `row` and seat `number`.
- **Demo outbox** (`seatlock.mail.demo-outbox`, on in the `demo` profile): with no mail server,
  emails are kept in memory (last 50) and shown at `/outbox.html`, so the ticket email and codes
  can be seen without SMTP. It answers only requests from the same computer.

### Changed

- `EmailSender` sends HTML emails with inline images (multipart), and plain text as before.
- Guest contact emails are stored lower-case, so Find my tickets matches them in any case.
- Help, Privacy and Security pages explain ticket emails, Find my tickets and its codes.
- Version 0.9.0 in `pom.xml`, the OpenAPI spec and the `?v=` file links.

### Known limitations

- Codes go by email only; there is no SMS service.
- A ticket's QR code holds only the booking reference: there is no scan-once check-in.

### Run

- Built with `./mvnw clean package` and run as a jar with the demo data:
  `java -jar target/seatlock-0.9.0.jar --spring.profiles.active=demo` (http://localhost:8080).
  Nothing is published to a public server.

### Tested

| What | How | Result |
|---|---|---|
| Full test suite | `./mvnw clean package` | 140 tests, 0 failures (13 new) |
| Ticket email | GreenMail | HTML and text parts carry the concert, venue name, address, city, type and stage, each seat by row and number, prices, total, email and phone; the inline image is a PNG whose QR code decodes to the reference |
| QR endpoint | HTTP, decoded with ZXing | PNG scans to the reference; unknown reference `404` |
| Find my tickets | HTTP, GreenMail, controllable clock | Code by email; lookup returns only the matching guest bookings, soonest first; email in capitals and phone with brackets match; code works once; unknown details get the same `202` and no email; 5 wrong tries lock it; expires after 10 minutes; a new code only after 30 seconds and it replaces the old one; validation `400` |
| Demo outbox | Unit tests | Newest first, last 50, images as data URIs; loopback addresses only |
| Release jar | `java -jar` with the demo profile | Starts in under 3 seconds; reports 0.9.0; venues have addresses; outbox answers this computer |
| In the browser | Headless Chrome against the same build | Guest booking `SL-GPGSHC`: QR shown on the confirmation; ticket email in the outbox with the QR image, venue address and both seats; Find my tickets with the email in other capitals and the phone with dashes; code email (code not in the subject); wrong code refused; right code shows the ticket with its QR code and address; screenshots of the confirmation, the tickets page and the ticket email |
| Manual scenarios | `docs/TESTING.md` T1–T11, test data for Find my tickets | |

---

## 0.8.0 — 2026-10-31

Book as a guest or log in, and booking details by email.

### Added

- **"How would you like to book?"** When a logged-out buyer clicks **Book now** (or **Join the
  queue instead**), a dialog offers three buttons and nothing to fill in:
  - **Log in** or **Sign up**: after signing in the buyer returns to the same concert with the
    same seats selected and goes straight on to payment.
  - **Continue as guest**: goes straight to the payment page.
  Signed-in buyers skip the question.
- **Guest routine on the payment page:** **Your details** (email and phone number) above the
  card / UPI form, and a **Pay now · $…** button. Pay now checks the details first (valid email;
  7 to 20 digits, spaces, brackets, dashes, optional `+`), then the payment, then confirms. A
  signed-in buyer's **Your details** shows their name and email instead, with nothing to type.
- **Guest contact on the booking:** `POST /api/holds/{id}/confirm` takes an optional body
  `{contactEmail, contactPhone}`, validated on the server and stored on the booking
  (`contact_email`, `contact_phone`), never returned by the API. Ignored for signed-in buyers.
  A guest who leaves without paying leaves no contact details behind.
- **Booking confirmation email** ("Your SeatLock booking SL-…": reference, concert, venue, time
  in UTC, seats, total, site link) sent after the booking commits, to the account's email for a
  signed-in buyer or to the guest's email (`BookingMailer`). The confirmation page says where it
  was sent.
- **Test data section** in `docs/TESTING.md`: demo login, guest contact examples, test cards and
  UPI IDs, how emails behave without a mail server, booking lookup.

### Changed

- **Opening the site always starts on the page in the address**: a plain link opens the home
  page and never the log-in or payment page. Only reloading the payment page (`#checkout`)
  brings back a payment in progress; otherwise an unfinished payment's seats are released at once.
- The cart no longer shows "Log in first to keep this booking"; the dialog replaces it.
- The payment button reads **Pay now · $…**.
- **Personal data rule:** guests now give an email and phone number when they pay, kept with
  their booking (README, CLAUDE.md and the Privacy page updated).
- Version 0.8.0 in `pom.xml`, the OpenAPI spec and the `?v=` file links. The account
  notifications (watchlist, "booking opens" alerts, reminders, "selling fast" emails) move to a later
  version (0.9.0 became tickets and Find my tickets).

### Fixed

- **Phone numbers were never checked in the browser** (sign-up, profile and now Your details):
  the phone pattern used `(` and `)` unescaped inside `[...]`, which browsers reject when they
  compile patterns with the `v` flag, and an invalid pattern is silently ignored. The server
  still refused bad numbers, so only the error message was poor ("Invalid request content").

### Known limitations

- No text messages: the phone number is stored, but booking details go by email only.
- Without a mail server (`SPRING_MAIL_*`), confirmation emails are skipped with a log line.

### Tested

| What | How | Result |
|---|---|---|
| Full test suite | `./mvnw clean test` | 127 tests, 0 failures (5 new in `AccountIntegrationTest`) |
| Guest confirmation email | GreenMail | Confirm with email and phone in the body → booking stores both → email arrives at the guest's address with reference, concert, venue, seat, total and link |
| Signed-in confirmation email | GreenMail | Goes to the account's email (greeting by name), not to a contact email sent in the body; the booking stores no contact |
| Contact validation | HTTP | Bad email or phone `400` with the hold still active; a valid pair then `201`; confirming without a body still books |
| Privacy | HTTP | Booking lookup by reference never returns the guest's email or phone |
| Guest routine in the browser | Headless Chrome | Home page on arrival, logged out; **Book now** → dialog with exactly **Log in**, **Sign up**, **Continue as guest** and no fields; **Continue as guest** → payment page with Order summary, Your details, Payment and **Pay now · $65.00**; Pay now with no email → "Enter your email address…" (cursor in Email); phone `call me` → "Enter your phone number…"; no card → "Enter the name on the card."; test card → "Processing payment…" → `SL-VDZC36`, "Booking details are emailed to guest.fan@example.com"; server log shows the confirmation email for `SL-VDZC36` (skipped, no SMTP) |
| Log-in routine in the browser | Headless Chrome | **Book now** → **Log in** → demo account → back with the same seat on the payment page; Your details shows "Demo Fan · demo@example.com" with no fields; UPI `success@demo` → `SL-FUCS7T`, "emailed to demo@example.com" |
| Queue as a guest | Headless Chrome | **Join the queue instead** → **Continue as guest** → "You're in the queue…" → payment page with Your details |
| Landing | Headless Chrome | Plain address with an unpaid hold saved: home page, hold `RELEASED` on the server; reload of `#checkout`: payment page restored, hold still `ACTIVE` |
| Manual scenarios | `docs/TESTING.md` rows 13, 22, A7, A8, A19 updated, A18–A25 added; new "Test accounts and test data" section | |

---

## 0.7.0 — 2026-10-31

Home page blocks, and a demo payment page with card and UPI.

### Added

- **Demo payment page:** the payment step now has a **Card** form (name, number spaced in fours
  with the brand shown, expiry, CVC) and a **UPI** tab, a short "Processing payment…" step, and
  "Paid with Visa ending 4242" (or the UPI ID) on the confirmation.
  - Only published test details work: cards `4242 4242 4242 4242` and `5555 5555 5555 4444`
    (approved), `4000 0000 0000 0002` (declined), `4000 0000 0000 9995` (insufficient funds);
    UPI `success@demo` (approved) and `failure@demo` (declined). Any other card, including a
    real one, is refused. **Use a test card** / **Use a test UPI ID** fill the form in.
  - Card and UPI details are checked in the browser (number checksum, future expiry, CVC, UPI
    format) and are never sent to the server or stored; the form is cleared after payment. The
    server still only receives `POST /api/holds/{id}/confirm`.
  - A declined payment keeps the seats reserved so the buyer can try again before the timer ends.
  - New file `static/payment.js`; Help, Terms, Privacy and Payments pages describe it.

### Changed

- **Home blocks:** **Today's concerts**, **Tomorrow's concerts** and **Selling fast** are three
  blocks side by side (stacked on a phone) that show only how many concerts each has, with no
  concert details. Clicking a block opens its list with dates, venues, times and "from" prices;
  Selling fast also shows "% sold", most sold first. Each list has its own address (`#today`,
  `#tomorrow`, `#selling-fast`, and `#all` for **Browse all concerts**), so the browser's Back
  button returns from a concert to the list it came from. The rows inside the boxes, **See all**
  and the bars are gone.
- Version 0.7.0 in `pom.xml`, the OpenAPI spec and the `?v=` file links. The account
  notifications planned for 0.7.0 (watchlist, "booking opens" alerts, reminders, "selling fast"
  emails) move to a later version.

### Known limitations

- The demo payment runs in the browser, so `POST /api/holds/{id}/confirm` still books without
  a payment. A real provider must confirm bookings from its server-side notification instead.

### Tested

| What | How | Result |
|---|---|---|
| Full test suite | `./mvnw clean test` | 122 tests, 0 failures; `WebClientIntegrationTest` also checks `payment.js` is served |
| Home blocks | Headless Chrome | 3 blocks and no concert rows (Today 2, Tomorrow 2, Selling fast 6); **Tomorrow's concerts** → `#tomorrow` with 2 concerts → *Sunset Acoustic Sessions* with 816 of 1,632 seats free → Back returns to `#tomorrow` |
| Demo card payment | Headless Chrome, driving the form | Real-looking `4111 1111 1111 1111` refused; `4000 0000 0000 0002` shows "Processing payment…" (button disabled) then "declined" with the hold still active; empty name caught; test card books (`SL-9GMVHS`, "Paid with Visa ending 4242") |
| Demo UPI payment | Headless Chrome | `someone@okbank` refused, `failure@demo` declined, `success@demo` booked |
| Manual scenarios | `docs/TESTING.md` rows 1, 2, 15, 37, 38 updated, 43–50 added | |

---

## 0.6.0 — 2026-10-31

A minimal home page instead of one long strip of shows.

### Added

- **Home page** at `/`, focused on the concerts: "Quick, hassle-free concert tickets", "Find your
  next concert", a one-line description and a search box (concert, venue or city), then three
  boxes side by side (stacked on a phone), each with a count, 5 rows and **See all** for the rest:
  - **Today** and **Tomorrow**: start time, concert and venue; "Only N left" in red when nearly gone.
  - **Selling fast**: concerts with at least 40% of their seats gone, most sold first, with
    "% sold" and a bar.
  - **Browse all concerts** (or searching) shows one list of every concert on sale with date,
    venue, city and "from" price.
- **What we offer**: four tiles under the boxes (live seat maps, seats kept while you pay, book
  in seconds, fair on busy days).
- **Footer** on every page, with **Help & support** (FAQ), **Terms of use**, **Your data &
  privacy**, **Security** and **Payments** pages (`#help`, `#terms`, `#privacy`, `#security`,
  `#payments`, in `static/info.js`). They describe only what SeatLock does today: the checkout
  is a demo, no card details are taken, passwords are BCrypt hashes, reset links last 30 minutes.
  Leaving the payment page for one of them releases the seats.
  - Searching turns the sections into one list of matches.
- **Concert page**: any concert, including a Selling fast card, opens on its own page
  (`#event-N`, linkable) with **← All concerts** back to the home page. It opens on the live seat
  status: "43 of 290 seats free" (red when nearly gone) and Free / Held by others / Sold counts
  next to the legend, refreshed with the seat map every 5 seconds.
- **Seat totals in the event API**: `totalSeats`, `availableSeats` and `fromPriceCents` on every
  event response; `GET /api/events` is sorted soonest first and counts all events' seats in one
  grouped query.
- **Demo data**: two concerts starting a few hours after the app starts (*Open Mic Night*,
  *Vinyl After Dark*), two tomorrow evening (*Sunset Acoustic Sessions*, *Neon Nights Live*), and
  guest sales on six concerts (45–85% sold), so Today, Tomorrow and Selling fast are filled.
  20 concerts in total; ids 1–16 are unchanged.

### Changed

- The horizontal strip of show cards and the venue-type filter are gone; the seat map has the
  page to itself.
- **Back to shows** after paying, and the SeatLock logo, go to the home page. Leaving the payment
  page for the home page releases the seats, like Back and Cancel.
- The page loads `app.css`, `app.js` and `account.js` with `?v=0.6.0`, so browsers fetch the new
  files after an upgrade instead of showing the old page from cache. Bump it with each release.
- Version 0.6.0 in `pom.xml` and the OpenAPI spec.

### Tested

| What | How | Result |
|---|---|---|
| Full test suite | `./mvnw clean test` | 122 tests, 0 failures (3 new) |
| Seat totals | `EventListingIntegrationTest` (3) | New event fully on sale from the cheapest section; booked and held seats taken; a lapsed hold counts as on sale before the cleanup job; list soonest first |
| Running app | `curl /api/events` | 20 concerts; seeded sales match (e.g. *Late Night Jazz Sessions* 43 of 290 left) |
| Web client | Headless Chrome, desktop and phone width, dark mode | Home page boxes (Today 2, Tomorrow 2, Selling fast 6) with **See all** and **Browse all 20 concerts**; What we offer; footer link to **Payments** opens that page; the Security page; clicking the second Selling fast row opens *Tidewater* with 130 of 464 seats free; search for "lakeside" gives 4 concerts; clicking the top Selling fast card opens *Late Night Jazz Sessions* with 43 free, 0 held, 247 sold; **← All concerts** returns; a `#event-16` link opens that concert; clicking the logo on the payment page releases the hold (`RELEASED`) |
| Manual scenarios | `docs/TESTING.md` rows 1, 2, 15 updated, 27–42 added | |
| Bugs found and fixed | Screenshots | The footer's last line ignored its margin (a general rule overrode it); on short pages the footer floated halfway up the window, so the page now fills the window |
| Bug found and fixed | Screenshot of the concert page | The home page's `display: grid` overrode the `hidden` attribute, so a clicked concert opened below the still-visible home page. A global `[hidden] { display: none !important; }` fixes it for every page; `WebClientIntegrationTest` checks the rule is there |

---

## 0.5.0 — 2026-10-31

Optional buyer accounts. Phase 1 of 2: watchlist, "booking opens" alerts, show reminders and
"selling fast" emails come in a later version.

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
3. Set the version in `pom.xml` (`<version>`), in `config/OpenApiConfig.java` (`.version(...)`) and in the `?v=` of the three file links in `static/index.html`.
4. Commit, then tag the commit: `git tag -a v0.4.0 -m "SeatLock 0.4.0"` and
   `git push origin v0.4.0`.
