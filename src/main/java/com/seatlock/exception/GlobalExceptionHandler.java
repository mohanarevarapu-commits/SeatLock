package com.seatlock.exception;

import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps domain exceptions to RFC 9457 problem responses. Bean validation errors are
 * handled by the {@link ResponseEntityExceptionHandler} base class.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail handleNotFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Not found", ex.getMessage());
    }

    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail handleInvalid(InvalidRequestException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", ex.getMessage());
    }

    @ExceptionHandler(SeatUnavailableException.class)
    ProblemDetail handleSeatUnavailable(SeatUnavailableException ex) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Seats unavailable", ex.getMessage());
        problem.setProperty("eventSeatIds", ex.getEventSeatIds());
        return problem;
    }

    @ExceptionHandler(HoldNotActiveException.class)
    ProblemDetail handleHoldNotActive(HoldNotActiveException ex) {
        return problem(HttpStatus.CONFLICT, "Hold not active", ex.getMessage());
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    ProblemDetail handleEmailTaken(EmailAlreadyRegisteredException ex) {
        return problem(HttpStatus.CONFLICT, "Email already registered", ex.getMessage());
    }

    /** Same answer for an unknown email and a wrong password, so sign-in does not reveal who is registered. */
    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleSignInFailed(AuthenticationException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "Sign-in failed", "Email or password is incorrect.");
    }

    @ExceptionHandler(HoldExpiredException.class)
    ProblemDetail handleHoldExpired(HoldExpiredException ex) {
        return problem(HttpStatus.GONE, "Hold expired", ex.getMessage());
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    ProblemDetail handleLockFailure(RuntimeException ex) {
        return problem(HttpStatus.CONFLICT, "Concurrent update",
                "The seats were modified by another request. Please retry.");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
