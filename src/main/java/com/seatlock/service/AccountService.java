package com.seatlock.service;

import com.seatlock.dto.AccountBookingsResponse;
import com.seatlock.dto.AccountBookingsResponse.AccountBooking;
import com.seatlock.dto.AccountResponse;
import com.seatlock.dto.RegisterRequest;
import com.seatlock.dto.UpdateProfileRequest;
import com.seatlock.exception.EmailAlreadyRegisteredException;
import com.seatlock.exception.NotFoundException;
import com.seatlock.model.Account;
import com.seatlock.model.Booking;
import com.seatlock.model.Event;
import com.seatlock.notification.AccountEmailEvents.Registered;
import com.seatlock.repository.AccountRepository;
import com.seatlock.repository.BookingRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registration, profiles and a buyer's own bookings. */
@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final BookingRepository bookingRepository;
    private final HoldService holdService;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public AccountService(AccountRepository accountRepository, BookingRepository bookingRepository,
                          HoldService holdService, PasswordEncoder passwordEncoder,
                          ApplicationEventPublisher events, Clock clock) {
        this.accountRepository = accountRepository;
        this.bookingRepository = bookingRepository;
        this.holdService = holdService;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public AccountResponse register(RegisterRequest request) {
        String email = Account.normalizeEmail(request.email());
        if (accountRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }
        Account account;
        try {
            account = accountRepository.saveAndFlush(new Account(email, passwordEncoder.encode(request.password()),
                    request.name().strip(), blankToNull(request.phone()), clock.instant()));
        } catch (DataIntegrityViolationException ex) {
            // Two sign-ups with the same email at the same moment: the unique index lets only one in.
            throw new EmailAlreadyRegisteredException();
        }
        events.publishEvent(new Registered(account.getEmail(), account.getFullName()));
        return AccountResponse.from(account);
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(Long accountId) {
        return AccountResponse.from(findAccount(accountId));
    }

    @Transactional
    public AccountResponse updateProfile(Long accountId, UpdateProfileRequest request) {
        Account account = findAccount(accountId);
        account.updateProfile(request.name().strip(), blankToNull(request.phone()));
        return AccountResponse.from(account);
    }

    @Transactional(readOnly = true)
    public AccountBookingsResponse getBookings(Long accountId) {
        Instant now = clock.instant();
        List<AccountBooking> upcoming = new ArrayList<>();
        List<AccountBooking> past = new ArrayList<>();
        for (Booking booking : bookingRepository.findAllByAccountId(accountId)) {
            Event event = booking.getHold().getEvent();
            AccountBooking view = new AccountBooking(booking.getReference(), event.getId(), event.getName(),
                    event.getVenue().getName(), event.getVenue().getCity(), event.getStartsAt(),
                    booking.getTotalCents(), booking.getCreatedAt(), holdService.toResponse(booking.getHold()).seats());
            (event.hasStarted(now) ? past : upcoming).add(view);
        }
        Collections.reverse(past);
        return new AccountBookingsResponse(upcoming, past);
    }

    private Account findAccount(Long accountId) {
        return accountRepository.findById(accountId).orElseThrow(() -> new NotFoundException("Account", accountId));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
