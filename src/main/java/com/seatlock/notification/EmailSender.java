package com.seatlock.notification;

import com.seatlock.config.AccountProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends plain-text emails over SMTP. The mail server comes from the standard {@code spring.mail.*}
 * settings; when none is configured, emails are skipped with a warning (their content, which may
 * hold a reset link, is never logged).
 */
@Service
public class EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final AccountProperties properties;

    public EmailSender(ObjectProvider<JavaMailSender> mailSender, AccountProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    public void send(String to, String subject, String body) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.warn("No mail server configured (spring.mail.host); not sending \"{}\"", subject);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mailFrom());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        sender.send(message);
    }
}
