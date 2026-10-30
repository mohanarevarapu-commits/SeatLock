package com.seatlock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seatlock.dto.CreateHoldRequest;
import com.seatlock.dto.EventResponse;
import com.seatlock.dto.HoldResponse;
import com.seatlock.dto.SeatMapResponse;
import com.seatlock.exception.HoldNotActiveException;
import com.seatlock.exception.InvalidRequestException;
import com.seatlock.exception.NotFoundException;
import com.seatlock.exception.SeatUnavailableException;
import com.seatlock.model.HoldStatus;
import com.seatlock.model.SeatStatus;
import com.seatlock.support.IntegrationTest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class HoldServiceIntegrationTest extends IntegrationTest {

    @Autowired
    private HoldService holdService;

    private EventResponse event;
    private List<Long> seats;

    @BeforeEach
    void setUp() {
        event = createEvent();
        seats = seatIds(event.id());
    }

    @Test
    void holdReservesSeatsForTenMinutes() {
        HoldResponse hold = holdService.createHold(event.id(), request(seats.get(0), seats.get(1)));

        assertThat(hold.status()).isEqualTo(HoldStatus.ACTIVE);
        assertThat(hold.expiresAt()).isEqualTo(START.plus(Duration.ofMinutes(10)));
        assertThat(hold.totalCents()).isEqualTo(10_000);
        assertThat(hold.seats()).extracting("label").containsExactly("A1", "A2");
        assertThat(statusOf(seats.get(0))).isEqualTo(SeatStatus.HELD);
        assertThat(statusOf(seats.get(2))).isEqualTo(SeatStatus.AVAILABLE);
    }

    @Test
    void cannotHoldASeatSomeoneElseHolds() {
        holdService.createHold(event.id(), request(seats.get(0)));

        assertThatThrownBy(() -> holdService.createHold(event.id(), request(seats.get(0), seats.get(1))))
                .isInstanceOfSatisfying(SeatUnavailableException.class,
                        ex -> assertThat(ex.getEventSeatIds()).containsExactly(seats.get(0)));

        // The failed request must not have partially held the free seat.
        assertThat(statusOf(seats.get(1))).isEqualTo(SeatStatus.AVAILABLE);
    }

    @Test
    void lapsedHoldDoesNotBlockNewHoldsEvenBeforeTheSweeperRuns() {
        holdService.createHold(event.id(), request(seats.get(0)));
        clock.advance(Duration.ofMinutes(10));

        assertThat(statusOf(seats.get(0))).isEqualTo(SeatStatus.AVAILABLE);
        HoldResponse second = holdService.createHold(event.id(), request(seats.get(0)));
        assertThat(second.status()).isEqualTo(HoldStatus.ACTIVE);
    }

    @Test
    void releasingAHoldFreesItsSeats() {
        HoldResponse hold = holdService.createHold(event.id(), request(seats.get(0), seats.get(1)));

        HoldResponse released = holdService.releaseHold(hold.id());

        assertThat(released.status()).isEqualTo(HoldStatus.RELEASED);
        assertThat(statusOf(seats.get(0))).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(statusOf(seats.get(1))).isEqualTo(SeatStatus.AVAILABLE);
        assertThatThrownBy(() -> holdService.releaseHold(hold.id())).isInstanceOf(HoldNotActiveException.class);
    }

    @Test
    void releasingALapsedHoldDoesNotFreeSeatsNowHeldBySomeoneElse() {
        HoldResponse first = holdService.createHold(event.id(), request(seats.get(0)));
        clock.advance(Duration.ofMinutes(11));
        HoldResponse second = holdService.createHold(event.id(), request(seats.get(0)));

        holdService.releaseHold(first.id());

        assertThat(statusOf(seats.get(0))).isEqualTo(SeatStatus.HELD);
        assertThat(holdService.getHold(second.id()).status()).isEqualTo(HoldStatus.ACTIVE);
    }

    @Test
    void rejectsTooManySeats() {
        Set<Long> nine = Set.copyOf(seats.subList(0, 9));

        assertThatThrownBy(() -> holdService.createHold(event.id(), new CreateHoldRequest("s1", nine)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("at most 8");
    }

    @Test
    void rejectsSeatsFromAnotherEvent() {
        EventResponse other = createEvent();
        Long foreignSeat = seatIds(other.id()).get(0);

        assertThatThrownBy(() -> holdService.createHold(event.id(), request(seats.get(0), foreignSeat)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(String.valueOf(foreignSeat));
    }

    @Test
    void rejectsHoldsOnceTheEventHasStarted() {
        clock.setInstant(event.startsAt());

        assertThatThrownBy(() -> holdService.createHold(event.id(), request(seats.get(0))))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void holdNeverOutlivesTheEventStart() {
        clock.setInstant(event.startsAt().minus(Duration.ofMinutes(3)));

        HoldResponse hold = holdService.createHold(event.id(), request(seats.get(0)));

        assertThat(hold.expiresAt()).isEqualTo(event.startsAt());
    }

    private CreateHoldRequest request(Long... eventSeatIds) {
        return new CreateHoldRequest("session-" + System.nanoTime(), Set.of(eventSeatIds));
    }

    private SeatStatus statusOf(Long eventSeatId) {
        Map<Long, SeatMapResponse.SeatView> byId = eventService.getSeatMap(event.id()).sections().stream()
                .flatMap(s -> s.seats().stream())
                .collect(Collectors.toMap(SeatMapResponse.SeatView::eventSeatId, Function.identity()));
        return byId.get(eventSeatId).status();
    }
}
