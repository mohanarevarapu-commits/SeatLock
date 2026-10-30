package com.seatlock.model;

import jakarta.persistence.Column;
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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;

/**
 * The state of one seat for one event. This is the row that gets locked when
 * customers compete for a seat.
 *
 * <p>A seat whose hold has passed its expiry is treated as available even before
 * the expiry sweeper has cleaned it up, so a slow sweeper never blocks sales.
 */
@Entity
@Table(name = "event_seats",
        uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "seat_id"}),
        indexes = @Index(name = "idx_event_seats_hold", columnList = "hold_id"))
public class EventSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id")
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id")
    private Seat seat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SeatStatus status = SeatStatus.AVAILABLE;

    @Column(name = "hold_id")
    private Long holdId;

    @Column(name = "held_until")
    private Instant heldUntil;

    @Version
    private long version;

    protected EventSeat() {
    }

    public EventSeat(Event event, Seat seat) {
        this.event = event;
        this.seat = seat;
    }

    public boolean isAvailableAt(Instant now) {
        return status == SeatStatus.AVAILABLE
                || (status == SeatStatus.HELD && !now.isBefore(heldUntil));
    }

    public boolean isHeldBy(Long holdId, Instant now) {
        return status == SeatStatus.HELD
                && Objects.equals(this.holdId, holdId)
                && now.isBefore(heldUntil);
    }

    /** Status as a customer would see it at {@code now}, with lapsed holds shown as available. */
    public SeatStatus effectiveStatus(Instant now) {
        return isAvailableAt(now) ? SeatStatus.AVAILABLE : status;
    }

    public void hold(Long holdId, Instant until, Instant now) {
        if (!isAvailableAt(now)) {
            throw new IllegalStateException("Seat " + id + " is not available");
        }
        this.status = SeatStatus.HELD;
        this.holdId = holdId;
        this.heldUntil = until;
    }

    public void book(Long holdId, Instant now) {
        if (!isHeldBy(holdId, now)) {
            throw new IllegalStateException("Seat " + id + " is not held by hold " + holdId);
        }
        this.status = SeatStatus.BOOKED;
        this.heldUntil = null;
    }

    /**
     * Frees the seat if it is still held by {@code holdId}. Returns false when the
     * seat has since been booked or re-held by someone else, in which case it is left alone.
     */
    public boolean releaseIfHeldBy(Long holdId) {
        if (status != SeatStatus.HELD || !Objects.equals(this.holdId, holdId)) {
            return false;
        }
        this.status = SeatStatus.AVAILABLE;
        this.holdId = null;
        this.heldUntil = null;
        return true;
    }

    public Long getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    public Seat getSeat() {
        return seat;
    }

    public SeatStatus getStatus() {
        return status;
    }

    public Long getHoldId() {
        return holdId;
    }

    public Instant getHeldUntil() {
        return heldUntil;
    }
}
