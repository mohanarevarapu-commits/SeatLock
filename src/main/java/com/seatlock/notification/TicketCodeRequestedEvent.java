package com.seatlock.notification;

import java.time.Duration;

/**
 * A guest asked to see their tickets and should get a one-time code by email. Published inside the
 * transaction that stores the code's hash; {@link BookingMailer} sends it after commit.
 */
public record TicketCodeRequestedEvent(String email, String code, Duration validFor) {
}
