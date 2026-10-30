package com.seatlock.repository;

import com.seatlock.model.EventSeat;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventSeatRepository extends JpaRepository<EventSeat, Long> {

    /**
     * Locks the given rows with {@code SELECT ... FOR UPDATE}. Rows are always locked in
     * ascending id order so two transactions grabbing overlapping seats cannot deadlock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select es from EventSeat es where es.id in :ids order by es.id")
    List<EventSeat> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

    /**
     * Reads only the columns the seat map needs, without loading entities. A large venue has
     * thousands of seats and this is the busiest read during an on-sale, so it stays cheap.
     */
    @Query("""
            select new com.seatlock.repository.SeatMapRow(
                es.id, sec.id, sec.name, sec.zone, sec.priceCents, s.rowLabel, s.number, es.status, es.heldUntil)
            from EventSeat es
            join es.seat s
            join s.section sec
            where es.event.id = :eventId
            order by es.id
            """)
    List<SeatMapRow> findSeatMap(@Param("eventId") Long eventId);

    @Query("""
            select es from EventSeat es
            join fetch es.seat s
            join fetch s.section
            where es.id in :ids
            order by es.id
            """)
    List<EventSeat> findAllWithSeatById(@Param("ids") Collection<Long> ids);
}
