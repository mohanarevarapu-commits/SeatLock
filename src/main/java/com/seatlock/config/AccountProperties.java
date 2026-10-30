package com.seatlock.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Settings for accounts and the emails sent to them, bound from {@code seatlock.accounts.*}.
 *
 * @param resetTokenTtl how long a forgot-password link works
 * @param baseUrl       address of the web client, used to build links in emails
 * @param mailFrom      sender of SeatLock emails; with Gmail this must be the signed-in address
 */
@Validated
@ConfigurationProperties("seatlock.accounts")
public record AccountProperties(
        @NotNull @DefaultValue("PT30M") Duration resetTokenTtl,
        @NotBlank @DefaultValue("http://localhost:8080") String baseUrl,
        @NotBlank @DefaultValue("SeatLock <no-reply@seatlock.local>") String mailFrom) {
}
