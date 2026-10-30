package com.seatlock.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 12)
    private String reference;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hold_id", unique = true)
    private Hold hold;

    @Column(name = "total_cents", nullable = false)
    private long totalCents;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Booking() {
    }

    public Booking(String reference, Hold hold, long totalCents, Instant createdAt) {
        this.reference = reference;
        this.hold = hold;
        this.totalCents = totalCents;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public Hold getHold() {
        return hold;
    }

    public long getTotalCents() {
        return totalCents;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
