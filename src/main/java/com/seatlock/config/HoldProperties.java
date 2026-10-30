package com.seatlock.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Tunables for seat holds, bound from {@code seatlock.holds.*}.
 *
 * @param ttl             how long a hold reserves its seats before it lapses
 * @param maxSeatsPerHold upper bound on seats one hold may reserve
 * @param sweepBatchSize  how many expired holds the sweeper processes per run
 */
@Validated
@ConfigurationProperties("seatlock.holds")
public record HoldProperties(
        @NotNull @DefaultValue("PT10M") Duration ttl,
        @Min(1) @Max(50) @DefaultValue("8") int maxSeatsPerHold,
        @Min(1) @DefaultValue("200") int sweepBatchSize) {
}
