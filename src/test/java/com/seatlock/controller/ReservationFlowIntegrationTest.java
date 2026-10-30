package com.seatlock.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.seatlock.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Drives the whole venue -> event -> hold -> booking flow through the HTTP API. */
@AutoConfigureMockMvc
class ReservationFlowIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void customerCanHoldAndBookSeats() throws Exception {
        String venue = mvc.perform(post("/api/venues").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Flow Hall", "city": "Testville",
                         "sections": [{"name": "Floor", "rows": 2, "seatsPerRow": 3, "priceCents": 7500}]}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.capacity").value(6))
                .andReturn().getResponse().getContentAsString();
        int venueId = JsonPath.read(venue, "$.id");

        String event = mvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content("""
                        {"venueId": %d, "name": "Flow Show", "startsAt": "2099-06-01T19:30:00Z"}
                        """.formatted(venueId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int eventId = JsonPath.read(event, "$.id");

        String seatMap = mvc.perform(get("/api/events/{id}/seat-map", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableCount").value(6))
                .andReturn().getResponse().getContentAsString();
        int seatId = JsonPath.read(seatMap, "$.sections[0].seats[0].eventSeatId");

        String hold = mvc.perform(post("/api/events/{id}/holds", eventId).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerRef": "checkout-1", "eventSeatIds": [%d]}
                                """.formatted(seatId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int holdId = JsonPath.read(hold, "$.id");

        mvc.perform(get("/api/events/{id}/seat-map", eventId))
                .andExpect(jsonPath("$.availableCount").value(5))
                .andExpect(jsonPath("$.sections[0].seats[0].status").value("HELD"));

        String booking = mvc.perform(post("/api/holds/{id}/confirm", holdId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalCents").value(7500))
                .andReturn().getResponse().getContentAsString();
        String reference = JsonPath.read(booking, "$.reference");

        mvc.perform(get("/api/bookings/{ref}", reference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seats[0].label").value("A1"));
        mvc.perform(get("/api/events/{id}/seat-map", eventId))
                .andExpect(jsonPath("$.sections[0].seats[0].status").value("BOOKED"));
    }

    @Test
    void eventsInThePastAreRejected() throws Exception {
        mvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content("""
                        {"venueId": 1, "name": "Old Show", "startsAt": "2001-01-01T00:00:00Z"}
                        """))
                .andExpect(status().isBadRequest());
    }
}
