package com.seatlock.service;

import com.seatlock.config.AccountProperties;
import com.seatlock.exception.InvalidRequestException;
import com.seatlock.model.Account;
import com.seatlock.model.PasswordResetToken;
import com.seatlock.notification.AccountEmailEvents.PasswordChanged;
import com.seatlock.notification.AccountEmailEvents.PasswordResetRequested;
import com.seatlock.repository.AccountRepository;
import com.seatlock.repository.PasswordResetTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Forgot-password flow: email a one-time link, then let its holder set a new password.
 *
 * <p>The answer to a reset request is the same whether or not the email has an account, and the
 * email is sent in the background, so the endpoint cannot be used to find out who is registered.
 */
@Service
public class PasswordResetService {

    private static final String INVALID_LINK = "This reset link is invalid, already used or expired. Ask for a new one.";

    private final AccountRepository accountRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final AccountProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(AccountRepository accountRepository, PasswordResetTokenRepository tokenRepository,
                                PasswordEncoder passwordEncoder, ApplicationEventPublisher events,
                                AccountProperties properties, Clock clock) {
        this.accountRepository = accountRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    /** Emails a reset link if the address has an account; does nothing otherwise. */
    @Transactional
    public void requestReset(String email) {
        accountRepository.findByEmail(Account.normalizeEmail(email)).ifPresent(account -> {
            Instant now = clock.instant();
            // Only the newest link works.
            tokenRepository.findUnusedByAccountId(account.getId()).forEach(old -> old.markUsed(now));
            String token = newToken();
            tokenRepository.save(new PasswordResetToken(account, hash(token), now.plus(properties.resetTokenTtl())));
            events.publishEvent(new PasswordResetRequested(account.getEmail(), account.getFullName(),
                    properties.baseUrl() + "/#reset/" + token, properties.resetTokenTtl()));
        });
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        Instant now = clock.instant();
        PasswordResetToken resetToken = tokenRepository.findByTokenHashForUpdate(hash(token))
                .filter(candidate -> candidate.isUsableAt(now))
                .orElseThrow(() -> new InvalidRequestException(INVALID_LINK));
        Account account = resetToken.getAccount();
        account.changePassword(passwordEncoder.encode(newPassword));
        resetToken.markUsed(now);
        events.publishEvent(new PasswordChanged(account.getEmail(), account.getFullName()));
    }

    /** 32 random bytes, URL-safe: long enough that guessing a live link is hopeless. */
    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is always available", ex);
        }
    }
}
