package com.seatlock.notification;

import java.time.Instant;
import java.util.List;

/**
 * A booking was confirmed and its buyer should get the details by email: the account's email for a
 * signed-in buyer, or the contact email a guest gave. Published inside the booking transaction;
 * {@link BookingMailer} sends it after commit.
 *
 * @param name the account holder's name, or null for a guest
 */
public record BookingConfirmedEvent(String email, String name, String reference, String eventName, String venueName,
                                    String venueCity, Instant startsAt, List<String> seats, long totalCents) {
}
