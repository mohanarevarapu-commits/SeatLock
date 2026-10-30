package com.seatlock.repository;

import com.seatlock.model.Hold;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HoldRepository extends JpaRepository<Hold, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Hold h where h.id = :id")
    Optional<Hold> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select h.id from Hold h
            where h.status = com.seatlock.model.HoldStatus.ACTIVE and h.expiresAt <= :now
            order by h.expiresAt
            """)
    List<Long> findExpiredActiveIds(@Param("now") Instant now, Pageable page);
}
