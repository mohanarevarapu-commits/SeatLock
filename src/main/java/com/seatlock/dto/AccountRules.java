package com.seatlock.dto;

/** Validation rules shared by the account requests. */
final class AccountRules {

    /** Empty, or 7 to 20 digits, spaces, brackets and dashes, optionally starting with +. */
    static final String PHONE = "^$|^\\+?[0-9 ()-]{7,20}$";
    static final String PHONE_MESSAGE = "must be a phone number such as +44 20 7946 0000";

    private AccountRules() {
    }
}
