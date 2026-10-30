package com.seatlock.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.seatlock.dto.CreateHoldRequest;
import com.seatlock.dto.EventResponse;
import com.seatlock.dto.HoldResponse;
import com.seatlock.model.HoldStatus;
import com.seatlock.model.SeatStatus;
import com.seatlock.support.IntegrationTest;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class HoldExpiryServiceIntegrationTest extends IntegrationTest {

    @Autowired
    private HoldService holdService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private HoldExpiryService holdExpiryService;

    private EventResponse event;
    private List<Long> seats;

    @BeforeEach
    void setUp() {
        event = createEvent();
        seats = seatIds(event.id());
        // Flush lapsed holds left behind by other test classes sharing this context.
        clock.setInstant(START.plus(Duration.ofDays(365)));
        holdExpiryService.expireLapsedHolds();
        clock.setInstant(START);
    }

    @Test
    void sweeperExpiresLapsedHoldsAndFreesSeats() {
        HoldResponse lapsed = holdService.createHold(event.id(), new CreateHoldRequest("s1", Set.of(seats.get(0))));
        clock.advance(Duration.ofMinutes(5));
        HoldResponse live = holdService.createHold(event.id(), new CreateHoldRequest("s2", Set.of(seats.get(1))));
        clock.advance(Duration.ofMinutes(6));

        int expired = holdExpiryService.expireLapsedHolds();

        assertThat(expired).isEqualTo(1);
        assertThat(holdService.getHold(lapsed.id()).status()).isEqualTo(HoldStatus.EXPIRED);
        assertThat(holdService.getHold(live.id()).status()).isEqualTo(HoldStatus.ACTIVE);
        assertThat(rawStatusOf(0)).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(rawStatusOf(1)).isEqualTo(SeatStatus.HELD);
    }

    @Test
    void sweeperLeavesSeatsThatWereReheldByANewerHold() {
        HoldResponse lapsed = holdService.createHold(event.id(), new CreateHoldRequest("s1", Set.of(seats.get(0))));
        clock.advance(Duration.ofMinutes(10));
        HoldResponse newer = holdService.createHold(event.id(), new CreateHoldRequest("s2", Set.of(seats.get(0))));

        holdExpiryService.expireLapsedHolds();

        assertThat(holdService.getHold(lapsed.id()).status()).isEqualTo(HoldStatus.EXPIRED);
        assertThat(rawStatusOf(0)).isEqualTo(SeatStatus.HELD);
        assertThat(bookingService.confirmHold(newer.id()).seats()).hasSize(1);
    }

    @Test
    void sweeperIgnoresConfirmedHolds() {
        HoldResponse hold = holdService.createHold(event.id(), new CreateHoldRequest("s1", Set.of(seats.get(0))));
        bookingService.confirmHold(hold.id());
        clock.advance(Duration.ofHours(1));

        assertThat(holdExpiryService.expireLapsedHolds()).isZero();
        assertThat(rawStatusOf(0)).isEqualTo(SeatStatus.BOOKED);
    }

    /** Seat status at the original start time, so lapsed-but-unswept holds still read as HELD. */
    private SeatStatus rawStatusOf(int index) {
        clock.setInstant(START);
        return eventService.getSeatMap(event.id()).sections().get(0).seats().get(index).status();
    }
}
