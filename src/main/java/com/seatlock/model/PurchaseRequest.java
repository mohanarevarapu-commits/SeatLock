package com.seatlock.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * The outcome of a queued purchase request. A row is written only once the consumer has
 * processed the request, so the producer never touches the database. The id is the request id
 * handed to the client, which also makes processing idempotent: a redelivered message whose id
 * already has a row is skipped.
 */
@Entity
@Table(name = "purchase_requests")
public class PurchaseRequest {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "customer_ref", nullable = false, length = 64)
    private String customerRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PurchaseRequestStatus status;

    @Column(name = "hold_id")
    private Long holdId;

    @Column(length = 255)
    private String reason;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected PurchaseRequest() {
    }

    private PurchaseRequest(String id, Long eventId, String customerRef, PurchaseRequestStatus status,
                            Long holdId, String reason, Instant processedAt) {
        this.id = id;
        this.eventId = eventId;
        this.customerRef = customerRef;
        this.status = status;
        this.holdId = holdId;
        this.reason = reason == null || reason.length() <= 255 ? reason : reason.substring(0, 255);
        this.processedAt = processedAt;
    }

    public static PurchaseRequest held(String id, Long eventId, String customerRef, Long holdId, Instant now) {
        return new PurchaseRequest(id, eventId, customerRef, PurchaseRequestStatus.HELD, holdId, null, now);
    }

    public static PurchaseRequest rejected(String id, Long eventId, String customerRef, String reason, Instant now) {
        return new PurchaseRequest(id, eventId, customerRef, PurchaseRequestStatus.REJECTED, null, reason, now);
    }

    public static PurchaseRequest failed(String id, Long eventId, String customerRef, String reason, Instant now) {
        return new PurchaseRequest(id, eventId, customerRef, PurchaseRequestStatus.FAILED, null, reason, now);
    }

    public String getId() {
        return id;
    }

    public Long getEventId() {
        return eventId;
    }

    public String getCustomerRef() {
        return customerRef;
    }

    public PurchaseRequestStatus getStatus() {
        return status;
    }

    public Long getHoldId() {
        return holdId;
    }

    public String getReason() {
        return reason;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
