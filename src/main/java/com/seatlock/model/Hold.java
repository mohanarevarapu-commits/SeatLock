package com.seatlock.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * A temporary reservation of one or more event seats while the customer checks out.
 */
@Entity
@Table(name = "holds", indexes = @Index(name = "idx_holds_status_expires", columnList = "status, expires_at"))
public class Hold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id")
    private Event event;

    /** Opaque reference supplied by the caller (e.g. a session id). Never personal data. */
    @Column(name = "customer_ref", nullable = false, length = 64)
    private String customerRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private HoldStatus status = HoldStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "hold_seats", joinColumns = @JoinColumn(name = "hold_id"))
    @Column(name = "event_seat_id", nullable = false)
    private Set<Long> eventSeatIds = new TreeSet<>();

    protected Hold() {
    }

    public Hold(Event event, String customerRef, Set<Long> eventSeatIds, Instant createdAt, Instant expiresAt) {
        this.event = event;
        this.customerRef = customerRef;
        this.eventSeatIds = new TreeSet<>(eventSeatIds);
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public void confirm() {
        transitionTo(HoldStatus.CONFIRMED);
    }

    public void release() {
        transitionTo(HoldStatus.RELEASED);
    }

    public void expire() {
        transitionTo(HoldStatus.EXPIRED);
    }

    private void transitionTo(HoldStatus next) {
        if (status.isTerminal()) {
            throw new IllegalStateException("Hold " + id + " is already " + status);
        }
        this.status = next;
    }

    public Long getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    public String getCustomerRef() {
        return customerRef;
    }

    public HoldStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Set<Long> getEventSeatIds() {
        return Collections.unmodifiableSet(eventSeatIds);
    }
}
