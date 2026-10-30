package com.seatlock.queue;

import com.seatlock.service.PurchaseRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

/**
 * Drains the purchase queue. Listener sessions are transacted, so an exception here rolls the
 * message back onto the queue for the broker to retry (see {@link TicketQueueConfig}).
 */
@Component
public class PurchaseRequestConsumer {

    private static final Logger log = LoggerFactory.getLogger(PurchaseRequestConsumer.class);

    private final PurchaseRequestService purchaseRequestService;

    public PurchaseRequestConsumer(PurchaseRequestService purchaseRequestService) {
        this.purchaseRequestService = purchaseRequestService;
    }

    @JmsListener(destination = TicketQueueConfig.PURCHASE_QUEUE, concurrency = "${seatlock.queue.consumers:1-4}")
    public void onPurchaseRequest(PurchaseRequestMessage message) {
        purchaseRequestService.process(message);
    }

    @JmsListener(destination = TicketQueueConfig.DEAD_LETTER_QUEUE)
    public void onDeadLetter(PurchaseRequestMessage message) {
        log.warn("Purchase request {} for event {} failed after all retries", message.requestId(), message.eventId());
        purchaseRequestService.markFailed(message);
    }
}
