package it.fantacalciointelligence.backend.identity.enrichment.transfermarkt;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class TransfermarktDiscoveryPropertiesTest {

    @Test
    void disabledConfigurationIsSafeByDefault() {
        assertThatCode(
                new TransfermarktDiscoveryProperties()
                        ::validateEnabledConfiguration
        ).doesNotThrowAnyException();
    }

    @Test
    void enabledConfigurationRequiresQueryPlaceholder() {
        var properties =
                new TransfermarktDiscoveryProperties();

        properties.setEnabled(true);
        properties.setSearchUrlTemplate(
                "https://www.transfermarkt.com/search"
        );

        assertThatThrownBy(
                properties
                        ::validateEnabledConfiguration
        ).hasMessageContaining("{query}");
    }

    @Test
    void rejectsInvalidLimits() {
        var properties =
                new TransfermarktDiscoveryProperties();

        properties.setEnabled(true);
        properties.setMaxBytes(0);

        assertThatThrownBy(
                properties
                        ::validateEnabledConfiguration
        ).hasMessageContaining("max-bytes");

        properties.setMaxBytes(100);
        properties.setRequestTimeout(
                Duration.ZERO
        );

        assertThatThrownBy(
                properties
                        ::validateEnabledConfiguration
        ).hasMessageContaining(
                "request-timeout"
        );
    }
}