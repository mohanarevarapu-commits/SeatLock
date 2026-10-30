package com.seatlock.controller;

import com.seatlock.dto.AccountResponse;
import com.seatlock.dto.ForgotPasswordRequest;
import com.seatlock.dto.LoginRequest;
import com.seatlock.dto.RegisterRequest;
import com.seatlock.dto.ResetPasswordRequest;
import com.seatlock.security.SessionAuthenticator;
import com.seatlock.service.AccountService;
import com.seatlock.service.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Sign-in", description = "Accounts are optional; guests can still book")
@RequestMapping("/api/auth")
public class AuthController {

    private final AccountService accountService;
    private final PasswordResetService passwordResetService;
    private final SessionAuthenticator sessionAuthenticator;

    public AuthController(AccountService accountService, PasswordResetService passwordResetService,
                          SessionAuthenticator sessionAuthenticator) {
        this.accountService = accountService;
        this.passwordResetService = passwordResetService;
        this.sessionAuthenticator = sessionAuthenticator;
    }

    @Operation(summary = "Create an account and sign in; 409 if the email is already registered")
    @ApiResponse(responseCode = "201")
    @PostMapping("/register")
    public ResponseEntity<AccountResponse> register(@Valid @RequestBody RegisterRequest body,
                                                    HttpServletRequest request, HttpServletResponse response) {
        AccountResponse account = accountService.register(body);
        sessionAuthenticator.logIn(body.email(), body.password(), request, response);
        return ResponseEntity.created(URI.create("/api/account")).body(account);
    }

    @Operation(summary = "Sign in; sets the session cookie. 401 if the email or password is wrong")
    @PostMapping("/login")
    public AccountResponse login(@Valid @RequestBody LoginRequest body,
                                 HttpServletRequest request, HttpServletResponse response) {
        Long accountId = sessionAuthenticator.logIn(body.email(), body.password(), request, response);
        return accountService.getAccount(accountId);
    }

    @Operation(summary = "Sign out")
    @ApiResponse(responseCode = "204")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        sessionAuthenticator.logOut(request, response);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Email a password reset link. Always 202, whether or not the email has an account")
    @ApiResponse(responseCode = "202")
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest body) {
        passwordResetService.requestReset(body.email());
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Set a new password with the token from the reset link; 400 if the link is invalid or expired")
    @ApiResponse(responseCode = "204")
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest body) {
        passwordResetService.resetPassword(body.token(), body.password());
        return ResponseEntity.noContent().build();
    }
}
