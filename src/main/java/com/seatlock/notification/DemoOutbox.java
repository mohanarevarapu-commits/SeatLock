package com.seatlock.notification;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Keeps the emails SeatLock would have sent, in memory, when no mail server is set up, so the demo
 * can show booking emails and ticket codes. Only on with {@code seatlock.mail.demo-outbox=true} (the
 * {@code demo} profile), and it can only be read from the same machine; see DemoOutboxController.
 * It holds the newest {@value #KEEP} emails and forgets them all when the app stops.
 */
@Component
@ConditionalOnProperty(name = "seatlock.mail.demo-outbox", havingValue = "true")
public class DemoOutbox {

    static final int KEEP = 50;

    private final Deque<StoredEmail> emails = new ConcurrentLinkedDeque<>();
    private final AtomicLong ids = new AtomicLong();
    private final Clock clock;

    public DemoOutbox(Clock clock) {
        this.clock = clock;
    }

    public void store(EmailMessage message) {
        emails.addFirst(new StoredEmail(ids.incrementAndGet(), message.to(), message.subject(), clock.instant(),
                message.text(), withImagesInline(message)));
        while (emails.size() > KEEP) {
            emails.pollLast();
        }
    }

    /** Newest first. */
    public List<StoredEmail> list() {
        return new ArrayList<>(emails);
    }

    /** The HTML body with each {@code cid:} image swapped for a data URI, so a browser can show it as is. */
    private static String withImagesInline(EmailMessage message) {
        if (message.html() == null) {
            return null;
        }
        String html = message.html();
        for (EmailMessage.InlineImage image : message.images()) {
            html = html.replace("cid:" + image.contentId(), "data:" + image.contentType() + ";base64,"
                    + Base64.getEncoder().encodeToString(image.data()));
        }
        return html;
    }

    public record StoredEmail(long id, String to, String subject, Instant sentAt, String text, String html) {
    }
}
