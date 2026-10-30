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
import jakarta.persistence.UniqueConstraint;

/**
 * A physical seat in a venue. Availability is tracked per event in {@link EventSeat}.
 */
@Entity
@Table(name = "seats", uniqueConstraints = @UniqueConstraint(columnNames = {"section_id", "row_label", "seat_number"}))
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id")
    private Section section;

    @Column(name = "row_label", nullable = false, length = 4)
    private String rowLabel;

    @Column(name = "seat_number", nullable = false)
    private int number;

    protected Seat() {
    }

    Seat(Section section, String rowLabel, int number) {
        this.section = section;
        this.rowLabel = rowLabel;
        this.number = number;
    }

    /**
     * Converts a 1-based row index to a spreadsheet-style label: 1 -> A, 26 -> Z, 27 -> AA.
     */
    static String rowLabel(int row) {
        if (row < 1) {
            throw new IllegalArgumentException("row must be >= 1");
        }
        StringBuilder label = new StringBuilder();
        int n = row;
        while (n > 0) {
            n--;
            label.insert(0, (char) ('A' + n % 26));
            n /= 26;
        }
        return label.toString();
    }

    public Long getId() {
        return id;
    }

    public Section getSection() {
        return section;
    }

    public String getRowLabel() {
        return rowLabel;
    }

    public int getNumber() {
        return number;
    }

    public String getLabel() {
        return rowLabel + number;
    }
}
