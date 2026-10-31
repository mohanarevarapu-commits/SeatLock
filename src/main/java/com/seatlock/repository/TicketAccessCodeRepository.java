package com.seatlock.repository;

import com.seatlock.model.TicketAccessCode;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketAccessCodeRepository extends JpaRepository<TicketAccessCode, Long> {

    /**
     * The unspent codes for these contact details, newest first, locked so that two checks of the same
     * code at once cannot both succeed or both escape the wrong-attempt count.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c from TicketAccessCode c
            where c.email = :email and c.phone = :phone and c.usedAt is null
            order by c.id desc
            """)
    List<TicketAccessCode> findUnusedForUpdate(@Param("email") String email, @Param("phone") String phone);
}
