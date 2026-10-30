package com.seatlock.service;

import com.seatlock.dto.CreateHoldRequest;
import com.seatlock.dto.HoldResponse;
import com.seatlock.dto.PurchaseRequestResponse;
import com.seatlock.exception.InvalidRequestException;
import com.seatlock.exception.NotFoundException;
import com.seatlock.exception.SeatUnavailableException;
import com.seatlock.model.PurchaseRequest;
import com.seatlock.queue.PurchaseRequestMessage;
import com.seatlock.queue.TicketQueueConfig;
import com.seatlock.repository.PurchaseRequestRepository;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The queued checkout path. {@link #submit} only puts the request on the queue, so a traffic
 * spike lands on the broker instead of the database. {@link #process} is called by the consumer
 * and places the hold through {@link HoldService}, so all the usual locking rules still apply.
 */
@Service
public class PurchaseRequestService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseRequestService.class);

    private final JmsTemplate jmsTemplate;
    private final PurchaseRequestRepository purchaseRequestRepository;
    private final HoldService holdService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public PurchaseRequestService(JmsTemplate jmsTemplate, PurchaseRequestRepository purchaseRequestRepository,
                                  HoldService holdService, TransactionTemplate transactionTemplate, Clock clock) {
        this.jmsTemplate = jmsTemplate;
        this.purchaseRequestRepository = purchaseRequestRepository;
        this.holdService = holdService;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    /** Queues the request and returns at once. Nothing is written to the database here. */
    public PurchaseRequestResponse submit(Long eventId, CreateHoldRequest request) {
        String requestId = UUID.randomUUID().toString();
        PurchaseRequestMessage message = new PurchaseRequestMessage(requestId, eventId, request.customerRef(),
                request.eventSeatIds(), clock.instant());
        jmsTemplate.convertAndSend(TicketQueueConfig.PURCHASE_QUEUE, message, jms -> {
            // One message group per event: requests for an event are processed in order, one at a time.
            jms.setStringProperty(TicketQueueConfig.GROUP_ID_PROPERTY, "event-" + eventId);
            return jms;
        });
        return PurchaseRequestResponse.queued(requestId, eventId);
    }

    /** Requests with no outcome yet (still on the queue, or an unknown id) report {@code QUEUED}. */
    @Transactional(readOnly = true)
    public PurchaseRequestResponse getStatus(String requestId) {
        return purchaseRequestRepository.findById(requestId)
                .map(PurchaseRequestResponse::from)
                .orElseGet(() -> PurchaseRequestResponse.queued(requestId, null));
    }

    /**
     * Places the hold for one queued request. A business refusal is recorded as {@code REJECTED}
     * and is final. Any other exception is rethrown so the broker rolls back and redelivers the
     * message, and after the last attempt moves it to the dead-letter queue.
     */
    public void process(PurchaseRequestMessage message) {
        if (purchaseRequestRepository.existsById(message.requestId())) {
            log.debug("Purchase request {} already processed; skipping redelivery", message.requestId());
            return;
        }
        try {
            transactionTemplate.executeWithoutResult(status -> {
                HoldResponse hold = holdService.createHold(message.eventId(),
                        new CreateHoldRequest(message.customerRef(), message.eventSeatIds()));
                purchaseRequestRepository.save(PurchaseRequest.held(message.requestId(), message.eventId(),
                        message.customerRef(), hold.id(), clock.instant()));
            });
        } catch (SeatUnavailableException | NotFoundException | InvalidRequestException ex) {
            transactionTemplate.executeWithoutResult(status -> purchaseRequestRepository.save(PurchaseRequest.rejected(
                    message.requestId(), message.eventId(), message.customerRef(), ex.getMessage(), clock.instant())));
        }
    }

    /** Records a request that used up its retries and landed on the dead-letter queue. */
    @Transactional
    public void markFailed(PurchaseRequestMessage message) {
        if (!purchaseRequestRepository.existsById(message.requestId())) {
            purchaseRequestRepository.save(PurchaseRequest.failed(message.requestId(), message.eventId(),
                    message.customerRef(), "Could not be processed after several retries; please try again",
                    clock.instant()));
        }
    }
}
