package com.seatlock.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatlock.config.RateLimitProperties;
import com.seatlock.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitingFilterTest {

    private static final String HOLD = "/api/events/1/holds";
    private static final String CONFIRM = "/api/holds/7/confirm";

    private final MutableClock clock = new MutableClock(Instant.parse("2030-01-01T18:00:00Z"));
    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
    private final RateLimitingFilter filter = filter(true);

    @Test
    void allowsFiveRequestsPerSecondThenRejectsTheSixth() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(send("10.0.0.1", HOLD).passed()).isTrue();
        }

        Result sixth = send("10.0.0.1", HOLD);

        assertThat(sixth.passed()).isFalse();
        assertThat(sixth.response().getStatus()).isEqualTo(429);
        assertThat(sixth.response().getHeader("Retry-After")).isEqualTo("1");
        assertThat(sixth.response().getContentType()).isEqualTo("application/problem+json");
        JsonNode body = objectMapper.readTree(sixth.response().getContentAsByteArray());
        assertThat(body.get("status").asInt()).isEqualTo(429);
        assertThat(body.get("title").asText()).isEqualTo("Too many requests");
        assertThat(body.get("detail").asText())
                .isEqualTo("Too many checkout requests from your address. The limit is 5 requests per second; "
                        + "retry after 1 second.");
        assertThat(body.get("instance").asText()).isEqualTo(HOLD);
    }

    @Test
    void refillsAtFiveTokensPerSecond() throws Exception {
        drain("10.0.0.1");

        clock.advance(Duration.ofMillis(199));
        assertThat(send("10.0.0.1", HOLD).passed()).isFalse();

        clock.advance(Duration.ofMillis(1));
        assertThat(send("10.0.0.1", HOLD).passed()).isTrue();
        assertThat(send("10.0.0.1", HOLD).passed()).isFalse();

        clock.advance(Duration.ofSeconds(10));
        for (int i = 0; i < 5; i++) {
            assertThat(send("10.0.0.1", HOLD).passed()).isTrue();
        }
        assertThat(send("10.0.0.1", HOLD).passed()).as("burst is capped at 5").isFalse();
    }

    @Test
    void eachIpHasItsOwnLimit() throws Exception {
        drain("10.0.0.1");

        assertThat(send("10.0.0.1", HOLD).passed()).isFalse();
        assertThat(send("10.0.0.2", HOLD).passed()).isTrue();
    }

    @Test
    void holdAndConfirmShareOneLimit() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(send("10.0.0.1", HOLD).passed()).isTrue();
        }
        assertThat(send("10.0.0.1", CONFIRM).passed()).isTrue();
        assertThat(send("10.0.0.1", CONFIRM).passed()).isTrue();

        assertThat(send("10.0.0.1", CONFIRM).passed()).isFalse();
    }

    @Test
    void otherEndpointsAreNotLimited() throws Exception {
        for (int i = 0; i < 50; i++) {
            assertThat(send("10.0.0.1", "/api/events/1/seat-map").passed()).isTrue();
        }
    }

    @Test
    void disabledFilterLetsEverythingThrough() throws Exception {
        RateLimitingFilter disabled = filter(false);
        for (int i = 0; i < 50; i++) {
            assertThat(send(disabled, "10.0.0.1", HOLD).passed()).isTrue();
        }
    }

    private RateLimitingFilter filter(boolean enabled) {
        RateLimitProperties properties = new RateLimitProperties(enabled, 5,
                List.of("/api/events/*/holds", "/api/holds/*/confirm"));
        return new RateLimitingFilter(properties, clock, objectMapper);
    }

    private void drain(String ip) throws Exception {
        for (int i = 0; i < 5; i++) {
            send(ip, HOLD);
        }
    }

    private Result send(String ip, String path) throws Exception {
        return send(filter, ip, path);
    }

    private static Result send(RateLimitingFilter filter, String ip, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return new Result(chain.getRequest() != null, response);
    }

    private record Result(boolean passed, MockHttpServletResponse response) {
    }
}
