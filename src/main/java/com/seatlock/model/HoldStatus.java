package com.seatlock.model;

public enum HoldStatus {
    ACTIVE,
    CONFIRMED,
    RELEASED,
    EXPIRED;

    public boolean isTerminal() {
        return this != ACTIVE;
    }
}
