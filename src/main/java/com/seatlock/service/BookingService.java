package com.seatlock.service;

import com.seatlock.dto.BookingResponse;
import com.seatlock.dto.ConfirmHoldRequest;
import com.seatlock.dto.HoldResponse;
import com.seatlock.dto.TicketResponse;
import com.seatlock.exception.HoldExpiredException;
import com.seatlock.exception.HoldNotActiveException;
import com.seatlock.exception.NotFoundException;
import com.seatlock.model.Booking;
import com.seatlock.model.EventSeat;
import com.seatlock.model.Hold;
import com.seatlock.model.HoldStatus;
import com.seatlock.notification.BookingConfirmedEvent;
import com.seatlock.repository.AccountRepository;
import com.seatlock.repository.BookingRepository;
import com.seatlock.repository.EventSeatRepository;
import com.seatlock.repository.HoldRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns an active hold into a confirmed booking and emails the details to the buyer (their account
 * email, or the contact email a guest gives when paying). Payment is out of scope: confirming a hold
 * assumes the caller has already taken payment.
 */
@Service
public class BookingService {

    private static final int MAX_REFERENCE_ATTEMPTS = 5;

    private final BookingRepository bookingRepository;
    private final HoldRepository holdRepository;
    private final EventSeatRepository eventSeatRepository;
    private final HoldService holdService;
    private final BookingReferenceGenerator referenceGenerator;
    private final AccountRepository accountRepository;
    private final ApplicationEventPublisher events;
    private final QrCodeGenerator qrCodes;
    private final Clock clock;

    public BookingService(BookingRepository bookingRepository, HoldRepository holdRepository,
                          EventSeatRepository eventSeatRepository, HoldService holdService,
                          BookingReferenceGenerator referenceGenerator, AccountRepository accountRepository,
                          ApplicationEventPublisher events, QrCodeGenerator qrCodes, Clock clock) {
        this.bookingRepository = bookingRepository;
        this.holdRepository = holdRepository;
        this.eventSeatRepository = eventSeatRepository;
        this.holdService = holdService;
        this.referenceGenerator = referenceGenerator;
        this.accountRepository = accountRepository;
        this.events = events;
        this.qrCodes = qrCodes;
        this.clock = clock;
    }

    @Transactional
    public BookingResponse confirmHold(Long holdId) {
        return confirmHold(holdId, null);
    }

    @Transactional
    public BookingResponse confirmHold(Long holdId, Long accountId) {
        return confirmHold(holdId, accountId, null);
    }

    /**
     * @param accountId the signed-in buyer, or null for a guest; must match the hold's account
     * @param contact   a guest's email and phone for the booking, or null; ignored for signed-in buyers
     */
    @Transactional
    public BookingResponse confirmHold(Long holdId, Long accountId, ConfirmHoldRequest contact) {
        Instant now = clock.instant();
        Hold hold = holdRepository.findByIdForUpdate(holdId)
                .orElseThrow(() -> new NotFoundException("Hold", holdId));
        HoldService.requireAccess(hold, accountId);
        if (hold.getStatus() != HoldStatus.ACTIVE) {
            throw new HoldNotActiveException(holdId, hold.getStatus());
        }
        if (hold.isExpiredAt(now)) {
            throw new HoldExpiredException(holdId);
        }

        List<EventSeat> seats = eventSeatRepository.findAllByIdForUpdate(hold.getEventSeatIds());
        if (seats.stream().anyMatch(seat -> !seat.isHeldBy(holdId, now))) {
            throw new HoldExpiredException(holdId);
        }
        seats.forEach(seat -> seat.book(holdId, now));
        hold.confirm();

        HoldResponse view = holdService.toResponse(hold);
        boolean guest = hold.getAccountId() == null && contact != null;
        Booking booking = bookingRepository.save(new Booking(uniqueReference(), hold, view.totalCents(), now,
                guest ? contact.contactEmail() : null, guest ? contact.contactPhone() : null));
        publishConfirmation(hold, booking, view);
        return toResponse(booking, view);
    }

    /**
     * The ticket email, sent after commit by BookingMailer: to the account's email for a signed-in
     * buyer, or to the guest's contact email. A booking with neither gets no email.
     */
    private void publishConfirmation(Hold hold, Booking booking, HoldResponse view) {
        String email = booking.getContactEmail();
        String phone = booking.getContactPhone();
        String name = null;
        if (hold.getAccountId() != null) {
            var account = accountRepository.findById(hold.getAccountId()).orElse(null);
            if (account != null) {
                email = account.getEmail();
                phone = account.getPhone();
                name = account.getFullName();
            }
        }
        if (email == null) {
            return;
        }
        events.publishEvent(new BookingConfirmedEvent(email, name, phone, toTicket(booking, view)));
    }

    /** The QR code printed on a booking's ticket, as a PNG; it holds the booking reference. */
    @Transactional(readOnly = true)
    public byte[] ticketQrCode(String reference) {
        if (!bookingRepository.existsByReference(reference)) {
            throw new NotFoundException("Booking", reference);
        }
        return qrCodes.png(reference);
    }

    /** A booking as a ticket. Must run inside a transaction: it reads the booking's hold, event and venue. */
    TicketResponse toTicket(Booking booking) {
        return toTicket(booking, holdService.toResponse(booking.getHold()));
    }

    private static TicketResponse toTicket(Booking booking, HoldResponse hold) {
        var event = booking.getHold().getEvent();
        var venue = event.getVenue();
        return new TicketResponse(booking.getReference(), event.getId(), event.getName(), event.getStartsAt(),
                venue.getName(), venue.getType(), venue.getAddress(), venue.getCity(), venue.getStageLabel(),
                hold.seats(), booking.getTotalCents(), booking.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public BookingResponse getBooking(String reference) {
        Booking booking = bookingRepository.findByReference(reference)
                .orElseThrow(() -> new NotFoundException("Booking", reference));
        return toResponse(booking, holdService.toResponse(booking.getHold()));
    }

    private String uniqueReference() {
        for (int attempt = 0; attempt < MAX_REFERENCE_ATTEMPTS; attempt++) {
            String reference = referenceGenerator.next();
            if (!bookingRepository.existsByReference(reference)) {
                return reference;
            }
        }
        throw new IllegalStateException("Could not generate a unique booking reference");
    }

    private static BookingResponse toResponse(Booking booking, HoldResponse hold) {
        return new BookingResponse(booking.getReference(), hold.id(), hold.eventId(), booking.getTotalCents(),
                booking.getCreatedAt(), hold.seats());
    }
}
