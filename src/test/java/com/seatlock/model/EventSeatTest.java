package com.seatlock.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class EventSeatTest {

    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final Instant LATER = NOW.plus(Duration.ofMinutes(10));

    private final EventSeat seat = new EventSeat(null, null);

    @Test
    void newSeatIsAvailable() {
        assertThat(seat.isAvailableAt(NOW)).isTrue();
        assertThat(seat.effectiveStatus(NOW)).isEqualTo(SeatStatus.AVAILABLE);
    }

    @Test
    void heldSeatIsUnavailableUntilHoldLapses() {
        seat.hold(1L, LATER, NOW);

        assertThat(seat.isAvailableAt(NOW)).isFalse();
        assertThat(seat.isAvailableAt(LATER.minusMillis(1))).isFalse();
        assertThat(seat.isAvailableAt(LATER)).isTrue();
        assertThat(seat.effectiveStatus(LATER)).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.HELD);
    }

    @Test
    void cannotHoldSeatThatIsAlreadyHeld() {
        seat.hold(1L, LATER, NOW);

        assertThatThrownBy(() -> seat.hold(2L, LATER, NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void lapsedHoldCanBeTakenOverByAnotherHold() {
        seat.hold(1L, LATER, NOW);
        seat.hold(2L, LATER.plus(Duration.ofMinutes(10)), LATER);

        assertThat(seat.getHoldId()).isEqualTo(2L);
        assertThat(seat.isHeldBy(1L, LATER)).isFalse();
        assertThat(seat.isHeldBy(2L, LATER)).isTrue();
    }

    @Test
    void bookingRequiresALiveHoldByTheSameHold() {
        seat.hold(1L, LATER, NOW);

        assertThatThrownBy(() -> seat.book(2L, NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> seat.book(1L, LATER)).isInstanceOf(IllegalStateException.class);

        seat.book(1L, NOW);
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.BOOKED);
        assertThat(seat.isAvailableAt(LATER.plus(Duration.ofDays(1)))).isFalse();
    }

    @Test
    void releaseOnlyAffectsTheOwningHold() {
        seat.hold(1L, LATER, NOW);

        assertThat(seat.releaseIfHeldBy(2L)).isFalse();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.HELD);

        assertThat(seat.releaseIfHeldBy(1L)).isTrue();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(seat.getHoldId()).isNull();
    }

    @Test
    void releaseNeverUnbooksASeat() {
        seat.hold(1L, LATER, NOW);
        seat.book(1L, NOW);

        assertThat(seat.releaseIfHeldBy(1L)).isFalse();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.BOOKED);
    }
}
