package com.seatlock.exception;

public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException() {
        super("An account with this email already exists. Sign in, or reset your password if you forgot it.");
    }
}
