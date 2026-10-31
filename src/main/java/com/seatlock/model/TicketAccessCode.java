package com.seatlock.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A one-time code (OTP) emailed to a guest so they can see their tickets on Find my tickets. It
 * belongs to the email and phone number the guest booked with. Only a BCrypt hash of the 6-digit
 * code is stored; a code works once, until it expires, and stops working after too many wrong tries.
 */
@Entity
@Table(name = "ticket_access_codes", indexes = @Index(name = "idx_ticket_codes_contact", columnList = "email, phone"))
public class TicketAccessCode {

    public static final int MAX_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Normalised like {@link Account#normalizeEmail}. */
    @Column(nullable = false, length = 254)
    private String email;

    /** Normalised like {@link Booking#normalizePhone}. */
    @Column(nullable = false, length = 24)
    private String phone;

    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "used_at")
    private Instant usedAt;

    protected TicketAccessCode() {
    }

    public TicketAccessCode(String email, String phone, String codeHash, Instant createdAt, Instant expiresAt) {
        this.email = email;
        this.phone = phone;
        this.codeHash = codeHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public boolean isUsableAt(Instant now) {
        return usedAt == null && attempts < MAX_ATTEMPTS && now.isBefore(expiresAt);
    }

    public void recordWrongAttempt() {
        attempts++;
    }

    /** Spends the code: after a successful check, or when a newer code replaces it. */
    public void markUsed(Instant now) {
        if (usedAt == null) {
            usedAt = now;
        }
    }

    public String getCodeHash() {
        return codeHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
