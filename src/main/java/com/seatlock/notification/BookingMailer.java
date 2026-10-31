package com.seatlock.notification;

import com.seatlock.config.AccountProperties;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Emails the booking details to the buyer once the booking has committed, on a background thread. */
@Component
public class BookingMailer {

    /** The server cannot know the buyer's time zone, so emails state the time in UTC. */
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm 'UTC'", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    private final EmailSender emailSender;
    private final AccountProperties properties;

    public BookingMailer(EmailSender emailSender, AccountProperties properties) {
        this.emailSender = emailSender;
        this.properties = properties;
    }

    @Async
    @TransactionalEventListener
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        emailSender.send(event.email(), "Your SeatLock booking " + event.reference(), """
                Hi %s,

                Your booking is confirmed.

                Booking reference: %s
                Concert: %s
                Venue: %s, %s
                When: %s
                Seats:
                %s
                Total: %s

                Keep this reference: use it under Find a booking on any concert page at %s.

                The SeatLock team
                """.formatted(event.name() == null ? "there" : event.name(), event.reference(), event.eventName(),
                event.venueName(), event.venueCity(), WHEN.format(event.startsAt()),
                String.join("\n", event.seats().stream().map(seat -> "  - " + seat).toList()),
                money(event.totalCents()), properties.baseUrl()));
    }

    private static String money(long cents) {
        return String.format(Locale.ENGLISH, "$%d.%02d", cents / 100, cents % 100);
    }
}
