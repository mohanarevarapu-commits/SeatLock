package com.seatlock.service;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;

/**
 * Generates short, human-readable booking references such as {@code SL-7KQ2MX}.
 * The alphabet leaves out characters that are easy to misread (0/O, 1/I/L).
 */
@Component
public class BookingReferenceGenerator {

    static final String PREFIX = "SL-";
    static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    static final int LENGTH = 6;

    private final RandomGenerator random;

    public BookingReferenceGenerator() {
        this(new SecureRandom());
    }

    BookingReferenceGenerator(RandomGenerator random) {
        this.random = random;
    }

    public String next() {
        StringBuilder reference = new StringBuilder(PREFIX);
        for (int i = 0; i < LENGTH; i++) {
            reference.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return reference.toString();
    }
}
