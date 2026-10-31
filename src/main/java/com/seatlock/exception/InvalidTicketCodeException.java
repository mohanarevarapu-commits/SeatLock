package com.seatlock.exception;

/**
 * A Find my tickets code that is wrong, used, expired, or tried too often. All four get the same
 * answer, so the response says nothing about which one it was.
 */
public class InvalidTicketCodeException extends RuntimeException {

    public InvalidTicketCodeException() {
        super("That code is wrong or has expired. Ask for a new code and try again.");
    }
}
