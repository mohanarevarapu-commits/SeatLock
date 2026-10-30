package com.seatlock.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AccountRulesTest {

    private static final Instant NOW = Instant.parse("2030-01-01T18:00:00Z");

    @Test
    void emailIsStoredTrimmedAndLowerCase() {
        Account account = new Account("  Asha.Rao@Example.COM ", "hash", "Asha", null, NOW);

        assertThat(account.getEmail()).isEqualTo("asha.rao@example.com");
    }

    @Test
    void resetTokenWorksUntilItExpiresOrIsUsed() {
        PasswordResetToken token = new PasswordResetToken(null, "hash", NOW.plusSeconds(1800));

        assertThat(token.isUsableAt(NOW)).isTrue();
        assertThat(token.isUsableAt(NOW.plusSeconds(1800))).isFalse();

        token.markUsed(NOW);
        assertThat(token.isUsableAt(NOW)).isFalse();
    }

    @Test
    void guestHoldIsOpenToAnyoneButAnAccountHoldOnlyToItsOwner() {
        Hold guest = new Hold(null, "web-1", Set.of(1L), NOW, NOW.plusSeconds(600));
        Hold owned = new Hold(null, "web-2", 7L, Set.of(2L), NOW, NOW.plusSeconds(600));

        assertThat(guest.isAccessibleBy(null)).isTrue();
        assertThat(guest.isAccessibleBy(7L)).isTrue();
        assertThat(owned.isAccessibleBy(7L)).isTrue();
        assertThat(owned.isAccessibleBy(8L)).isFalse();
        assertThat(owned.isAccessibleBy(null)).isFalse();
    }
}
