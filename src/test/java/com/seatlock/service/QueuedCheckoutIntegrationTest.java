package com.seatlock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.seatlock.dto.CreateHoldRequest;
import com.seatlock.dto.EventResponse;
import com.seatlock.dto.HoldResponse;
import com.seatlock.dto.PurchaseRequestResponse;
import com.seatlock.exception.NotFoundException;
import com.seatlock.model.Account;
import com.seatlock.model.HoldStatus;
import com.seatlock.model.PurchaseRequestStatus;
import com.seatlock.queue.PurchaseRequestMessage;
import com.seatlock.repository.AccountRepository;
import com.seatlock.support.IntegrationTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** Drives the queued checkout path end to end through the embedded Artemis broker. */
class QueuedCheckoutIntegrationTest extends IntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private PurchaseRequestService purchaseRequestService;

    @MockitoSpyBean
    private HoldService holdService;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void queuedRequestEndsUpHoldingTheSeatsForTenMinutes() throws Exception {
        EventResponse event = createEvent();
        Set<Long> wanted = Set.copyOf(seatIds(event.id()).subList(0, 2));

        PurchaseRequestResponse queued = purchaseRequestService.submit(event.id(),
                new CreateHoldRequest("queued-buyer", wanted));
        assertThat(queued.status()).isEqualTo(PurchaseRequestStatus.QUEUED);

        PurchaseRequestResponse done = awaitOutcome(queued.requestId());
        assertThat(done.status()).isEqualTo(PurchaseRequestStatus.HELD);
        HoldResponse hold = holdService.getHold(done.holdId());
        assertThat(hold.status()).isEqualTo(HoldStatus.ACTIVE);
        assertThat(hold.customerRef()).isEqualTo("queued-buyer");
        assertThat(hold.seats()).extracting(s -> s.eventSeatId()).containsExactlyInAnyOrderElementsOf(wanted);
        assertThat(hold.expiresAt()).isEqualTo(START.plus(Duration.ofMinutes(10)));
    }

    @Test
    void competingRequestsForTheSameSeatsHoldThemExactlyOnce() throws Exception {
        EventResponse event = createEvent();
        Set<Long> wanted = Set.copyOf(seatIds(event.id()).subList(0, 2));

        List<String> requestIds = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            requestIds.add(purchaseRequestService.submit(event.id(),
                    new CreateHoldRequest("buyer-" + i, wanted)).requestId());
        }
        List<PurchaseRequestResponse> outcomes = new ArrayList<>();
        for (String id : requestIds) {
            outcomes.add(awaitOutcome(id));
        }

        assertThat(outcomes).filteredOn(o -> o.status() == PurchaseRequestStatus.HELD).hasSize(1);
        assertThat(outcomes).filteredOn(o -> o.status() == PurchaseRequestStatus.REJECTED).hasSize(19)
                .allMatch(o -> o.reason().startsWith("Seats no longer available"));
    }

    @Test
    void unknownEventIsRejectedWithoutRetrying() throws Exception {
        PurchaseRequestResponse queued = purchaseRequestService.submit(999_999L,
                new CreateHoldRequest("lost-buyer", Set.of(1L)));

        PurchaseRequestResponse done = awaitOutcome(queued.requestId());

        assertThat(done.status()).isEqualTo(PurchaseRequestStatus.REJECTED);
        assertThat(done.reason()).isEqualTo("Event 999999 not found");
        verify(holdService, times(1)).createHold(anyLong(), argThat(r -> "lost-buyer".equals(r.customerRef())), any());
    }

    @Test
    void transientFailureIsRetriedUntilItSucceeds() throws Exception {
        EventResponse event = createEvent();
        CannotAcquireLockException lockTimeout = new CannotAcquireLockException("lock wait timeout");
        doThrow(lockTimeout).doThrow(lockTimeout).doCallRealMethod()
                .when(holdService).createHold(anyLong(), argThat(r -> "flaky-buyer".equals(r.customerRef())), any());

        PurchaseRequestResponse queued = purchaseRequestService.submit(event.id(),
                new CreateHoldRequest("flaky-buyer", Set.of(seatIds(event.id()).get(0))));

        assertThat(awaitOutcome(queued.requestId()).status()).isEqualTo(PurchaseRequestStatus.HELD);
        verify(holdService, times(3)).createHold(anyLong(), argThat(r -> "flaky-buyer".equals(r.customerRef())), any());
    }

    @Test
    void persistentFailureEndsOnTheDeadLetterQueueAsFailed() throws Exception {
        EventResponse event = createEvent();
        doThrow(new CannotAcquireLockException("lock wait timeout"))
                .when(holdService).createHold(anyLong(), argThat(r -> "doomed-buyer".equals(r.customerRef())), any());

        PurchaseRequestResponse queued = purchaseRequestService.submit(event.id(),
                new CreateHoldRequest("doomed-buyer", Set.of(seatIds(event.id()).get(0))));

        PurchaseRequestResponse done = awaitOutcome(queued.requestId());
        assertThat(done.status()).isEqualTo(PurchaseRequestStatus.FAILED);
        assertThat(done.reason()).contains("after several retries");
        // max-delivery-attempts = 4: the first delivery plus 3 retries.
        verify(holdService, times(4)).createHold(anyLong(), argThat(r -> "doomed-buyer".equals(r.customerRef())), any());
        assertThat(eventService.getSeatMap(event.id()).availableCount()).isEqualTo(10);
    }

    @Test
    void redeliveredMessageIsNotProcessedTwice() {
        EventResponse event = createEvent();
        PurchaseRequestMessage message = new PurchaseRequestMessage(UUID.randomUUID().toString(), event.id(),
                "twice-buyer", Set.of(seatIds(event.id()).get(0)), START, null);

        purchaseRequestService.process(message);
        purchaseRequestService.process(message);

        assertThat(purchaseRequestService.getStatus(message.requestId()).status()).isEqualTo(PurchaseRequestStatus.HELD);
        verify(holdService, times(1)).createHold(anyLong(), argThat(r -> "twice-buyer".equals(r.customerRef())), any());
    }

    @Test
    void signedInBuyersQueuedRequestIsHeldForTheirAccount() throws Exception {
        EventResponse event = createEvent();
        Account buyer = accountRepository.save(new Account("queue-" + UUID.randomUUID() + "@example.com", "unused",
                "Queue Fan", null, START));

        PurchaseRequestResponse queued = purchaseRequestService.submit(event.id(),
                new CreateHoldRequest("queued-member", Set.of(seatIds(event.id()).get(0))), buyer.getId());
        PurchaseRequestResponse done = awaitOutcome(queued.requestId());

        assertThat(done.status()).isEqualTo(PurchaseRequestStatus.HELD);
        assertThat(holdService.getHold(done.holdId(), buyer.getId()).status()).isEqualTo(HoldStatus.ACTIVE);
        assertThatThrownBy(() -> holdService.getHold(done.holdId())).isInstanceOf(NotFoundException.class);
    }

    private PurchaseRequestResponse awaitOutcome(String requestId) throws InterruptedException {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            PurchaseRequestResponse status = purchaseRequestService.getStatus(requestId);
            if (status.status() != PurchaseRequestStatus.QUEUED) {
                return status;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Purchase request " + requestId + " was not processed within " + TIMEOUT);
    }
}
