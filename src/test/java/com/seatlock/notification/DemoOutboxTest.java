package com.seatlock.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.seatlock.notification.DemoOutbox.StoredEmail;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

class DemoOutboxTest {

    private final DemoOutbox outbox = new DemoOutbox(Clock.fixed(Instant.parse("2030-01-01T18:00:00Z"), ZoneOffset.UTC));

    @Test
    void keepsTheNewestEmailsFirstAndOnlyTheLastFifty() {
        for (int i = 1; i <= DemoOutbox.KEEP + 5; i++) {
            outbox.store(EmailMessage.plain("fan" + i + "@example.com", "Email " + i, "Body " + i));
        }

        List<StoredEmail> emails = outbox.list();
        assertThat(emails).hasSize(DemoOutbox.KEEP);
        assertThat(emails.get(0).subject()).isEqualTo("Email 55");
        assertThat(emails.get(DemoOutbox.KEEP - 1).subject()).isEqualTo("Email 6");
    }

    @Test
    void imagesInTheHtmlBecomeDataUrisSoABrowserCanShowThem() {
        byte[] png = {1, 2, 3};
        outbox.store(new EmailMessage("fan@example.com", "Ticket", "text", "<img src=\"cid:ticket-qr\">",
                List.of(new EmailMessage.InlineImage("ticket-qr", png, "image/png"))));

        assertThat(outbox.list().get(0).html())
                .isEqualTo("<img src=\"data:image/png;base64," + Base64.getEncoder().encodeToString(png) + "\">");
    }
}
