package com.seatlock.notification;

import com.seatlock.config.AccountProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Sends emails over SMTP. The mail server comes from the standard {@code spring.mail.*} settings.
 * When none is configured, emails go to the {@link DemoOutbox} if it is on, and are otherwise
 * skipped with a warning. Their content (which may hold a reset link or a ticket code) is never logged.
 */
@Service
public class EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final ObjectProvider<DemoOutbox> outbox;
    private final AccountProperties properties;

    public EmailSender(ObjectProvider<JavaMailSender> mailSender, ObjectProvider<DemoOutbox> outbox,
                       AccountProperties properties) {
        this.mailSender = mailSender;
        this.outbox = outbox;
        this.properties = properties;
    }

    public void send(String to, String subject, String body) {
        send(EmailMessage.plain(to, subject, body));
    }

    public void send(EmailMessage message) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            DemoOutbox demoOutbox = outbox.getIfAvailable();
            if (demoOutbox != null) {
                demoOutbox.store(message);
                log.info("No mail server configured; kept \"{}\" in the demo outbox", message.subject());
            } else {
                log.warn("No mail server configured (spring.mail.host); not sending \"{}\"", message.subject());
            }
            return;
        }
        if (message.html() == null) {
            SimpleMailMessage plain = new SimpleMailMessage();
            plain.setFrom(properties.mailFrom());
            plain.setTo(message.to());
            plain.setSubject(message.subject());
            plain.setText(message.text());
            sender.send(plain);
            return;
        }
        MimeMessage mime = sender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(properties.mailFrom());
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.text(), message.html());
            for (EmailMessage.InlineImage image : message.images()) {
                helper.addInline(image.contentId(), new ByteArrayResource(image.data()), image.contentType());
            }
        } catch (MessagingException ex) {
            throw new MailPreparationException("Could not build the email \"" + message.subject() + "\"", ex);
        }
        sender.send(mime);
    }
}
