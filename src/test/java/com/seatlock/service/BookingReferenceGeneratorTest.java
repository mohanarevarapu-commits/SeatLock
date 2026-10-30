package com.seatlock.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class BookingReferenceGeneratorTest {

    @RepeatedTest(20)
    void referencesUseOnlyUnambiguousCharacters() {
        String reference = new BookingReferenceGenerator().next();

        assertThat(reference).matches("SL-[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{6}");
    }

    @Test
    void seededGeneratorIsDeterministic() {
        String first = new BookingReferenceGenerator(new Random(42)).next();
        String second = new BookingReferenceGenerator(new Random(42)).next();

        assertThat(first).isEqualTo(second);
    }

    @Test
    void referencesRarelyCollide() {
        BookingReferenceGenerator generator = new BookingReferenceGenerator(new Random(7));
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            seen.add(generator.next());
        }
        assertThat(seen).hasSizeGreaterThan(9_990);
    }
}
