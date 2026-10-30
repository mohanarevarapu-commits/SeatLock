package com.seatlock.notification;

import java.time.Duration;

/**
 * Things that happened to an account and need an email. Services publish them inside their
 * transaction; {@link AccountMailer} sends the email only after the transaction commits.
 */
public final class AccountEmailEvents {

    private AccountEmailEvents() {
    }

    public record Registered(String email, String name) {
    }

    public record PasswordResetRequested(String email, String name, String resetLink, Duration validFor) {
    }

    public record PasswordChanged(String email, String name) {
    }
}
