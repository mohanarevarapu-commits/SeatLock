package com.seatlock.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.seatlock.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** The browser client in src/main/resources/static is served next to the API. */
@AutoConfigureMockMvc
class WebClientIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void rootServesTheWebClient() throws Exception {
        mvc.perform(get("/")).andExpect(forwardedUrl("index.html"));
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>SeatLock Tickets</title>")));
    }

    @Test
    void scriptsAndStylesAreServed() throws Exception {
        mvc.perform(get("/app.js")).andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/events")));
        // Pages are switched with the hidden attribute, which a page's own display rule would override.
        mvc.perform(get("/app.css")).andExpect(status().isOk())
                .andExpect(content().string(containsString("[hidden] { display: none !important; }")));
        mvc.perform(get("/account.js")).andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/auth/login")));
        mvc.perform(get("/payment.js")).andExpect(status().isOk())
                .andExpect(content().string(containsString("4242424242424242")));
        mvc.perform(get("/info.js")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Your data & privacy")));
    }
}
