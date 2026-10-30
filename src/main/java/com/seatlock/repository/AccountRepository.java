package com.seatlock.repository;

import com.seatlock.model.Account;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {

    /** Expects an already normalised (lower-case) email; see {@link Account#normalizeEmail}. */
    Optional<Account> findByEmail(String email);

    boolean existsByEmail(String email);
}
