package it.fantacalciointelligence.backend.identity.enrichment.transfermarkt;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class IdentityAutomationPropertiesTest {

    @Test
    void disabledIsSafeByDefault() {

        assertThatCode(
                new IdentityAutomationProperties()
                        ::validateEnabledConfiguration
        )
                .doesNotThrowAnyException();
    }

    @Test
    void enabledRejectsInvalidBatchLimit() {

        IdentityAutomationProperties properties =
                new IdentityAutomationProperties();

        properties.setEnabled(true);
        properties.setBatchLimit(0);

        assertThatThrownBy(
                properties
                        ::validateEnabledConfiguration
        )
                .hasMessageContaining(
                        "batch-limit"
                );
    }

    @Test
    void enabledRejectsInvalidRetryDurations() {

        IdentityAutomationProperties properties =
                new IdentityAutomationProperties();

        properties.setEnabled(true);

        properties.setFailedRetry(
                Duration.ZERO
        );

        assertThatThrownBy(
                properties
                        ::validateEnabledConfiguration
        )
                .hasMessageContaining(
                        "failed-retry"
                );
    }

    @Test
    void validEnabledConfigurationPasses() {

        IdentityAutomationProperties properties =
                new IdentityAutomationProperties();

        properties.setEnabled(true);
        properties.setBatchLimit(10);

        assertThatCode(
                properties
                        ::validateEnabledConfiguration
        )
                .doesNotThrowAnyException();
    }
}