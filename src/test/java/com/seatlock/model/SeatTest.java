package com.seatlock.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class SeatTest {

    @ParameterizedTest
    @CsvSource({"1, A", "2, B", "26, Z", "27, AA", "28, AB", "52, AZ", "53, BA", "702, ZZ", "703, AAA"})
    void rowLabelUsesSpreadsheetStyleLetters(int row, String expected) {
        assertThat(Seat.rowLabel(row)).isEqualTo(expected);
    }

    @Test
    void rowLabelRejectsNonPositiveRows() {
        assertThatThrownBy(() -> Seat.rowLabel(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void venueBuildsSeatGridForEachSection() {
        Venue venue = new Venue("Hall", "City");
        Section floor = venue.addSection("Floor", 3, 4, 1_000);
        venue.addSection("Balcony", 2, 2, 500);

        assertThat(venue.capacity()).isEqualTo(16);
        assertThat(floor.getSeats()).extracting(Seat::getLabel)
                .startsWith("A1", "A2", "A3", "A4", "B1")
                .endsWith("C4");
    }
}
