package com.seatlock.notification;

import com.seatlock.dto.TicketResponse;

/**
 * A booking was confirmed and its buyer should get their ticket by email: the account's email for a
 * signed-in buyer, or the contact email a guest gave. Published inside the booking transaction;
 * {@link BookingMailer} sends it after commit.
 *
 * @param name  the account holder's name, or null for a guest
 * @param phone the buyer's phone number, if they gave one
 */
public record BookingConfirmedEvent(String email, String name, String phone, TicketResponse ticket) {
}
