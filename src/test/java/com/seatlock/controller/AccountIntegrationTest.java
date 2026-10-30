package com.seatlock.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.jayway.jsonpath.JsonPath;
import com.seatlock.dto.EventResponse;
import com.seatlock.support.IntegrationTest;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Accounts end to end over HTTP: sign-up, sign-in, profile, password reset by email, My bookings,
 * and who may use a signed-in buyer's hold. Emails go to GreenMail, an in-memory SMTP server.
 */
@AutoConfigureMockMvc
class AccountIntegrationTest extends IntegrationTest {

    private static final Pattern RESET_LINK = Pattern.compile("http://seatlock\\.test/#reset/([A-Za-z0-9_-]+)");

    @RegisterExtension
    static final GreenMailExtension mailServer = new GreenMailExtension(ServerSetupTest.SMTP)
            .withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication())
            // One server for the class: emails are sent in the background and may still arrive after
            // a test ends. Each test uses its own address and only counts emails sent to it.
            .withPerMethodLifecycle(false);

    @Autowired
    private MockMvc mvc;

    // ---- sign-up and sign-in ------------------------------------------------------------------

    @Test
    void signUpCreatesTheAccountSignsInAndSendsAWelcomeEmail() throws Exception {
        String email = uniqueEmail();
        MockHttpSession session = register("Asha Rao", email, "correct-horse-1", "+44 20 7946 0000");

        perform(get("/api/account"), session)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Asha Rao"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.phone").value("+44 20 7946 0000"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        MimeMessage welcome = awaitEmails(email, 1).get(0);
        assertThat(welcome.getSubject()).isEqualTo("Welcome to SeatLock");
    }

    @Test
    void emailIsRegisteredOnlyOnceWhateverItsCase() throws Exception {
        String email = uniqueEmail();
        register("First", email, "password-one", null);

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Second", email.toUpperCase(), "password-two", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Email already registered"));
    }

    @Test
    void signUpValidatesPasswordLengthAndPhone() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Short", uniqueEmail(), "short", null)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Phone", uniqueEmail(), "long-enough-1", "call me")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameAnswer() throws Exception {
        String email = uniqueEmail();
        register("Asha", email, "correct-horse-1", null);

        login(email, "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Email or password is incorrect."));
        login(uniqueEmail(), "whatever-123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Email or password is incorrect."));
    }

    @Test
    void signInIsCaseInsensitiveAndSignOutEndsTheSession() throws Exception {
        String email = uniqueEmail();
        register("Asha", email, "correct-horse-1", null);

        MvcResult signedIn = login(email.toUpperCase(), "correct-horse-1").andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) signedIn.getRequest().getSession(false);
        perform(get("/api/account"), session).andExpect(status().isOk());

        perform(post("/api/auth/logout"), session).andExpect(status().isNoContent());
        perform(get("/api/account"), session).andExpect(status().isUnauthorized());
    }

    @Test
    void accountEndpointsNeedASignIn() throws Exception {
        mvc.perform(get("/api/account"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Sign-in required"));
        mvc.perform(get("/api/account/bookings")).andExpect(status().isUnauthorized());
    }

    @Test
    void signedInChangesNeedTheCsrfToken() throws Exception {
        MockHttpSession session = register("Asha", uniqueEmail(), "correct-horse-1", null);

        mvc.perform(withSession(put("/api/account"), session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Forged\"}"))
                .andExpect(status().isForbidden());
        perform(get("/api/account"), session).andExpect(jsonPath("$.name").value("Asha"));
    }

    @Test
    void buyerCanChangeNameAndPhone() throws Exception {
        MockHttpSession session = register("Asha", uniqueEmail(), "correct-horse-1", "+91 98765 43210");

        perform(put("/api/account").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Asha R\", \"phone\": \"\"}"), session)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Asha R"))
                .andExpect(jsonPath("$.phone").doesNotExist());
    }

    // ---- forgot password --------------------------------------------------------------------------

    @Test
    void forgottenPasswordIsResetThroughTheEmailedLink() throws Exception {
        String email = uniqueEmail();
        register("Asha", email, "old-password-1", null);
        awaitEmails(email, 1);

        forgotPassword(email).andExpect(status().isAccepted());
        MimeMessage resetEmail = awaitEmails(email, 2).get(1);
        assertThat(resetEmail.getSubject()).isEqualTo("Reset your SeatLock password");
        String token = resetToken(resetEmail);

        resetPassword(token, "new-password-1").andExpect(status().isNoContent());

        login(email, "old-password-1").andExpect(status().isUnauthorized());
        login(email, "new-password-1").andExpect(status().isOk());
        assertThat(awaitEmails(email, 3).get(2).getSubject()).isEqualTo("Your SeatLock password was changed");
    }

    @Test
    void unknownEmailGetsTheSameAnswerAndNoEmail() throws Exception {
        String email = uniqueEmail();
        forgotPassword(email).andExpect(status().isAccepted());

        Thread.sleep(500);
        assertThat(emailsTo(email)).isEmpty();
    }

    @Test
    void resetLinkWorksOnlyOnce() throws Exception {
        String email = uniqueEmail();
        register("Asha", email, "old-password-1", null);
        forgotPassword(email);
        String token = resetToken(awaitEmails(email, 2).get(1));

        resetPassword(token, "new-password-1").andExpect(status().isNoContent());
        resetPassword(token, "another-password-2")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("invalid, already used or expired")));
    }

    @Test
    void resetLinkExpiresAfterThirtyMinutes() throws Exception {
        String email = uniqueEmail();
        register("Asha", email, "old-password-1", null);
        forgotPassword(email);
        String token = resetToken(awaitEmails(email, 2).get(1));

        clock.advance(Duration.ofMinutes(30));

        resetPassword(token, "new-password-1").andExpect(status().isBadRequest());
        login(email, "old-password-1").andExpect(status().isOk());
    }

    @Test
    void onlyTheNewestResetLinkWorks() throws Exception {
        String email = uniqueEmail();
        register("Asha", email, "old-password-1", null);
        forgotPassword(email);
        forgotPassword(email);
        List<MimeMessage> emails = awaitEmails(email, 3);
        String first = resetToken(emails.get(1));
        String second = resetToken(emails.get(2));

        resetPassword(first, "new-password-1").andExpect(status().isBadRequest());
        resetPassword(second, "new-password-2").andExpect(status().isNoContent());
    }

    // ---- My bookings and hold ownership ----------------------------------------------------------

    @Test
    void myBookingsListsOnlyTheBuyersOwnBookingsSplitIntoUpcomingAndPast() throws Exception {
        MockHttpSession session = register("Asha", uniqueEmail(), "correct-horse-1", null);
        EventResponse event = createEvent();
        String reference = bookAs(session, event, seatIds(event.id()).get(0));
        bookAs(null, event, seatIds(event.id()).get(1)); // a guest's booking at the same show

        perform(get("/api/account/bookings"), session)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upcoming.length()").value(1))
                .andExpect(jsonPath("$.upcoming[0].reference").value(reference))
                .andExpect(jsonPath("$.upcoming[0].eventName").value("Test Show"))
                .andExpect(jsonPath("$.upcoming[0].venueName").value("Test Hall"))
                .andExpect(jsonPath("$.upcoming[0].seats[0].label").value("A1"))
                .andExpect(jsonPath("$.past.length()").value(0));

        clock.advance(Duration.ofDays(31)); // the show has now started

        perform(get("/api/account/bookings"), session)
                .andExpect(jsonPath("$.upcoming.length()").value(0))
                .andExpect(jsonPath("$.past[0].reference").value(reference));
    }

    @Test
    void aSignedInBuyersHoldCanOnlyBeUsedByThatBuyer() throws Exception {
        MockHttpSession owner = register("Owner", uniqueEmail(), "correct-horse-1", null);
        MockHttpSession other = register("Other", uniqueEmail(), "correct-horse-2", null);
        EventResponse event = createEvent();
        int holdId = holdAs(owner, event, seatIds(event.id()).get(0));

        mvc.perform(get("/api/holds/{id}", holdId)).andExpect(status().isNotFound());
        mvc.perform(post("/api/holds/{id}/confirm", holdId)).andExpect(status().isNotFound());
        perform(delete("/api/holds/{id}", holdId), other).andExpect(status().isNotFound());

        perform(get("/api/holds/{id}", holdId), owner).andExpect(status().isOk());
        perform(post("/api/holds/{id}/confirm", holdId), owner).andExpect(status().isCreated());
    }

    @Test
    void guestHoldsStillWorkWithoutAnAccount() throws Exception {
        EventResponse event = createEvent();
        int holdId = holdAs(null, event, seatIds(event.id()).get(0));

        mvc.perform(get("/api/holds/{id}", holdId)).andExpect(status().isOk());
        mvc.perform(post("/api/holds/{id}/confirm", holdId)).andExpect(status().isCreated());
    }

    // ---- helpers --------------------------------------------------------------------------------

    private static String uniqueEmail() {
        return "buyer-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private static String registerJson(String name, String email, String password, String phone) {
        return """
                {"name": "%s", "email": "%s", "password": "%s"%s}
                """.formatted(name, email, password, phone == null ? "" : ", \"phone\": \"" + phone + "\"");
    }

    private MockHttpSession register(String name, String email, String password, String phone) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(name, email, password, phone)))
                .andExpect(status().isCreated())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)));
    }

    private ResultActions forgotPassword(String email) throws Exception {
        return mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\"}".formatted(email)));
    }

    private ResultActions resetPassword(String token, String password) throws Exception {
        return mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"%s\", \"password\": \"%s\"}".formatted(token, password)));
    }

    private int holdAs(MockHttpSession session, EventResponse event, long seatId) throws Exception {
        String hold = perform(post("/api/events/{id}/holds", event.id()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerRef\": \"web-test\", \"eventSeatIds\": [%d]}".formatted(seatId)), session)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(hold, "$.id");
    }

    private String bookAs(MockHttpSession session, EventResponse event, long seatId) throws Exception {
        int holdId = holdAs(session, event, seatId);
        String booking = perform(post("/api/holds/{id}/confirm", holdId), session)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(booking, "$.reference");
    }

    /** Sends the request as the browser would: with the session cookie (if any) and the CSRF token. */
    private ResultActions perform(MockHttpServletRequestBuilder request, MockHttpSession session) throws Exception {
        return session == null ? mvc.perform(request) : mvc.perform(withSession(request, session).with(csrf()));
    }

    /** Attaches the session the way a cookie does, so the CSRF check applies as it would in a browser. */
    private static MockHttpServletRequestBuilder withSession(MockHttpServletRequestBuilder request,
                                                             MockHttpSession session) {
        RequestPostProcessor cookie = req -> {
            req.setRequestedSessionId(session.getId());
            return req;
        };
        return request.session(session).with(cookie);
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
            if (email.getAllRecipients()[0].toString().equals(address)) {
                emails.add(email);
            }
        }
        return emails;
    }

    private static String resetToken(MimeMessage email) {
        Matcher link = RESET_LINK.matcher(GreenMailUtil.getBody(email));
        assertThat(link.find()).as("reset link in the email").isTrue();
        return link.group(1);
    }
}
