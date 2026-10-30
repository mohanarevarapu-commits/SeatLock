package com.seatlock.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Tunables for the queued checkout path, bound from {@code seatlock.queue.*}.
 *
 * @param consumers            listener concurrency, e.g. {@code 1-4}; requests for one event are
 *                             still processed one at a time because they share a message group
 * @param maxDeliveryAttempts  total tries (first delivery + retries) before a message goes to the dead-letter queue
 * @param redeliveryDelay      wait before the first retry
 * @param redeliveryMultiplier factor applied to the wait on each further retry
 */
@Validated
@ConfigurationProperties("seatlock.queue")
public record QueueProperties(
        @NotBlank @DefaultValue("1-4") String consumers,
        @Min(1) @DefaultValue("4") int maxDeliveryAttempts,
        @NotNull @DefaultValue("PT0.5S") Duration redeliveryDelay,
        @DecimalMin("1.0") @DefaultValue("2.0") double redeliveryMultiplier) {
}
