package com.seatlock.notification;

import com.seatlock.config.AccountProperties;
import com.seatlock.notification.AccountEmailEvents.PasswordChanged;
import com.seatlock.notification.AccountEmailEvents.PasswordResetRequested;
import com.seatlock.notification.AccountEmailEvents.Registered;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Writes and sends account emails. Each runs after the publishing transaction commits (so nothing is
 * sent for a change that rolled back) and on a background thread (so a slow mail server never
 * delays the response, and response times do not reveal whether an email exists).
 */
@Component
public class AccountMailer {

    private final EmailSender emailSender;
    private final AccountProperties properties;

    public AccountMailer(EmailSender emailSender, AccountProperties properties) {
        this.emailSender = emailSender;
        this.properties = properties;
    }

    @Async
    @TransactionalEventListener
    public void onRegistered(Registered event) {
        emailSender.send(event.email(), "Welcome to SeatLock", """
                Hi %s,

                Your SeatLock account is ready. Sign in at %s to book seats and find all your
                bookings under My bookings.

                The SeatLock team
                """.formatted(event.name(), properties.baseUrl()));
    }

    @Async
    @TransactionalEventListener
    public void onPasswordResetRequested(PasswordResetRequested event) {
        emailSender.send(event.email(), "Reset your SeatLock password", """
                Hi %s,

                Someone asked to reset the password for this SeatLock account. To choose a new
                password, open this link within %d minutes:

                %s

                If it was not you, ignore this email; your password stays the same.

                The SeatLock team
                """.formatted(event.name(), event.validFor().toMinutes(), event.resetLink()));
    }

    @Async
    @TransactionalEventListener
    public void onPasswordChanged(PasswordChanged event) {
        emailSender.send(event.email(), "Your SeatLock password was changed", """
                Hi %s,

                The password for your SeatLock account was just changed. If this was you, there
                is nothing to do. If not, reset it now at %s/#forgot.

                The SeatLock team
                """.formatted(event.name(), properties.baseUrl()));
    }
}
