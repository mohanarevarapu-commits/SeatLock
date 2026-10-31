package com.seatlock.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.seatlock.dto.BookingResponse;
import com.seatlock.dto.HoldResponse;
import com.seatlock.dto.SeatLine;
import com.seatlock.exception.HoldExpiredException;
import com.seatlock.exception.HoldNotActiveException;
import com.seatlock.exception.NotFoundException;
import com.seatlock.exception.SeatUnavailableException;
import com.seatlock.model.HoldStatus;
import com.seatlock.ratelimit.RateLimitingFilter;
import com.seatlock.security.SecurityConfig;
import com.seatlock.service.BookingService;
import com.seatlock.service.EventService;
import com.seatlock.service.HoldService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// The rate limiter is covered by RateLimitingFilterTest; this test is only about HTTP mapping.
// SeatLock's own security rules are loaded, so guests (no session) can POST without a CSRF token.
@WebMvcTest(controllers = {EventController.class, HoldController.class},
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RateLimitingFilter.class))
@Import(SecurityConfig.class)
class HoldControllerTest {

    private static final Instant EXPIRES = Instant.parse("2030-01-01T18:10:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private EventService eventService;

    @MockitoBean
    private HoldService holdService;

    @MockitoBean
    private BookingService bookingService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void createHoldReturns201WithLocation() throws Exception {
        given(holdService.createHold(eq(1L), any(), isNull())).willReturn(new HoldResponse(42L, 1L, "abc", HoldStatus.ACTIVE,
                EXPIRES, 5_000, List.of(new SeatLine(7L, "Main", "A1", 5_000))));

        mvc.perform(post("/api/events/1/holds").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerRef": "abc", "eventSeatIds": [7]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/holds/42"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.seats[0].label").value("A1"));
    }

    @Test
    void createHoldValidatesTheBody() throws Exception {
        mvc.perform(post("/api/events/1/holds").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerRef": "has spaces", "eventSeatIds": []}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void takenSeatsReturn409WithTheConflictingIds() throws Exception {
        given(holdService.createHold(eq(1L), any(), isNull())).willThrow(new SeatUnavailableException(List.of(7L, 8L)));

        mvc.perform(post("/api/events/1/holds").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerRef": "abc", "eventSeatIds": [7, 8]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Seats unavailable"))
                .andExpect(jsonPath("$.eventSeatIds[0]").value(7));
    }

    @Test
    void lockConflictsAskTheClientToRetry() throws Exception {
        given(holdService.createHold(eq(1L), any(), isNull()))
                .willThrow(new ObjectOptimisticLockingFailureException("EventSeat", 7L));

        mvc.perform(post("/api/events/1/holds").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerRef": "abc", "eventSeatIds": [7]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Concurrent update"));
    }

    @Test
    void confirmReturns201WithBookingLocation() throws Exception {
        given(bookingService.confirmHold(eq(42L), isNull(), isNull())).willReturn(new BookingResponse("SL-ABC234", 42L, 1L, 5_000,
                EXPIRES, List.of()));

        mvc.perform(post("/api/holds/42/confirm"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/bookings/SL-ABC234"));
    }

    @Test
    void confirmingAnExpiredHoldReturns410() throws Exception {
        given(bookingService.confirmHold(eq(42L), isNull(), isNull())).willThrow(new HoldExpiredException(42L));

        mvc.perform(post("/api/holds/42/confirm")).andExpect(status().isGone());
    }

    @Test
    void releasingAnInactiveHoldReturns409() throws Exception {
        given(holdService.releaseHold(eq(42L), isNull())).willThrow(new HoldNotActiveException(42L, HoldStatus.CONFIRMED));

        mvc.perform(delete("/api/holds/42")).andExpect(status().isConflict());
    }

    @Test
    void unknownHoldReturns404() throws Exception {
        given(holdService.releaseHold(eq(99L), isNull())).willThrow(new NotFoundException("Hold", 99L));

        mvc.perform(delete("/api/holds/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Hold 99 not found"));
    }
}
