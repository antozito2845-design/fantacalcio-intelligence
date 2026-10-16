package it.fantacalciointelligence.backend.freshness;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FreshnessClockConfiguration {
    @Bean
    Clock freshnessClock() {
        return Clock.systemUTC();
    }
}
