package com.seatlock.controller;

import com.seatlock.exception.NotFoundException;
import com.seatlock.notification.DemoOutbox;
import com.seatlock.notification.DemoOutbox.StoredEmail;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The demo outbox (emails kept instead of sent when no mail server is set up), for the page at
 * {@code /outbox.html}. It holds ticket codes and reset links, so it only answers requests from the
 * machine the app runs on, and it only exists when {@code seatlock.mail.demo-outbox} is on.
 */
@Hidden
@RestController
@RequestMapping("/api/demo/outbox")
@ConditionalOnProperty(name = "seatlock.mail.demo-outbox", havingValue = "true")
public class DemoOutboxController {

    private final DemoOutbox outbox;

    public DemoOutboxController(DemoOutbox outbox) {
        this.outbox = outbox;
    }

    @GetMapping
    public List<StoredEmail> list(HttpServletRequest request) {
        if (!isLoopback(request.getRemoteAddr())) {
            throw new NotFoundException("Outbox", "demo");
        }
        return outbox.list();
    }

    static boolean isLoopback(String address) {
        try {
            return address != null && InetAddress.getByName(address).isLoopbackAddress();
        } catch (UnknownHostException ex) {
            return false;
        }
    }
}
