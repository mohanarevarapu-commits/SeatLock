# SeatLock test scenarios

This guide lists every scenario SeatLock is tested against: the automated tests that run on every
build, the manual checks for the web client, and the load test. For how the tests are built (test
profile, controllable clock, race harness) see the
[architecture guide](ARCHITECTURE.md#testing-strategy).

- [Test accounts and test data](#test-accounts-and-test-data)
- [Running the tests](#running-the-tests)
- [Automated scenarios](#automated-scenarios)
  - [Seat and hold rules](#seat-and-hold-rules)
  - [Placing and releasing holds](#placing-and-releasing-holds)
  - [Checkout and bookings](#checkout-and-bookings)
  - [Hold expiry](#hold-expiry)
  - [Concurrency and races](#concurrency-and-races)
  - [Queued checkout](#queued-checkout)
  - [Rate limiting](#rate-limiting)
  - [HTTP API](#http-api)
  - [Venue layouts](#venue-layouts)
  - [Seats and booking references](#seats-and-booking-references)
  - [Web client delivery](#web-client-delivery)
  - [Accounts, sign-in and email](#accounts-sign-in-and-email)
- [Manual scenarios: web client](#manual-scenarios-web-client)
- [Manual scenarios: accounts](#manual-scenarios-accounts)
- [Load test scenario](#load-test-scenario)
- [Not covered yet](#not-covered-yet)

## Test accounts and test data

Everything you need to try SeatLock by hand. Start the app with demo data
(`./mvnw spring-boot:run -Dspring-boot.run.profiles=demo`) and open http://localhost:8080. The
demo data, including any bookings you make, resets every time the app restarts.

**Log in (demo account)**

| Field | Value |
|---|---|
| Email | `demo@example.com` |
| Password | `seatlock-demo` |

It has two bookings under **My bookings** (*The Glass Garden* and *Late Night Jazz Sessions*).
**Sign up** works with any email and a password of 8 or more characters.

**Book as a guest**

**Book now** → **Continue as guest**; on the payment page, **Your details** asks for an email
and a phone number, then **Pay now**. Any well-formed values work, for example:

| Field | Example | Rules |
|---|---|---|
| Email | `guest.fan@example.com` | A valid email address |
| Phone | `+91 98765 43210` or `+44 20 7946 0000` | 7 to 20 digits, spaces, brackets and dashes, optionally starting with `+` |

**Pay (demo payment page)**

| Method | Test detail | Result |
|---|---|---|
| Card | `4242 4242 4242 4242` | Approved |
| Card | `5555 5555 5555 4444` | Approved |
| Card | `4000 0000 0000 0002` | Declined |
| Card | `4000 0000 0000 9995` | Insufficient funds |
| UPI | `success@demo` | Approved |
| UPI | `failure@demo` | Declined |

For cards use any name, any future expiry (e.g. `12/27`) and any CVC (e.g. `123`); **Use a test
card** fills one in. Any other card or UPI ID, real or not, is refused.

**Emails**

Booking confirmations, welcome, reset-link and password-changed emails are sent only when a mail
server is set (`SPRING_MAIL_*`, see the README). Without one, the app log shows
`No mail server configured (spring.mail.host); not sending "Your SeatLock booking SL-…"` instead.
Use an address you can read when you test real email.

**Find a booking**

Every booking gets a reference such as `SL-Q4XKJV`. Enter it under **Find a booking** on any
concert page to see its seats and total.

## Running the tests

```bash
./mvnw clean test                                                      # all 119 tests
./mvnw test -Dtest=ConcurrentHoldIntegrationTest                       # one class
./mvnw test -Dtest=HoldServiceIntegrationTest#cannotHoldASeatSomeoneElseHolds   # one scenario
```

The tests need no database, broker, mail account or network: they use in-memory H2, the embedded
Artemis broker, and GreenMail, an in-memory SMTP server that receives the account emails. Stop a running app before `./mvnw clean test`, because `clean` deletes the classes it runs from.

| Test class | Kind | Tests |
|---|---|---|
| `model/EventSeatTest` | Unit | 7 |
| `model/HoldTest` | Unit | 3 |
| `model/SeatTest` | Unit | 11 |
| `model/VenueLayoutTest` | Unit | 9 |
| `model/AccountRulesTest` | Unit | 3 |
| `ratelimit/RateLimitingFilterTest` | Unit | 6 |
| `service/BookingReferenceGeneratorTest` | Unit | 22 |
| `controller/HoldControllerTest` | HTTP slice (`@WebMvcTest`, mocked services) | 8 |
| `controller/AccountIntegrationTest` | Full app over HTTP, GreenMail | 21 |
| `controller/ReservationFlowIntegrationTest` | Full app over HTTP | 2 |
| `controller/WebClientIntegrationTest` | Full app over HTTP | 2 |
| `service/HoldServiceIntegrationTest` | Full app, H2 | 9 |
| `service/BookingServiceIntegrationTest` | Full app, H2 | 6 |
| `service/HoldExpiryServiceIntegrationTest` | Full app, H2 | 3 |
| `service/ConcurrentHoldIntegrationTest` | Full app, H2, 200 threads | 3 |
| `service/QueuedCheckoutIntegrationTest` | Full app, H2, embedded broker | 7 |
| `service/VenueLayoutIntegrationTest` | Full app, H2 | 2 |
| `service/EventListingIntegrationTest` | Full app, H2 | 3 |
| **Total** | | **127** |

Parameterised and repeated tests count once per run, so `SeatTest` has 3 methods but 11 runs, and
`BookingReferenceGeneratorTest` has 3 methods but 22 runs.

## Automated scenarios

Each table lists the situation, what must happen, and the test method that checks it.

### Seat and hold rules

Plain unit tests of the rules on the entities, with no Spring and no database.

| Scenario | Expected | Test |
|---|---|---|
| A new event seat | It is `AVAILABLE` | `EventSeatTest.newSeatIsAvailable` |
| A seat is held | Unavailable until the hold's expiry time, available from that moment on | `EventSeatTest.heldSeatIsUnavailableUntilHoldLapses` |
| A second hold tries to take a held seat | Refused | `EventSeatTest.cannotHoldSeatThatIsAlreadyHeld` |
| A hold has lapsed and another buyer wants the seat | The new hold takes it over | `EventSeatTest.lapsedHoldCanBeTakenOverByAnotherHold` |
| Booking a seat | Only allowed for the hold that currently holds it, while that hold is live | `EventSeatTest.bookingRequiresALiveHoldByTheSameHold` |
| Releasing a seat on behalf of a hold that no longer owns it | The seat is left alone | `EventSeatTest.releaseOnlyAffectsTheOwningHold` |
| Releasing a booked seat | It stays `BOOKED` | `EventSeatTest.releaseNeverUnbooksASeat` |
| A new hold | Starts `ACTIVE`, seat ids stored in sorted order | `HoldTest.startsActiveWithSortedSeatIds` |
| The clock reaches the hold's expiry time | It counts as expired at exactly that instant | `HoldTest.expiresExactlyAtExpiryTime` |
| A confirmed, released or expired hold is changed again | Refused | `HoldTest.terminalStatesCannotTransitionAgain` |

### Placing and releasing holds

`HoldServiceIntegrationTest`, against the full app and database.

| Scenario | Expected | Test |
|---|---|---|
| A buyer holds free seats | Hold is `ACTIVE` for 10 minutes and the seats show as `HELD` | `holdReservesSeatsForTenMinutes` |
| A buyer asks for a seat someone else is holding | Refused with the conflicting seat ids (`409`) | `cannotHoldASeatSomeoneElseHolds` |
| A hold has lapsed but the background sweeper has not run yet | A new buyer can still take the seats | `lapsedHoldDoesNotBlockNewHoldsEvenBeforeTheSweeperRuns` |
| A buyer releases a hold | The seats are available again | `releasingAHoldFreesItsSeats` |
| A lapsed hold is released after another buyer took its seats | The other buyer keeps the seats | `releasingALapsedHoldDoesNotFreeSeatsNowHeldBySomeoneElse` |
| More than 8 seats in one hold | Refused (`400`) | `rejectsTooManySeats` |
| A seat id that belongs to a different event | Refused | `rejectsSeatsFromAnotherEvent` |
| A hold attempt after the show has started | Refused | `rejectsHoldsOnceTheEventHasStarted` |
| A hold placed less than 10 minutes before the show | Expiry is capped at the start time | `holdNeverOutlivesTheEventStart` |

### Checkout and bookings

`BookingServiceIntegrationTest`.

| Scenario | Expected | Test |
|---|---|---|
| A buyer confirms an active hold | A booking with a reference is created and the seats are `BOOKED` | `confirmingAHoldBooksItsSeats` |
| Time passes beyond when the hold would have expired | Booked seats stay booked | `bookedSeatsStayTakenAfterTheHoldWouldHaveExpired` |
| The same hold is confirmed twice | The second attempt is refused; there is only one booking | `cannotConfirmTwice` |
| Confirming a hold the buyer already released | Refused | `cannotConfirmAReleasedHold` |
| Confirming after the 10 minutes ran out | Refused as expired (`410`) | `cannotConfirmALapsedHold` |
| Looking up a booking reference that does not exist | Not found (`404`) | `unknownBookingReferenceIsNotFound` |

### Hold expiry

`HoldExpiryServiceIntegrationTest`. The sweeper is switched off in tests, so each test moves the
clock forward and runs one sweep directly.

| Scenario | Expected | Test |
|---|---|---|
| Holds have lapsed | The sweep marks them `EXPIRED` and frees their seats | `sweeperExpiresLapsedHoldsAndFreesSeats` |
| A lapsed hold's seats were already taken by a newer hold | The sweep expires the old hold but leaves the newer hold's seats alone | `sweeperLeavesSeatsThatWereReheldByANewerHold` |
| A confirmed hold is past its expiry time | The sweep ignores it; the seats stay booked | `sweeperIgnoresConfirmedHolds` |

### Concurrency and races

`ConcurrentHoldIntegrationTest`. Each race starts 200 threads at the same moment against real
database transactions.

| Scenario | Expected | Test |
|---|---|---|
| 200 buyers want the same 2 seats at once | Exactly one wins; all others get a clean "seats unavailable" refusal | `exactlyOneCustomerWinsTheSameSeats` |
| 200 buyers each want 3 neighbouring seats, so their requests overlap | No seat is held twice, nothing deadlocks, and the seat map matches the winners | `overlappingRequestsNeverDoubleHoldAndNeverDeadlock` |
| 200 buyers fight over the same 40 seats, retrying when beaten; a third confirm, a third release, a third keep the hold | Each seat ends in at most one booking or active hold, and the seat map's `BOOKED` and `HELD` counts match exactly | `mixedHoldConfirmAndReleaseTrafficSellsEachSeatAtMostOnce` |

### Queued checkout

`QueuedCheckoutIntegrationTest`, through the real embedded Artemis broker. A Mockito spy on
`HoldService` simulates database failures.

| Scenario | Expected | Test |
|---|---|---|
| A buyer queues a request for free seats | Accepted as `QUEUED`, then `HELD` with an active 10-minute hold | `queuedRequestEndsUpHoldingTheSeatsForTenMinutes` |
| 20 buyers queue for the same seats | Exactly 1 `HELD`, 19 `REJECTED` with "Seats no longer available" | `competingRequestsForTheSameSeatsHoldThemExactlyOnce` |
| A request for an event that does not exist | `REJECTED` straight away and tried only once | `unknownEventIsRejectedWithoutRetrying` |
| The database fails twice (lock timeout), then works | Retried by the broker and ends `HELD` after 3 attempts | `transientFailureIsRetriedUntilItSucceeds` |
| The database keeps failing | Tried 4 times, moved to the dead-letter queue, marked `FAILED`; no seats taken | `persistentFailureEndsOnTheDeadLetterQueueAsFailed` |
| The same message is delivered twice | Processed once; the second delivery is skipped | `redeliveredMessageIsNotProcessedTwice` |

### Rate limiting

`RateLimitingFilterTest`, a unit test with a controllable clock so token refills are exact.

| Scenario | Expected | Test |
|---|---|---|
| One address sends 6 checkout requests within a second | The first 5 pass; the 6th gets `429` with `Retry-After` | `allowsFiveRequestsPerSecondThenRejectsTheSixth` |
| Time passes after the limit was hit | Tokens come back at 5 per second | `refillsAtFiveTokensPerSecond` |
| Two addresses send requests | Each has its own limit | `eachIpHasItsOwnLimit` |
| One address mixes hold and confirm requests | They share one limit | `holdAndConfirmShareOneLimit` |
| Requests to non-checkout endpoints (seat map, events) | Never limited | `otherEndpointsAreNotLimited` |
| The limiter is switched off in configuration | Everything passes | `disabledFilterLetsEverythingThrough` |

### HTTP API

`HoldControllerTest` checks status codes and response shapes with mocked services;
`ReservationFlowIntegrationTest` drives the real app over HTTP.

| Scenario | Expected | Test |
|---|---|---|
| Valid hold request | `201 Created` with a `Location` header | `HoldControllerTest.createHoldReturns201WithLocation` |
| Hold request with a missing or invalid body | `400` with validation errors | `HoldControllerTest.createHoldValidatesTheBody` |
| Seats already taken | `409` listing the taken `eventSeatIds` | `HoldControllerTest.takenSeatsReturn409WithTheConflictingIds` |
| A concurrent update conflict | `409` telling the client to retry | `HoldControllerTest.lockConflictsAskTheClientToRetry` |
| Confirm succeeds | `201` with the booking's `Location` | `HoldControllerTest.confirmReturns201WithBookingLocation` |
| Confirm an expired hold | `410 Gone` | `HoldControllerTest.confirmingAnExpiredHoldReturns410` |
| Release a hold that is no longer active | `409` | `HoldControllerTest.releasingAnInactiveHoldReturns409` |
| Unknown hold id | `404` | `HoldControllerTest.unknownHoldReturns404` |
| Full journey: create venue, create event, read seat map, hold, confirm, fetch booking | Every step succeeds and the seat map changes from available to held to booked | `ReservationFlowIntegrationTest.customerCanHoldAndBookSeats` |
| Creating an event in the past | `400` | `ReservationFlowIntegrationTest.eventsInThePastAreRejected` |

### Venue layouts

Venue type, stage position and section zones, from `VenueLayoutTest` (unit) and
`VenueLayoutIntegrationTest`.

| Scenario | Expected | Test |
|---|---|---|
| Section added without a zone, for each stage position | It goes across from the stage: north ↔ south, west ↔ east, corner ↔ opposite corner; a centre stage puts it south | `VenueLayoutTest.sectionsFaceTheStageFromAcrossTheMap` (7 cases) |
| Function hall with the stage on the east wall, section without a zone | The section is placed west, facing the stage | `VenueLayoutTest.sectionWithoutZoneGoesAcrossFromTheStage` |
| A section placed in the stage's own zone | Refused (`400`) | `VenueLayoutTest.sectionCannotSitWhereTheStageIs` |
| A venue created with no layout fields (older API clients) | Arena, end stage at the north labelled "STAGE", sections south | `VenueLayoutIntegrationTest.venueWithoutLayoutIsAnArenaWithAnEndStage` |
| A stage in the round with sections north, west and one unplaced | The event and seat map carry the venue type, city, stage zone and label, and every section's zone | `VenueLayoutIntegrationTest.stageInTheRoundLayoutReachesTheSeatMap` |

### Show list and seat totals

`EventListingIntegrationTest`. The home page's Selling fast and "Only N left" depend on these.

| Scenario | Expected | Test |
|---|---|---|
| A new event at a venue with a $80 and a $35 section | All 14 seats on sale, from $35 | `newEventHasEverySeatOnSaleFromTheCheapestSection` |
| 2 seats booked and 1 held, then the hold runs out before the cleanup job | 7 of 10 on sale; after the hold runs out, 8 of 10 (the lapsed seat counts as on sale) | `bookedAndHeldSeatsAreTakenButALapsedHoldIsOnSaleAgain` |
| Two events, created later-date first | The list has the sooner one first | `listIsSoonestFirst` |

### Seats and booking references

| Scenario | Expected | Test |
|---|---|---|
| Row numbers turn into labels | 1 → A, 26 → Z, 27 → AA, 53 → BA, 703 → AAA (9 cases) | `SeatTest.rowLabelUsesSpreadsheetStyleLetters` |
| Row number 0 or below | Refused | `SeatTest.rowLabelRejectsNonPositiveRows` |
| A venue with two sections | Builds the right grid of labelled seats and the right capacity | `SeatTest.venueBuildsSeatGridForEachSection` |
| Booking references are generated | Only characters that cannot be misread (no `0`, `O`, `1`, `I` or `L`), checked 20 times | `BookingReferenceGeneratorTest.referencesUseOnlyUnambiguousCharacters` |
| Same random seed | Same references | `BookingReferenceGeneratorTest.seededGeneratorIsDeterministic` |
| 10,000 references | More than 9,990 are distinct | `BookingReferenceGeneratorTest.referencesRarelyCollide` |

### Web client delivery

`WebClientIntegrationTest`.

| Scenario | Expected | Test |
|---|---|---|
| A browser opens `/` | It gets the web client page | `rootServesTheWebClient` |
| The page loads its scripts and styles | `app.js`, `payment.js`, `account.js`, `info.js` and `app.css` are served; the stylesheet keeps the rule that makes `hidden` pages invisible | `scriptsAndStylesAreServed` |

### Accounts, sign-in and email

`AccountIntegrationTest` drives the full app over HTTP, sending the session cookie and CSRF token
the way a browser does. Emails go to GreenMail, an in-memory SMTP server, and each test checks
only the emails sent to its own address. `AccountRulesTest` covers the entity rules.

| Scenario | Expected | Test |
|---|---|---|
| A buyer signs up with name, email, phone and password | `201`; signed in straight away; the profile shows name, email and phone but never the password hash; a "Welcome to SeatLock" email arrives | `signUpCreatesTheAccountSignsInAndSendsAWelcomeEmail` |
| Signing up again with the same email in capitals | `409 Email already registered` | `emailIsRegisteredOnlyOnceWhateverItsCase` |
| Password shorter than 8 characters, or a phone that is not a number | `400` | `signUpValidatesPasswordLengthAndPhone` |
| Wrong password; unknown email | Both get the same `401` "Email or password is incorrect." | `wrongPasswordAndUnknownEmailGetTheSameAnswer` |
| Log in with the email in capitals, then log out | Logged in; after logout the profile answers `401` | `signInIsCaseInsensitiveAndSignOutEndsTheSession` |
| Profile or My bookings without a session | `401 Sign-in required` | `accountEndpointsNeedASignIn` |
| A signed-in change without the CSRF header (a forged request) | `403`; nothing changes | `signedInChangesNeedTheCsrfToken` |
| Change name, clear the phone | Saved; phone removed | `buyerCanChangeNameAndPhone` |
| Forgot password, then follow the emailed link | Reset email arrives with a link; new password works, old one does not; "password changed" email arrives | `forgottenPasswordIsResetThroughTheEmailedLink` |
| Forgot password for an email with no account | Same `202`; no email sent | `unknownEmailGetsTheSameAnswerAndNoEmail` |
| The same reset link used twice | Second use `400` "invalid, already used or expired" | `resetLinkWorksOnlyOnce` |
| A reset link used after 30 minutes | `400`; the old password still works | `resetLinkExpiresAfterThirtyMinutes` |
| Two reset links asked for in a row | Only the newest works | `onlyTheNewestResetLinkWorks` |
| A buyer books; a guest books at the same show; then the show happens | My bookings shows only the buyer's booking, first under upcoming (with show, venue and seats), then under past | `myBookingsListsOnlyTheBuyersOwnBookingsSplitIntoUpcomingAndPast` |
| A signed-in buyer's hold, tried by a guest and by another buyer | `404` for both (view, confirm, release); the owner can view and confirm it | `aSignedInBuyersHoldCanOnlyBeUsedByThatBuyer` |
| A guest hold | Still works without any account | `guestHoldsStillWorkWithoutAnAccount` |
| A signed-in buyer uses the queued checkout | The hold belongs to their account; a guest cannot see it | `QueuedCheckoutIntegrationTest.signedInBuyersQueuedRequestIsHeldForTheirAccount` |
| Email with spaces and capitals | Stored trimmed and lower-case | `AccountRulesTest.emailIsStoredTrimmedAndLowerCase` |
| A reset token | Works until it expires or is used, not after | `AccountRulesTest.resetTokenWorksUntilItExpiresOrIsUsed` |
| Guest hold vs account hold | A guest hold is open to anyone with its id; an account hold only to its owner | `AccountRulesTest.guestHoldIsOpenToAnyoneButAnAccountHoldOnlyToItsOwner` |
| A guest confirms with an email and phone in the body, as the payment page does | The booking stores both; a "Your SeatLock booking SL-…" email arrives at the guest's address with the reference, concert, venue, seat, total and site link | `guestGivesEmailAndPhoneWhenPayingAndGetsTheBookingEmail` |
| A signed-in buyer confirms, sending a different contact email | The confirmation goes to the account's email (greeting by name), nothing to the other address; the booking stores no contact | `signedInBookingEmailsTheAccountAddressNotTheContactGiven` |
| Confirm with email `not-an-email`, or phone `call me` | `400` and nothing is booked (the hold stays active); a valid pair then gets `201` | `guestContactDetailsAreValidatedBeforeAnythingIsBooked` |
| An API client confirms without a body | Still `201`; the booking has no contact details and no email is sent | `confirmWithoutContactDetailsStillBooksForApiClients` |
| Looking up a guest's booking by reference | The answer has no `contactEmail` or `contactPhone` and never contains the email | `bookingLookupNeverShowsTheGuestsContactDetails` |

## Manual scenarios: web client

The page's behaviour in a browser is checked by hand. Start the app with demo data and open
http://localhost:8080:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

| # | Scenario | Steps | Expected |
|---|---|---|---|
| 1 | Home page | Open the page | "Quick, hassle-free concert tickets", "Find your next concert", a one-line description and a search box (no venue list); three blocks side by side with no concert details, only counts: **Today's concerts** 2, **Tomorrow's concerts** 2, **Selling fast** 6 (in red); **Browse all 20 concerts** below |
| 2 | Selling fast opens the concert | Click **Selling fast**, then *Late Night Jazz Sessions* | Its page opens at the top (not below the home page) with "43 of 290 seats free" and Free 43, Held by others 0, Sold 247; only the free seats on the map can be clicked |
| 3 | End stage | Open *The Placeholder Tour* | Stage at the top; Floor, Lower Bowl and Upper Bowl below it, row A nearest the stage |
| 4 | Stage in the round | Open *Echo Circle Live* | Round stage in the middle; stands on all four sides and in the corners, each facing it |
| 5 | Stadium | Open *Summer Stadium Fest* | Main stage at the north end, pitch seats in the middle, side stands facing inward; map zoomed to fit |
| 6 | Cricket ground | Open *Boundary Beats Open Air* | Green pitch stage in the middle with stands all round |
| 7 | Opera house | Open *The Glass Garden* | Stalls in front of the stage, boxes along both walls starting at the stage, tiers behind the stalls |
| 8 | Stage at the south | Open *Requiem for a Paper Moon* | Stage at the bottom; stalls above it with row A at the bottom |
| 9 | Side and corner stages | Open *Fairground Folk Weekend*, *Golden Hour Gala Dinner* and *Late Night Jazz Sessions* | Stage on the west wall, the east wall and in a corner; seats turned towards it |
| 10 | Zoom | On the stadium, click **+**, **−** and **Fit** | The map zooms in and out; **Fit** shows the whole venue again |
| 11 | Select seats | Click 3 available seats | They turn purple; the panel lists them with prices and the total |
| 12 | Seat limit | Try to select a 9th seat | Refused with "You can hold up to 8 seats at a time" |
| 13 | Book now | Logged out: select seats, click **Book now**, then **Continue as guest** | Payment page opens: a 10:00 countdown (red in the last minute), the order summary, **Your details** (email, phone), the demo payment form, **Pay now · $…** and **Cancel and go back** |
| 14 | Reload during payment | Reload the page on the payment page | The payment page comes back with the same countdown |
| 15 | Pay | Click **Use a test card**, then **Pay** | "Booking confirmed" page with the reference (e.g. `SL-GUMGX3`); **Back to shows** opens the home page, and the show's seats are sold when you open it again |
| 16 | Find a booking | Enter the reference under **Find a booking** | The booking's seats and total appear; an unknown reference says no booking was found |
| 17 | Cancel payment | On the payment page click **Cancel and go back** | Back on the seat map, "Booking cancelled"; the seats are available again |
| 18 | Browser Back during payment | On the payment page press the browser's Back button | Same as cancelling: back on the seat map and the seats are available again |
| 19 | Payment time runs out | Book, then wait 10 minutes without paying | Sent back to the seat map with "Time's up. Your seats were released"; the seats are available again. To try it quickly, start the app with `-Dspring-boot.run.arguments=--seatlock.holds.ttl=PT20S` |
| 20 | Two buyers, same seats | In a normal and a private window, select the same seats; click **Book now** in one, then the other | The first gets the payment page; the second is told which seats were just taken |
| 21 | Live updates | Book seats in one window and watch another window on the same show | Within 5 seconds the seats turn orange (held by others), then sold after payment or available again after a cancel |
| 22 | Queued checkout | Select seats, click **Join the queue instead** (logged out: then **Continue as guest**) | "You're in the queue…", then the payment page opens with a countdown and **Your details** |
| 23 | Rate limit | Run `for i in $(seq 10); do curl -s -o /dev/null -w '%{http_code} ' -X POST localhost:8080/api/holds/999999/confirm; done` | Five `404`s (unknown hold), then `429`s: the 6th checkout request within a second is refused |
| 24 | Server down | Stop the app while the page is open | Within 5 seconds: "Could not reach SeatLock. Is the server running?" |
| 25 | Phone width | Open the page on a phone or a narrow window | One column, no sideways page scroll; the seat map zooms to fit |
| 26 | Dark and light mode | Switch the system appearance | Page colours follow it; sold seats stay distinguishable |
| 27 | Search | Type `lakeside` in the search box | "4 concerts": the Lakeside ones only, as one list; clearing the box brings the sections back |
| 28 | Search with no match | Type `zzz` | "0 concerts" and "Nothing matches. Try another concert, venue or city." |
| 29 | Open a show | Click any card or row | The show's page with its seat map; the address is `#event-N` |
| 30 | Back to all concerts | Click **← All concerts** (or the SeatLock logo) | The home page again, with fresh seat counts |
| 31 | Link to a show | Open `http://localhost:8080/#event-12` in a new tab | *Tidewater* opens directly, not the home page |
| 32 | Selling fast updates | Book seats on a show, then go back to the home page | Its "left" count and bar reflect the sale |
| 33 | Nearly sold out | Book a show down to 20 or fewer seats (or 10% of the venue) | "Only N left" in red on its card and row |
| 34 | Leave payment for the home page | On the payment page click the SeatLock logo | Home page, "Booking cancelled"; the seats are on sale again |
| 35 | Home page on a phone | Narrow window | The three boxes stack (Today, Upcoming, Selling fast); rows stay readable; no sideways page scroll |
| 37 | Open a block | Click **Tomorrow's concerts** | Address `#tomorrow`; "Tomorrow's concerts" lists *Sunset Acoustic Sessions* and *Neon Nights Live* with date, venue, time and "from" price; **Selling fast** lists its 6 most sold first with "% sold" |
| 38 | Browse all | Click **Browse all 20 concerts** | Address `#all`; every concert on sale in date order; **← Back** returns to the blocks |
| 44 | Demo payment: real card refused | On the payment page enter a real-looking card, e.g. `4111 1111 1111 1111`, a future expiry and a CVC, then **Pay** | "This is a demo checkout, so only test cards work"; nothing is booked and the seats stay reserved |
| 45 | Demo payment: declined | Enter `4000 0000 0000 0002`, then **Pay** | "Processing payment…" for about 1.5 seconds, then "Your card was declined"; the timer keeps running and you can try again |
| 46 | Demo payment: insufficient funds | Enter `4000 0000 0000 9995` | "Your card has insufficient funds" |
| 47 | Demo payment: form checks | Leave the name empty, use expiry `01/20`, or a 2-digit CVC | "Enter the name on the card", "Enter an expiry date in the future", "Enter the 3 or 4 digit CVC"; the number is spaced in fours and the brand (Visa, Mastercard…) shows under it |
| 48 | Demo payment: approved card | **Use a test card**, then **Pay** | "Booking confirmed" with the reference and "Paid with Visa ending 4242 · demo payment, no money was taken" |
| 49 | Demo payment: UPI | **UPI** tab: `someone@okbank`, then `failure@demo`, then `success@demo` | Refused (only test UPI IDs), then declined, then booked with "Paid with UPI (success@demo)" |
| 50 | Card details stay in the browser | Open the browser's developer tools, Network tab, and pay with the test card | The only request sent when paying is `POST /api/holds/{id}/confirm`, with no body: no card or UPI details leave the page |
| 43 | Block, concert, Back | Open **Tomorrow's concerts**, click a concert, then the browser's Back button | The concert opens with its live seat status; Back returns to the Tomorrow list, not the blocks |
| 39 | What we offer | Scroll below the boxes | Four tiles: live seat maps, seats kept while you pay, book in seconds, fair on busy days |
| 40 | Footer pages | Click **Help & support**, **Terms of use**, **Your data & privacy**, **Security** and **Payments** in the footer | Each opens its page (`#help` … `#payments`) with a side menu to the others and **← Back to concerts**; the text matches how SeatLock works (demo payment, no card details, BCrypt, 30-minute reset links) |
| 41 | Footer on short pages | Open the log-in page on a tall window | The footer sits at the bottom of the window, not halfway up |
| 42 | Leave payment for a help page | On the payment page click **Help & support** in the footer | The help page opens, "Booking cancelled", and the seats are on sale again |
| 36 | Seat status stays live | Keep a concert open; book seats on it in a private window | Within 5 seconds the free count drops and held (then sold) goes up |

## Manual scenarios: accounts

Start the app with demo data. The demo account is **demo@example.com** / **seatlock-demo**. To
check real emails, set the `SPRING_MAIL_*` variables first (see the README).

| # | Scenario | Steps | Expected |
|---|---|---|---|
| A1 | Logged-out corner | Open the page | **Log in** and **Sign up** in the top-right corner |
| A2 | Log in | **Log in**, enter the demo account | "Welcome back, Demo."; the corner shows **DF Demo** and you return to the show you were on |
| A3 | Wrong password | Log in with a wrong password | "Email or password is incorrect." under the form; still logged out |
| A4 | Profile menu | Click **DF Demo** | Menu with name, email and phone, then **My bookings**, **Profile**, **Log out**; closes on Escape or a click elsewhere |
| A5 | My bookings | Menu → **My bookings** | **Active (2)** with the opera and jazz bookings (show, venue, date, seats, total, reference); **Past (0)** |
| A6 | Edit profile | Menu → **Profile**, change the name, **Save changes** | "Profile saved."; new initials and name in the corner |
| A7 | Book while signed in | Pick seats, **Book now** | No question asked: the payment page opens, and **Your details** shows the account's name and email (nothing to type). After **Pay now**, the confirmation says the booking is saved under My bookings and emailed to the account's address; **View my bookings** lists it |
| A8 | Book as a guest | Log out, pick seats, **Book now** | "How would you like to book?" with three buttons: **Log in**, **Sign up**, **Continue as guest**, and no form fields. **Continue as guest** opens the payment page with **Your details** (email, phone). Fill them in, use a test card, **Pay now**: "Booking details are emailed to …"; the booking is not in anyone's My bookings |
| A18 | Opening the site | Open `http://localhost:8080` (logged out) | The home page, not the log-in page; nothing asks you to log in until you book |
| A19 | Guest details checked | On the payment page as a guest, **Pay now** with no email; then email `guest.fan@example.com` and phone `call me` | "Enter your email address, so we can send your booking details." (cursor in Email), then "Enter your phone number, such as +44 20 7946 0000." (cursor in Phone); no payment is attempted and the timer keeps running |
| A20 | Log in from Book now | Pick seats, **Book now** → **Log in**, log in with the demo account | Back on the concert with the same seats, and straight on to the payment page |
| A21 | Sign up from Book now | Pick seats, **Book now** → **Sign up**, create an account | Signed in, then straight on to the payment page with the same seats |
| A22 | Close the question | **Book now**, then × or Escape | The dialog closes; the seats stay selected; nothing is held |
| A23 | Guest confirmation email | With SMTP set up, book as a guest with your own email | "Your SeatLock booking SL-…" arrives with the reference, concert, venue, time (UTC), seats and total |
| A24 | Left payment, came back later | Book now, then open `http://localhost:8080` in the same browser | The home page opens (not the payment page) and those seats are released straight away |
| A25 | Reload the payment page | On the payment page, reload | The payment page comes back with the same countdown |
| A9 | Sign up | **Sign up** with name, email, phone, password | Signed in straight away; welcome email arrives (with SMTP set up) |
| A10 | Duplicate sign-up | Sign up again with the same email | "An account with this email already exists…" |
| A11 | Forgot password | **Log in** → **Forgot password?**, enter your email | "Check your email"; the reset email arrives within a minute (with SMTP set up) |
| A12 | Reset password | Open the emailed link | "Choose a new password" page; after saving: "Password changed. Log in with your new password."; "password changed" email arrives |
| A13 | Reused or old link | Open the same link again, or after 30 minutes | "This reset link is invalid, already used or expired." |
| A14 | Log out | Menu → **Log out** | "You're logged out."; corner shows **Log in** / **Sign up**; My bookings asks you to log in |
| A15 | My bookings needs a session | Logged out, open `http://localhost:8080/#bookings` | Log-in page; after logging in, My bookings opens |
| A16 | Leaving payment for log-in | During payment, click **Log in** | Seats released ("Booking cancelled"), log-in page shown |
| A17 | Phone width | Narrow window, logged in | Corner shows only the initials; menu and My bookings fit the screen |

## Load test scenario

`load-test/locustfile.py` simulates 500 concert fans against a running app for 2 minutes:

- 5 in 10 actions load the seat map;
- 4 in 10 hold 1 to 4 seats, mostly in the front sections where everyone competes, then pay or
  (15% of the time) abandon and release the hold;
- 1 in 10 uses the queued checkout and polls for the result.

A `409` (someone else got the seat) counts as expected, not as a failure. When the run ends, the
script checks that no seat was sold twice and compares its count with the seat map's `BOOKED`
total. It targets event 1 (Riverside Arena, 5,000 seats).

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo -Dspring-boot.run.arguments=--seatlock.rate-limit.enabled=false
pip install -r load-test/requirements.txt
locust --config load-test/locust.conf
```

Pass criteria: 0 failed requests and `RESULT: PASS - no seat sold twice`. The last recorded run
sold all 5,000 seats with 0 failures and 0 seats sold twice (see the README for latency figures).

## Not covered yet

- **The web client has no automated browser tests.** Its scenarios above are checked by hand.
- **Real SMTP delivery** is not part of the test suite (GreenMail stands in for the mail server).
  Check it by hand with scenarios A9, A11, A12 and A23 once `SPRING_MAIL_*` is set.
- **Text messages:** guests give a phone number, but SeatLock has no SMS provider, so booking
  details go by email only.
- **Sessions after a password reset:** other sessions of that account stay signed in until they
  time out (2 hours idle).
- **MySQL** is not exercised by the test suite; the tests run on H2 in MySQL mode. Run the app
  with the `mysql` profile to check against a real MySQL 8.
- **Several app instances** sharing one database: the locking is database-side so it should hold,
  but the rate limit is per instance and the embedded broker is per instance.
- **Broker restart:** the embedded broker keeps messages in memory, so queued requests are lost
  if the app stops before processing them.
