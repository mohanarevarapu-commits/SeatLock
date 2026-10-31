package com.seatlock.support;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;

/** Reads the parts of a received email (text, HTML, inline images), decoded. */
public final class Emails {

    private Emails() {
    }

    /** The decoded text of the first part of this type, e.g. "text/html", or null. */
    public static String part(Part part, String type) throws Exception {
        if (part.isMimeType(type)) {
            return (String) part.getContent();
        }
        if (part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                String found = part(multipart.getBodyPart(i), type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** The image the HTML shows as {@code cid:<contentId>}, or null. */
    public static BodyPart inlineImage(Part part, String contentId) throws Exception {
        if (part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart child = multipart.getBodyPart(i);
                String[] ids = child.getHeader("Content-ID");
                if (ids != null && ids[0].equals("<" + contentId + ">")) {
                    return child;
                }
                BodyPart found = inlineImage(child, contentId);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
