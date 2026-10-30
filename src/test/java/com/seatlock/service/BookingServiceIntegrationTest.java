package com.seatlock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seatlock.dto.BookingResponse;
import com.seatlock.dto.CreateHoldRequest;
import com.seatlock.dto.EventResponse;
import com.seatlock.dto.HoldResponse;
import com.seatlock.exception.HoldExpiredException;
import com.seatlock.exception.HoldNotActiveException;
import com.seatlock.exception.NotFoundException;
import com.seatlock.exception.SeatUnavailableException;
import com.seatlock.model.HoldStatus;
import com.seatlock.support.IntegrationTest;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BookingServiceIntegrationTest extends IntegrationTest {

    @Autowired
    private HoldService holdService;

    @Autowired
    private BookingService bookingService;

    private EventResponse event;
    private List<Long> seats;

    @BeforeEach
    void setUp() {
        event = createEvent();
        seats = seatIds(event.id());
    }

    @Test
    void confirmingAHoldBooksItsSeats() {
        HoldResponse hold = holdService.createHold(event.id(), new CreateHoldRequest("s1", Set.of(seats.get(0), seats.get(1))));

        BookingResponse booking = bookingService.confirmHold(hold.id());

        assertThat(booking.reference()).startsWith("SL-");
        assertThat(booking.totalCents()).isEqualTo(10_000);
        assertThat(booking.seats()).hasSize(2);
        assertThat(holdService.getHold(hold.id()).status()).isEqualTo(HoldStatus.CONFIRMED);
        assertThat(bookingService.getBooking(booking.reference())).isEqualTo(booking);
    }

    @Test
    void bookedSeatsStayTakenAfterTheHoldWouldHaveExpired() {
        HoldResponse hold = holdService.createHold(event.id(), new CreateHoldRequest("s1", Set.of(seats.get(0))));
        bookingService.confirmHold(hold.id());
        clock.advance(Duration.ofHours(1));

        assertThatThrownBy(() -> holdService.createHold(event.id(), new CreateHoldRequest("s2", Set.of(seats.get(0)))))
                .isInstanceOf(SeatUnavailableException.class);
    }

    @Test
    void cannotConfirmTwice() {
        HoldResponse hold = holdService.createHold(event.id(), new CreateHoldRequest("s1", Set.of(seats.get(0))));
        bookingService.confirmHold(hold.id());

        assertThatThrownBy(() -> bookingService.confirmHold(hold.id())).isInstanceOf(HoldNotActiveException.class);
    }

    @Test
    void cannotConfirmAReleasedHold() {
        HoldResponse hold = holdService.createHold(event.id(), new CreateHoldRequest("s1", Set.of(seats.get(0))));
        holdService.releaseHold(hold.id());

        assertThatThrownBy(() -> bookingService.confirmHold(hold.id())).isInstanceOf(HoldNotActiveException.class);
    }

    @Test
    void cannotConfirmALapsedHold() {
        HoldResponse hold = holdService.createHold(event.id(), new CreateHoldRequest("s1", Set.of(seats.get(0))));
        clock.advance(Duration.ofMinutes(10));

        assertThatThrownBy(() -> bookingService.confirmHold(hold.id())).isInstanceOf(HoldExpiredException.class);
    }

    @Test
    void unknownBookingReferenceIsNotFound() {
        assertThatThrownBy(() -> bookingService.getBooking("SL-NOPE00")).isInstanceOf(NotFoundException.class);
    }
}
