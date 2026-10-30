package com.seatlock.model;

public enum PurchaseRequestStatus {
    /** On the queue, not processed yet. Never stored: a request has no row until it is processed. */
    QUEUED,
    /** Processed and the seats are held; confirm the hold to book them. */
    HELD,
    /** Processed and refused for a business reason (seats taken, unknown event, ...). Not retried. */
    REJECTED,
    /** Kept failing for a technical reason, was retried, and ended on the dead-letter queue. */
    FAILED
}
