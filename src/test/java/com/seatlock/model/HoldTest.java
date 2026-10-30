package com.seatlock.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HoldTest {

    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");

    private Hold newHold() {
        return new Hold(null, "session-1", Set.of(3L, 1L, 2L), NOW, NOW.plusSeconds(600));
    }

    @Test
    void startsActiveWithSortedSeatIds() {
        Hold hold = newHold();

        assertThat(hold.getStatus()).isEqualTo(HoldStatus.ACTIVE);
        assertThat(hold.getEventSeatIds()).containsExactly(1L, 2L, 3L);
    }

    @Test
    void expiresExactlyAtExpiryTime() {
        Hold hold = newHold();

        assertThat(hold.isExpiredAt(NOW.plusSeconds(599))).isFalse();
        assertThat(hold.isExpiredAt(NOW.plusSeconds(600))).isTrue();
    }

    @Test
    void terminalStatesCannotTransitionAgain() {
        Hold confirmed = newHold();
        confirmed.confirm();
        assertThatThrownBy(confirmed::release).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(confirmed::expire).isInstanceOf(IllegalStateException.class);

        Hold released = newHold();
        released.release();
        assertThatThrownBy(released::confirm).isInstanceOf(IllegalStateException.class);
    }
}
