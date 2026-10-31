package com.seatlock.service;

import com.seatlock.dto.TicketCodeRequest;
import com.seatlock.dto.TicketLookupRequest;
import com.seatlock.dto.TicketResponse;
import com.seatlock.exception.InvalidTicketCodeException;
import com.seatlock.model.Account;
import com.seatlock.model.Booking;
import com.seatlock.model.TicketAccessCode;
import com.seatlock.notification.TicketCodeRequestedEvent;
import com.seatlock.repository.BookingRepository;
import com.seatlock.repository.TicketAccessCodeRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Find my tickets for guests: they give the email and phone number they booked with, get a 6-digit
 * code by email, and see their tickets once they enter it.
 *
 * <p>Asking for a code gives the same answer whether or not there are bookings for those details, and
 * does the same hashing work either way, so neither the answer nor its timing tells anyone whether an
 * email has booked. A code works once, for {@link #CODE_TTL}, and stops after
 * {@link TicketAccessCode#MAX_ATTEMPTS} wrong tries; a new code cancels the old ones, and a new code
 * is only sent {@link #RESEND_AFTER} after the last, so nobody can flood an inbox.
 */
@Service
public class TicketAccessService {

    public static final Duration CODE_TTL = Duration.ofMinutes(10);
    public static final Duration RESEND_AFTER = Duration.ofSeconds(30);

    private final BookingRepository bookingRepository;
    private final TicketAccessCodeRepository codeRepository;
    private final BookingService bookingService;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public TicketAccessService(BookingRepository bookingRepository, TicketAccessCodeRepository codeRepository,
                               BookingService bookingService, PasswordEncoder passwordEncoder,
                               ApplicationEventPublisher events, Clock clock) {
        this.bookingRepository = bookingRepository;
        this.codeRepository = codeRepository;
        this.bookingService = bookingService;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public void sendCode(TicketCodeRequest request) {
        String email = Account.normalizeEmail(request.email());
        String phone = Booking.normalizePhone(request.phone());
        String code = "%06d".formatted(random.nextInt(1_000_000));
        String codeHash = passwordEncoder.encode(code);   // before the lookup, so timing is the same either way
        if (guestBookings(email, phone).isEmpty()) {
            return;
        }
        Instant now = clock.instant();
        List<TicketAccessCode> open = codeRepository.findUnusedForUpdate(email, phone);
        if (!open.isEmpty() && open.get(0).getCreatedAt().isAfter(now.minus(RESEND_AFTER))) {
            return;   // one was sent moments ago
        }
        open.forEach(older -> older.markUsed(now));
        codeRepository.save(new TicketAccessCode(email, phone, codeHash, now, now.plus(CODE_TTL)));
        events.publishEvent(new TicketCodeRequestedEvent(email, code, CODE_TTL));
    }

    /** The guest's tickets, soonest concert first; a wrong try still counts after the exception. */
    @Transactional(noRollbackFor = InvalidTicketCodeException.class)
    public List<TicketResponse> lookUp(TicketLookupRequest request) {
        String email = Account.normalizeEmail(request.email());
        String phone = Booking.normalizePhone(request.phone());
        Instant now = clock.instant();
        TicketAccessCode code = codeRepository.findUnusedForUpdate(email, phone).stream()
                .findFirst()
                .filter(latest -> latest.isUsableAt(now))
                .orElseThrow(InvalidTicketCodeException::new);
        if (!passwordEncoder.matches(request.code().strip(), code.getCodeHash())) {
            code.recordWrongAttempt();
            throw new InvalidTicketCodeException();
        }
        code.markUsed(now);
        return guestBookings(email, phone).stream().map(bookingService::toTicket).toList();
    }

    private List<Booking> guestBookings(String email, String phone) {
        return bookingRepository.findGuestBookingsByContactEmail(email).stream()
                .filter(booking -> phone.equals(Booking.normalizePhone(booking.getContactPhone())))
                .toList();
    }
}
