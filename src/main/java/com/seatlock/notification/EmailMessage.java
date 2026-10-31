package com.seatlock.notification;

import java.util.List;

/**
 * One email: a plain-text body for every mail program, and optionally an HTML body with images
 * shown inside it (the HTML refers to each as {@code cid:<contentId>}).
 */
public record EmailMessage(String to, String subject, String text, String html, List<InlineImage> images) {

    public static EmailMessage plain(String to, String subject, String text) {
        return new EmailMessage(to, subject, text, null, List.of());
    }

    public record InlineImage(String contentId, byte[] data, String contentType) {
    }
}
