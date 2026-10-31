package com.seatlock.repository;

import com.seatlock.model.Booking;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByReference(String reference);

    boolean existsByReference(String reference);

    /** A buyer's bookings with their holds, events and venues loaded, soonest show first. */
    @Query("""
            select b from Booking b
            join fetch b.hold h
            join fetch h.event e
            join fetch e.venue
            where h.accountId = :accountId
            order by e.startsAt, b.id
            """)
    List<Booking> findAllByAccountId(@Param("accountId") Long accountId);

    /**
     * Guest bookings made with this contact email (stored lower-case), loaded like
     * {@link #findAllByAccountId}. The caller still checks the phone number.
     */
    @Query("""
            select b from Booking b
            join fetch b.hold h
            join fetch h.event e
            join fetch e.venue
            where b.contactEmail = :email and h.accountId is null
            order by e.startsAt, b.id
            """)
    List<Booking> findGuestBookingsByContactEmail(@Param("email") String email);
}
