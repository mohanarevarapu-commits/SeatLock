package com.seatlock.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.jayway.jsonpath.JsonPath;
import com.seatlock.dto.CreateEventRequest;
import com.seatlock.dto.CreateVenueRequest;
import com.seatlock.dto.CreateVenueRequest.SectionRequest;
import com.seatlock.dto.EventResponse;
import com.seatlock.dto.VenueResponse;
import com.seatlock.model.MapZone;
import com.seatlock.model.VenueType;
import com.seatlock.support.Emails;
import com.seatlock.support.IntegrationTest;
import com.seatlock.support.QrCodes;
import jakarta.mail.BodyPart;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The ticket email (QR code and venue details) and Find my tickets (email, phone and a code sent by
 * email), over HTTP. Emails go to GreenMail, an in-memory SMTP server; each test uses its own address.
 */
@AutoConfigureMockMvc
class TicketLookupIntegrationTest extends IntegrationTest {

    private static final Pattern CODE = Pattern.compile("Your SeatLock code is: (\\d{6})");
    private static final String PHONE = "+44 20 7946 0000";

    @RegisterExtension
    static final GreenMailExtension mailServer = new GreenMailExtension(ServerSetupTest.SMTP)
            .withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication())
            .withPerMethodLifecycle(false);

    @Autowired
    private MockMvc mvc;

    // ---- the ticket email ---------------------------------------------------------------------

    @Test
    void ticketEmailHasTheQrCodeVenueDetailsAndEverySeat() throws Exception {
        String email = uniqueEmail();
        EventResponse event = concertAtAnAddress();
        List<Long> seats = seatIds(event.id());
        String reference = bookAsGuest(event, List.of(seats.get(0), seats.get(1)), email, PHONE);

        MimeMessage ticket = awaitEmails(email, 1).get(0);
        assertThat(ticket.getSubject()).isEqualTo("Your SeatLock booking " + reference);
        String html = Emails.part(ticket, "text/html");
        assertThat(html).contains("Gala Concert", reference, "Crescent Hall", "12 Crescent Square", "Testville",
                "Opera house &middot; STAGE", "Stalls", ">A<", ">1<", ">2<", "$80.00", "$160.00", email, PHONE,
                "cid:ticket-qr", "Find my tickets", "http://seatlock.test/#tickets");
        assertThat(Emails.part(ticket, "text/plain")).contains("Booking reference: " + reference,
                "12 Crescent Square", "Stalls · Row A · Seat 1", "Stalls · Row A · Seat 2", "Total: $160.00");

        BodyPart qr = Emails.inlineImage(ticket, "ticket-qr");
        assertThat(qr.getContentType()).startsWith("image/png");
        assertThat(QrCodes.decode(qr.getInputStream().readAllBytes())).isEqualTo(reference);
    }

    @Test
    void qrCodeOfABookingIsServedAsAPng() throws Exception {
        EventResponse event = createEvent();
        String reference = bookAsGuest(event, List.of(seatIds(event.id()).get(0)), uniqueEmail(), PHONE);

        byte[] png = mvc.perform(get("/api/bookings/{reference}/qr", reference))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(QrCodes.decode(png)).isEqualTo(reference);

        mvc.perform(get("/api/bookings/SL-NOSUCH/qr")).andExpect(status().isNotFound());
    }

    // ---- Find my tickets ----------------------------------------------------------------------

    @Test
    void guestGetsACodeByEmailAndSeesOnlyTheirOwnTickets() throws Exception {
        String email = uniqueEmail();
        EventResponse later = createEvent();
        clock.advance(Duration.ofDays(-5));
        EventResponse sooner = createEvent();
        clock.advance(Duration.ofDays(5));
        String first = bookAsGuest(sooner, List.of(seatIds(sooner.id()).get(0)), email, PHONE);
        String second = bookAsGuest(later, List.of(seatIds(later.id()).get(0)), email, "+442079460000");
        bookAsGuest(later, List.of(seatIds(later.id()).get(1)), email, "+91 98765 43210");   // another phone
        bookAsGuest(later, List.of(seatIds(later.id()).get(2)), uniqueEmail(), PHONE);       // another email
        awaitEmails(email, 3);   // the three ticket emails

        // The email is matched whatever its case, the phone whatever its spacing.
        sendCode(email.toUpperCase(), "+44 (20) 7946-0000").andExpect(status().isAccepted());
        String code = code(awaitEmails(email, 4).get(3));

        lookUp(email, PHONE, code)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].reference").value(first))
                .andExpect(jsonPath("$[1].reference").value(second))
                .andExpect(jsonPath("$[0].eventName").value("Test Show"))
                .andExpect(jsonPath("$[0].venueName").value("Test Hall"))
                .andExpect(jsonPath("$[0].seats[0].row").value("A"))
                .andExpect(jsonPath("$[0].seats[0].number").value(1))
                .andExpect(jsonPath("$[0].totalCents").value(5_000));
    }

    @Test
    void aCodeWorksOnlyOnce() throws Exception {
        String email = uniqueEmail();
        String code = bookAndGetCode(email);

        lookUp(email, PHONE, code).andExpect(status().isOk());
        lookUp(email, PHONE, code)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid code"));
    }

    @Test
    void sameAnswerAndNoEmailWhenNoBookingMatches() throws Exception {
        String email = uniqueEmail();
        EventResponse event = createEvent();
        bookAsGuest(event, List.of(seatIds(event.id()).get(0)), email, PHONE);
        awaitEmails(email, 1);

        sendCode(uniqueEmail(), PHONE).andExpect(status().isAccepted());        // nobody booked with this email
        sendCode(email, "+91 98765 43210").andExpect(status().isAccepted());    // right email, wrong phone

        Thread.sleep(500);
        assertThat(emailsTo(email)).hasSize(1);
        lookUp(email, "+91 98765 43210", "123456").andExpect(status().isBadRequest());
    }

    @Test
    void fiveWrongCodesLockTheCode() throws Exception {
        String email = uniqueEmail();
        String code = bookAndGetCode(email);
        String wrong = code.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < 5; i++) {
            lookUp(email, PHONE, wrong).andExpect(status().isBadRequest());
        }
        lookUp(email, PHONE, code).andExpect(status().isBadRequest());
    }

    @Test
    void aCodeExpiresAfterTenMinutes() throws Exception {
        String email = uniqueEmail();
        String code = bookAndGetCode(email);

        clock.advance(Duration.ofMinutes(10));

        lookUp(email, PHONE, code).andExpect(status().isBadRequest());
    }

    @Test
    void aNewCodeIsSentOnlyAfterThirtySecondsAndReplacesTheOldOne() throws Exception {
        String email = uniqueEmail();
        String first = bookAndGetCode(email);   // ticket email, then the code

        sendCode(email, PHONE).andExpect(status().isAccepted());   // too soon: no second code
        Thread.sleep(500);
        assertThat(emailsTo(email)).hasSize(2);

        clock.advance(Duration.ofSeconds(31));
        sendCode(email, PHONE).andExpect(status().isAccepted());
        String second = code(awaitEmails(email, 3).get(2));

        if (!second.equals(first)) {
            lookUp(email, PHONE, first).andExpect(status().isBadRequest());
        }
        lookUp(email, PHONE, second).andExpect(status().isOk());
    }

    @Test
    void requestsAreValidated() throws Exception {
        sendCode("not-an-email", PHONE).andExpect(status().isBadRequest());
        sendCode(uniqueEmail(), "call me").andExpect(status().isBadRequest());
        lookUp(uniqueEmail(), PHONE, "12ab56").andExpect(status().isBadRequest());
    }

    // ---- helpers ------------------------------------------------------------------------------

    private EventResponse concertAtAnAddress() {
        VenueResponse venue = venueService.createVenue(new CreateVenueRequest("Crescent Hall", "Testville",
                VenueType.OPERA_HOUSE, MapZone.NORTH, "STAGE",
                List.of(new SectionRequest("Stalls", 2, 5, 8_000, MapZone.SOUTH)), "12 Crescent Square"));
        assertThat(venue.address()).isEqualTo("12 Crescent Square");
        return eventService.createEvent(new CreateEventRequest(venue.id(), "Gala Concert",
                clock.instant().plus(Duration.ofDays(30))));
    }

    private String bookAndGetCode(String email) throws Exception {
        EventResponse event = createEvent();
        bookAsGuest(event, List.of(seatIds(event.id()).get(0)), email, PHONE);
        awaitEmails(email, 1);
        sendCode(email, PHONE).andExpect(status().isAccepted());
        return code(awaitEmails(email, 2).get(1));
    }

    /** Holds the seats and pays as the web client does for a guest: contact details with the confirm. */
    private String bookAsGuest(EventResponse event, List<Long> seatIds, String email, String phone) throws Exception {
        String hold = mvc.perform(post("/api/events/{id}/holds", event.id()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerRef\": \"ticket-test\", \"eventSeatIds\": %s}".formatted(seatIds)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String booking = mvc.perform(post("/api/holds/{id}/confirm", JsonPath.<Integer>read(hold, "$.id"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactEmail\": \"%s\", \"contactPhone\": \"%s\"}".formatted(email, phone)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(booking, "$.reference");
    }

    private ResultActions sendCode(String email, String phone) throws Exception {
        return mvc.perform(post("/api/tickets/code").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"phone\": \"%s\"}".formatted(email, phone)));
    }

    private ResultActions lookUp(String email, String phone, String code) throws Exception {
        return mvc.perform(post("/api/tickets/lookup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"phone\": \"%s\", \"code\": \"%s\"}".formatted(email, phone, code)));
    }

    private static String code(MimeMessage email) throws Exception {
        assertThat(email.getSubject()).isEqualTo("Your SeatLock ticket code");
        Matcher code = CODE.matcher(GreenMailUtil.getBody(email));
        assertThat(code.find()).as("code in the email").isTrue();
        return code.group(1);
    }

    private static String uniqueEmail() {
        return "fan-" + UUID.randomUUID() + "@example.com";
    }

    /** Waits up to 5 seconds for {@code count} emails to this address; returns them oldest first. */
    private static List<MimeMessage> awaitEmails(String address, int count) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        List<MimeMessage> emails = emailsTo(address);
        while (emails.size() < count && System.nanoTime() < deadline) {
            Thread.sleep(25);
            emails = emailsTo(address);
        }
        assertThat(emails).as("emails to %s", address).hasSize(count);
        return emails;
    }

    private static List<MimeMessage> emailsTo(String address) throws Exception {
        List<MimeMessage> emails = new ArrayList<>();
        for (MimeMessage email : mailServer.getReceivedMessages()) {
            if (email.getAllRecipients()[0].toString().equalsIgnoreCase(address)) {
                emails.add(email);
            }
        }
        return emails;
    }
}
