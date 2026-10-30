package com.seatlock.exception;

/** The hold passed its expiry time before it could be confirmed. */
public class HoldExpiredException extends RuntimeException {

    public HoldExpiredException(Long holdId) {
        super("Hold " + holdId + " has expired");
    }
}
