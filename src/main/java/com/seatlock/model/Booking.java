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

    /** A guest's contact details, given when paying; null for signed-in buyers and API clients. */
    @Column(name = "contact_email", length = 254)
    private String contactEmail;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    protected Booking() {
    }

    public Booking(String reference, Hold hold, long totalCents, Instant createdAt) {
        this(reference, hold, totalCents, createdAt, null, null);
    }

    public Booking(String reference, Hold hold, long totalCents, Instant createdAt, String contactEmail,
                   String contactPhone) {
        this.reference = reference;
        this.hold = hold;
        this.totalCents = totalCents;
        this.createdAt = createdAt;
        String email = blankToNull(contactEmail);
        this.contactEmail = email == null ? null : Account.normalizeEmail(email);
        this.contactPhone = blankToNull(contactPhone);
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

    public String getContactEmail() {
        return contactEmail;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    /**
     * A phone number reduced to what identifies it: digits, plus a leading {@code +} if it had one.
     * "+44 (20) 7946-0000" and "+442079460000" are the same number.
     */
    public static String normalizePhone(String phone) {
        if (phone == null) {
            return null;
        }
        String trimmed = phone.strip();
        String digits = trimmed.replaceAll("[^0-9]", "");
        return trimmed.startsWith("+") ? "+" + digits : digits;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
