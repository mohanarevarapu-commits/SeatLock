package com.seatlock.notification;

import com.seatlock.config.AccountProperties;
import com.seatlock.dto.SeatLine;
import com.seatlock.dto.TicketResponse;
import com.seatlock.model.VenueType;
import com.seatlock.service.QrCodeGenerator;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.HtmlUtils;

/**
 * Emails tickets and ticket codes, once the booking or code has committed, on a background thread.
 * The ticket email has an HTML version laid out like a ticket, with the QR code shown inside it, and a
 * plain-text version with the same details for mail programs that do not show HTML.
 */
@Component
public class BookingMailer {

    static final String QR_CONTENT_ID = "ticket-qr";

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm", Locale.ENGLISH);

    private final EmailSender emailSender;
    private final QrCodeGenerator qrCodes;
    private final AccountProperties properties;

    public BookingMailer(EmailSender emailSender, QrCodeGenerator qrCodes, AccountProperties properties) {
        this.emailSender = emailSender;
        this.qrCodes = qrCodes;
        this.properties = properties;
    }

    @Async
    @TransactionalEventListener
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        TicketResponse ticket = event.ticket();
        emailSender.send(new EmailMessage(event.email(), "Your SeatLock booking " + ticket.reference(),
                ticketText(event), ticketHtml(event),
                List.of(new EmailMessage.InlineImage(QR_CONTENT_ID, qrCodes.png(ticket.reference()), "image/png"))));
    }

    @Async
    @TransactionalEventListener
    public void onTicketCodeRequested(TicketCodeRequestedEvent event) {
        // The code is only in the body: subjects can end up in logs, bodies never do.
        emailSender.send(event.email(), "Your SeatLock ticket code", """
                Your SeatLock code is: %s

                Enter it on the Find my tickets page to see your tickets. It works once, for %d minutes.

                If you did not ask for this code, you can ignore this email: nobody can see your tickets
                without it.

                The SeatLock team
                """.formatted(event.code(), event.validFor().toMinutes()));
    }

    // ---- the ticket email ---------------------------------------------------------------------

    private String ticketText(BookingConfirmedEvent event) {
        TicketResponse t = event.ticket();
        StringBuilder seats = new StringBuilder();
        for (SeatLine seat : t.seats()) {
            seats.append("  %s · Row %s · Seat %d   %s%n".formatted(seat.section(), seat.row(), seat.number(),
                    money(seat.priceCents())));
        }
        return """
                Hi %s,

                Your booking is confirmed. Show the QR code in this email, or your booking reference, at the entrance.

                Booking reference: %s

                CONCERT
                %s
                %s

                VENUE
                %s

                SEATS
                %sTotal: %s

                BOOKED FOR
                %s

                %s

                SeatLock is a demo: no real tickets are sold and no money is taken.

                The SeatLock team
                """.formatted(event.name() == null ? "there" : event.name(), t.reference(), t.eventName(),
                when(t.startsAt()), String.join("\n", venueLines(t)), seats, money(t.totalCents()),
                String.join("\n", bookedFor(event)), findTicketsText(event));
    }

    private String ticketHtml(BookingConfirmedEvent event) {
        TicketResponse t = event.ticket();
        StringBuilder seats = new StringBuilder();
        for (SeatLine seat : t.seats()) {
            seats.append("""
                    <tr><td style="padding:6px 0;border-top:1px solid #e1e4ee">%s</td>\
                    <td style="padding:6px 0;border-top:1px solid #e1e4ee">%s</td>\
                    <td style="padding:6px 0;border-top:1px solid #e1e4ee">%d</td>\
                    <td style="padding:6px 0;border-top:1px solid #e1e4ee;text-align:right">%s</td></tr>
                    """.formatted(esc(seat.section()), esc(seat.row()), seat.number(), money(seat.priceCents())));
        }
        return """
                <!doctype html>
                <html><body style="margin:0;padding:24px 12px;background:#f5f6fa;font-family:Arial,Helvetica,sans-serif;color:#1b1d29">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"><tr><td align="center">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:560px;background:#ffffff;border:1px solid #e1e4ee;border-radius:14px">
                <tr><td style="padding:18px 24px;border-bottom:1px solid #e1e4ee;font-weight:bold;font-size:18px">
                  <span style="color:#5b4bff">&#9670;</span> SeatLock</td></tr>
                <tr><td style="padding:24px">
                  <p style="margin:0 0 4px;color:#646a7f">Hi %s, your booking is confirmed.</p>
                  <h1 style="margin:0 0 6px;font-size:24px">%s</h1>
                  <p style="margin:0 0 20px;font-size:15px">%s</p>
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f5f6fa;border-radius:12px">
                  <tr><td align="center" style="padding:18px">
                    <img src="cid:%s" width="200" height="200" alt="Ticket QR code for booking %s" style="display:block;border:0">
                    <p style="margin:10px 0 2px;font-family:Menlo,Consolas,monospace;font-size:20px;font-weight:bold;letter-spacing:2px">%s</p>
                    <p style="margin:0;color:#646a7f;font-size:13px">Show this QR code or reference at the entrance</p>
                  </td></tr></table>
                  <h2 style="margin:24px 0 6px;font-size:13px;letter-spacing:1px;text-transform:uppercase;color:#646a7f">Venue</h2>
                  <p style="margin:0;font-size:15px;line-height:1.5">%s</p>
                  <h2 style="margin:24px 0 6px;font-size:13px;letter-spacing:1px;text-transform:uppercase;color:#646a7f">Seats</h2>
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="font-size:14px">
                  <tr style="color:#646a7f"><td style="padding:4px 0">Section</td><td>Row</td><td>Seat</td><td style="text-align:right">Price</td></tr>
                  %s<tr><td colspan="3" style="padding:10px 0 0;border-top:2px solid #1b1d29;font-weight:bold">Total</td>
                  <td style="padding:10px 0 0;border-top:2px solid #1b1d29;font-weight:bold;text-align:right">%s</td></tr>
                  </table>
                  <h2 style="margin:24px 0 6px;font-size:13px;letter-spacing:1px;text-transform:uppercase;color:#646a7f">Booked for</h2>
                  <p style="margin:0;font-size:15px;line-height:1.5">%s</p>
                  <p style="margin:24px 0 0;font-size:14px;line-height:1.5">%s</p>
                </td></tr>
                <tr><td style="padding:16px 24px;border-top:1px solid #e1e4ee;color:#646a7f;font-size:12px">
                  Booked on %s. SeatLock is a demo: no real tickets are sold and no money is taken.</td></tr>
                </table></td></tr></table>
                </body></html>
                """.formatted(esc(event.name() == null ? "there" : event.name()), esc(t.eventName()),
                esc(when(t.startsAt())), QR_CONTENT_ID, esc(t.reference()), esc(t.reference()),
                String.join("<br>", venueLines(t).stream().map(BookingMailer::esc).toList()), seats,
                money(t.totalCents()), String.join("<br>", bookedFor(event).stream().map(BookingMailer::esc).toList()),
                findTicketsHtml(event), esc(when(t.bookedAt())));
    }

    private static List<String> venueLines(TicketResponse t) {
        String layout = typeLabel(t.venueType()) + " · " + t.stageLabel();
        return t.venueAddress() == null
                ? List.of(t.venueName(), t.venueCity(), layout)
                : List.of(t.venueName(), t.venueAddress(), t.venueCity(), layout);
    }

    private static List<String> bookedFor(BookingConfirmedEvent event) {
        return Stream.of(event.name(), event.email(), event.phone())
                .filter(line -> line != null && !line.isBlank())
                .toList();
    }

    private String findTicketsText(BookingConfirmedEvent event) {
        return event.name() == null
                ? "Check your tickets any time at %s/#tickets with the email and phone number you booked with."
                        .formatted(properties.baseUrl())
                : "Your tickets are also under My bookings when you log in at %s.".formatted(properties.baseUrl());
    }

    private String findTicketsHtml(BookingConfirmedEvent event) {
        String link = properties.baseUrl() + (event.name() == null ? "/#tickets" : "/#bookings");
        return event.name() == null
                ? "Check your tickets any time on <a href=\"%s\" style=\"color:#5b4bff\">Find my tickets</a> with the email and phone number you booked with."
                        .formatted(esc(link))
                : "Your tickets are also under <a href=\"%s\" style=\"color:#5b4bff\">My bookings</a> when you log in."
                        .formatted(esc(link));
    }

    private String when(Instant instant) {
        return WHEN.withZone(properties.zoneId()).format(instant) + " (" + properties.zoneId().getId() + ")";
    }

    private static String typeLabel(VenueType type) {
        String text = type.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static String money(long cents) {
        return String.format(Locale.ENGLISH, "$%d.%02d", cents / 100, cents % 100);
    }

    private static String esc(String text) {
        return HtmlUtils.htmlEscape(text);
    }
}
