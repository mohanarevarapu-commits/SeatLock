package com.seatlock.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Settings for accounts and the emails SeatLock sends, bound from {@code seatlock.accounts.*}.
 *
 * @param resetTokenTtl how long a forgot-password link works
 * @param baseUrl       address of the web client, used to build links in emails
 * @param mailFrom      sender of SeatLock emails; with Gmail this must be the signed-in address
 * @param timeZone      time zone for concert times in emails, e.g. "Asia/Kolkata"; empty means the
 *                      server's own time zone (the one the demo shows are scheduled in)
 */
@Validated
@ConfigurationProperties("seatlock.accounts")
public record AccountProperties(
        @NotNull @DefaultValue("PT30M") Duration resetTokenTtl,
        @NotBlank @DefaultValue("http://localhost:8080") String baseUrl,
        @NotBlank @DefaultValue("SeatLock <no-reply@seatlock.local>") String mailFrom,
        @DefaultValue("") String timeZone) {

    public AccountProperties {
        if (!timeZone.isBlank()) {
            ZoneId.of(timeZone);   // refuse to start with a misspelt zone rather than fail on the first email
        }
    }

    public ZoneId zoneId() {
        return timeZone.isBlank() ? ZoneId.systemDefault() : ZoneId.of(timeZone);
    }
}
