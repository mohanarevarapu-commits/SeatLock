package com.seatlock.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.seatlock.dto.CreateHoldRequest;
import com.seatlock.dto.EventResponse;
import com.seatlock.dto.HoldResponse;
import com.seatlock.dto.SeatMapResponse;
import com.seatlock.exception.SeatUnavailableException;
import com.seatlock.model.HoldStatus;
import com.seatlock.model.SeatStatus;
import com.seatlock.support.IntegrationTest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * Fires many simultaneous hold requests at the same seats and checks that no seat is
 * ever handed to two customers. These tests use real threads and real commits.
 */
class ConcurrentHoldIntegrationTest extends IntegrationTest {

    private static final int THREADS = 200;

    @Autowired
    private HoldService holdService;

    @Autowired
    private BookingService bookingService;

    @Test
    void exactlyOneCustomerWinsTheSameSeats() throws Exception {
        EventResponse event = createEvent();
        List<Long> seats = seatIds(event.id());
        Set<Long> wanted = Set.of(seats.get(0), seats.get(1));

        List<Outcome> outcomes = race(i -> () -> holdService.createHold(event.id(),
                new CreateHoldRequest("racer-" + i, wanted)));

        assertThat(outcomes).filteredOn(Outcome::won).hasSize(1);
        assertThat(outcomes).filteredOn(o -> !o.won()).allMatch(Outcome::rejectedCleanly);
    }

    @Test
    void overlappingRequestsNeverDoubleHoldAndNeverDeadlock() throws Exception {
        EventResponse event = createEvent();
        List<Long> seats = seatIds(event.id());

        // Each racer wants a sliding window of 3 seats, so neighbours overlap. Locking
        // overlapping rows in inconsistent order would deadlock here.
        List<Outcome> outcomes = race(i -> () -> {
            int start = i % (seats.size() - 2);
            Set<Long> window = Set.copyOf(seats.subList(start, start + 3));
            return holdService.createHold(event.id(), new CreateHoldRequest("racer-" + i, window));
        });

        assertThat(outcomes).allMatch(o -> o.won() || o.rejectedCleanly());

        List<Long> heldByWinners = outcomes.stream()
                .filter(Outcome::won)
                .flatMap(o -> o.hold().seats().stream().map(s -> s.eventSeatId()))
                .toList();
        assertThat(heldByWinners).doesNotHaveDuplicates();

        long heldOnMap = eventService.getSeatMap(event.id()).sections().stream()
                .flatMap(s -> s.seats().stream())
                .map(SeatMapResponse.SeatView::status)
                .filter(status -> status == SeatStatus.HELD)
                .count();
        assertThat(heldOnMap).isEqualTo(heldByWinners.size());
    }

    @Test
    void mixedHoldConfirmAndReleaseTrafficSellsEachSeatAtMostOnce() throws Exception {
        EventResponse event = createEvent(10, 10);
        List<Long> hotSeats = seatIds(event.id()).subList(0, 40);

        // Every racer fights for 3 of the same 40 seats, retrying when beaten, then confirms,
        // releases or keeps its hold. Releases free seats mid-race for others to grab, so
        // create, confirm and release all lock the same rows at the same time.
        List<Outcome> outcomes = race(i -> () -> {
            Random random = new Random(i);
            for (int attempt = 0; ; attempt++) {
                List<Long> shuffled = new ArrayList<>(hotSeats);
                Collections.shuffle(shuffled, random);
                try {
                    HoldResponse hold = holdService.createHold(event.id(),
                            new CreateHoldRequest("racer-" + i, Set.copyOf(shuffled.subList(0, 3))));
                    switch (i % 3) {
                        case 0 -> bookingService.confirmHold(hold.id());
                        case 1 -> holdService.releaseHold(hold.id());
                        default -> { }
                    }
                    return holdService.getHold(hold.id());
                } catch (SeatUnavailableException | ObjectOptimisticLockingFailureException ex) {
                    if (attempt == 4) {
                        throw ex;
                    }
                }
            }
        });

        assertThat(outcomes).allMatch(o -> o.won() || o.rejectedCleanly());

        List<Long> booked = seatsOfHoldsWithStatus(outcomes, HoldStatus.CONFIRMED);
        List<Long> held = seatsOfHoldsWithStatus(outcomes, HoldStatus.ACTIVE);
        assertThat(booked).isNotEmpty();
        assertThat(Stream.concat(booked.stream(), held.stream()).toList()).doesNotHaveDuplicates();

        Map<SeatStatus, Long> onMap = eventService.getSeatMap(event.id()).sections().stream()
                .flatMap(s -> s.seats().stream())
                .collect(Collectors.groupingBy(SeatMapResponse.SeatView::status, Collectors.counting()));
        assertThat(onMap.getOrDefault(SeatStatus.BOOKED, 0L)).isEqualTo(booked.size());
        assertThat(onMap.getOrDefault(SeatStatus.HELD, 0L)).isEqualTo(held.size());
    }

    private static List<Long> seatsOfHoldsWithStatus(List<Outcome> outcomes, HoldStatus status) {
        return outcomes.stream()
                .filter(o -> o.won() && o.hold().status() == status)
                .flatMap(o -> o.hold().seats().stream().map(s -> s.eventSeatId()))
                .toList();
    }

    private List<Outcome> race(IntFunction<Callable<HoldResponse>> attempt) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startGun = new CountDownLatch(1);
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                Callable<HoldResponse> call = attempt.apply(i);
                futures.add(pool.submit(() -> {
                    startGun.await();
                    try {
                        return Outcome.success(call.call());
                    } catch (RuntimeException ex) {
                        return Outcome.failure(ex);
                    }
                }));
            }
            startGun.countDown();

            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private record Outcome(HoldResponse hold, RuntimeException error) {

        static Outcome success(HoldResponse hold) {
            return new Outcome(hold, null);
        }

        static Outcome failure(RuntimeException error) {
            return new Outcome(null, error);
        }

        boolean won() {
            return hold != null;
        }

        /** Losing is fine as long as it is one of the expected "someone beat you" errors. */
        boolean rejectedCleanly() {
            return error instanceof SeatUnavailableException
                    || error instanceof ObjectOptimisticLockingFailureException;
        }
    }
}
