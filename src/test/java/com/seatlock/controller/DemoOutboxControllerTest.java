package com.seatlock.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The outbox holds ticket codes and reset links, so only this machine may read it. */
class DemoOutboxControllerTest {

    @Test
    void onlyRequestsFromThisMachineMayReadTheOutbox() {
        assertThat(DemoOutboxController.isLoopback("127.0.0.1")).isTrue();
        assertThat(DemoOutboxController.isLoopback("0:0:0:0:0:0:0:1")).isTrue();
        assertThat(DemoOutboxController.isLoopback("::1")).isTrue();
        assertThat(DemoOutboxController.isLoopback("192.168.1.20")).isFalse();
        assertThat(DemoOutboxController.isLoopback("203.0.113.7")).isFalse();
        assertThat(DemoOutboxController.isLoopback(null)).isFalse();
    }
}
