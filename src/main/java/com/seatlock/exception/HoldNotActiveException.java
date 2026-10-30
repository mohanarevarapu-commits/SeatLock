package com.seatlock.exception;

import com.seatlock.model.HoldStatus;

/** The hold has already been confirmed, released or expired. */
public class HoldNotActiveException extends RuntimeException {

    public HoldNotActiveException(Long holdId, HoldStatus status) {
        super("Hold " + holdId + " is " + status);
    }
}
