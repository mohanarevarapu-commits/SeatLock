package com.seatlock.dto;

import com.seatlock.model.Account;
import java.time.Instant;

/** A buyer's own profile. Never includes the password hash. */
public record AccountResponse(Long id, String name, String email, String phone, Instant createdAt) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(account.getId(), account.getFullName(), account.getEmail(), account.getPhone(),
                account.getCreatedAt());
    }
}
