package it.fantacalciointelligence.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

class RateLimitFilterTest {

    @Test
    void enforcesThresholdSeparatesIdentitiesAndResetsAndCleansUp() throws Exception {
        MutableClock clock = new MutableClock();
        RateLimitFilter filter = new RateLimitFilter(2, Duration.ofMinutes(1), clock);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProbeController()).addFilters(filter).build();

        mvc.perform(post("/mutation").with(request -> { request.setRemoteAddr("10.0.0.1"); return request; }))
                .andExpect(status().isOk());
        mvc.perform(post("/mutation").with(request -> { request.setRemoteAddr("10.0.0.1"); return request; }))
                .andExpect(status().isOk());
        mvc.perform(post("/mutation").with(request -> { request.setRemoteAddr("10.0.0.1"); return request; }))
                .andExpect(status().isTooManyRequests());
        mvc.perform(post("/mutation").with(request -> { request.setRemoteAddr("10.0.0.2"); return request; }))
                .andExpect(status().isOk());

        assertThat(filter.trackedIdentities()).isEqualTo(2);
        clock.advance(Duration.ofMinutes(1));
        mvc.perform(post("/mutation").with(request -> { request.setRemoteAddr("10.0.0.1"); return request; }))
                .andExpect(status().isOk());
        assertThat(filter.trackedIdentities()).isEqualTo(1);

        mvc.perform(get("/public").with(request -> { request.setRemoteAddr("10.0.0.1"); return request; }))
                .andExpect(status().isOk());
    }

    @RestController
    static class ProbeController {
        @PostMapping("/mutation") void mutation() { }
        @GetMapping("/public") void publicGet() { }
    }

    static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-01-01T00:00:00Z");
        void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
