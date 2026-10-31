package com.seatlock.model;

import com.seatlock.exception.InvalidRequestException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "venues")
public class Venue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 120)
    private String city;

    /** Street address shown on tickets, e.g. "12 Crescent Square"; optional. */
    @Column(length = 200)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VenueType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage_zone", nullable = false, length = 20)
    private MapZone stageZone;

    @Column(name = "stage_label", nullable = false, length = 30)
    private String stageLabel;

    @OneToMany(mappedBy = "venue", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<Section> sections = new ArrayList<>();

    protected Venue() {
    }

    public Venue(String name, String city) {
        this(name, city, VenueType.ARENA, MapZone.NORTH, "STAGE");
    }

    public Venue(String name, String city, VenueType type, MapZone stageZone, String stageLabel) {
        this(name, city, null, type, stageZone, stageLabel);
    }

    public Venue(String name, String city, String address, VenueType type, MapZone stageZone, String stageLabel) {
        this.name = name;
        this.city = city;
        this.address = address == null || address.isBlank() ? null : address.strip();
        this.type = type;
        this.stageZone = stageZone;
        this.stageLabel = stageLabel;
    }

    /** Adds a section across from the stage, facing it. */
    public Section addSection(String name, int rows, int seatsPerRow, long priceCents) {
        return addSection(name, rows, seatsPerRow, priceCents, stageZone.facing());
    }

    public Section addSection(String name, int rows, int seatsPerRow, long priceCents, MapZone zone) {
        if (zone == stageZone) {
            throw new InvalidRequestException("Section '" + name + "' cannot be placed in " + zone
                    + ", where the stage is");
        }
        Section section = new Section(this, name, priceCents, zone);
        for (int row = 1; row <= rows; row++) {
            String rowLabel = Seat.rowLabel(row);
            for (int number = 1; number <= seatsPerRow; number++) {
                section.addSeat(rowLabel, number);
            }
        }
        sections.add(section);
        return section;
    }

    public int capacity() {
        return sections.stream().mapToInt(s -> s.getSeats().size()).sum();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCity() {
        return city;
    }

    public VenueType getType() {
        return type;
    }

    public MapZone getStageZone() {
        return stageZone;
    }

    public String getStageLabel() {
        return stageLabel;
    }

    public String getAddress() {
        return address;
    }

    public List<Section> getSections() {
        return Collections.unmodifiableList(sections);
    }
}
