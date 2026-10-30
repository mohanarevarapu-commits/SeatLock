package com.seatlock.service;

import com.seatlock.config.HoldProperties;
import com.seatlock.model.Hold;
import com.seatlock.model.HoldStatus;
import com.seatlock.repository.EventSeatRepository;
import com.seatlock.repository.HoldRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Marks lapsed holds as expired and frees their seats. Each hold is expired in its
 * own short transaction so one failure (or a concurrent confirm) does not roll back
 * the whole batch.
 */
@Service
public class HoldExpiryService {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryService.class);

    private final HoldRepository holdRepository;
    private final EventSeatRepository eventSeatRepository;
    private final TransactionTemplate transactionTemplate;
    private final HoldProperties properties;
    private final Clock clock;

    public HoldExpiryService(HoldRepository holdRepository, EventSeatRepository eventSeatRepository,
                             TransactionTemplate transactionTemplate, HoldProperties properties, Clock clock) {
        this.holdRepository = holdRepository;
        this.eventSeatRepository = eventSeatRepository;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    /** Returns the number of holds expired in this run. */
    public int expireLapsedHolds() {
        Instant now = clock.instant();
        List<Long> candidates = holdRepository.findExpiredActiveIds(now, PageRequest.of(0, properties.sweepBatchSize()));
        int expired = 0;
        for (Long holdId : candidates) {
            try {
                if (Boolean.TRUE.equals(transactionTemplate.execute(status -> expireOne(holdId, now)))) {
                    expired++;
                }
            } catch (DataAccessException ex) {
                log.warn("Could not expire hold {}; will retry on next sweep", holdId, ex);
            }
        }
        if (expired > 0) {
            log.info("Expired {} lapsed hold(s)", expired);
        }
        return expired;
    }

    private boolean expireOne(Long holdId, Instant now) {
        Hold hold = holdRepository.findByIdForUpdate(holdId).orElse(null);
        // Re-check under the lock: the hold may have been confirmed or released since we listed it.
        if (hold == null || hold.getStatus() != HoldStatus.ACTIVE || !hold.isExpiredAt(now)) {
            return false;
        }
        eventSeatRepository.findAllByIdForUpdate(hold.getEventSeatIds())
                .forEach(seat -> seat.releaseIfHeldBy(holdId));
        hold.expire();
        return true;
    }
}
