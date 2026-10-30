package com.seatlock.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id")
    private Venue venue;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    protected Event() {
    }

    public Event(Venue venue, String name, Instant startsAt) {
        this.venue = venue;
        this.name = name;
        this.startsAt = startsAt;
    }

    public boolean hasStarted(Instant now) {
        return !now.isBefore(startsAt);
    }

    public Long getId() {
        return id;
    }

    public Venue getVenue() {
        return venue;
    }

    public String getName() {
        return name;
    }

    public Instant getStartsAt() {
        return startsAt;
    }
}
