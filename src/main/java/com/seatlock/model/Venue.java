package com.seatlock.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @OneToMany(mappedBy = "venue", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<Section> sections = new ArrayList<>();

    protected Venue() {
    }

    public Venue(String name, String city) {
        this.name = name;
        this.city = city;
    }

    public Section addSection(String name, int rows, int seatsPerRow, long priceCents) {
        Section section = new Section(this, name, priceCents);
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

    public List<Section> getSections() {
        return Collections.unmodifiableList(sections);
    }
}
