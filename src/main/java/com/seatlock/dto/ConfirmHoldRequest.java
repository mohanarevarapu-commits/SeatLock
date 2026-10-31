package com.seatlock.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Optional body of {@code POST /api/holds/{id}/confirm}: a guest's contact details, given on the
 * payment page. They are kept with the booking and the confirmation email goes to {@code contactEmail}.
 * Signed-in buyers' confirmations go to their account email, so these are ignored for them.
 */
public record ConfirmHoldRequest(
        @Email @Size(max = 254) String contactEmail,
        @Size(max = 20) @Pattern(regexp = AccountRules.PHONE, message = AccountRules.PHONE_MESSAGE) String contactPhone) {
}
