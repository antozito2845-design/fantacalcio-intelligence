package it.fantacalciointelligence.backend.ingestion;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

class SourceIngestionPropertiesTest {

    @Test
    void disabledConfigurationDoesNotRequireFeeds() {
        assertThatCode(new SourceIngestionProperties()::validateEnabledConfiguration).doesNotThrowAnyException();
    }

    @Test
    void enabledConfigurationRequiresAtLeastOneEnabledFeed() {
        SourceIngestionProperties properties = enabled();
        assertThatThrownBy(properties::validateEnabledConfiguration).hasMessageContaining("almeno un feed");
    }

    @Test
    void rejectsDuplicateIdsAndMalformedFeed() {
        SourceIngestionProperties properties = enabled();
        SourceIngestionProperties.Feed first = feed("players");
        SourceIngestionProperties.Feed second = feed(" PLAYERS ");
        properties.setFeeds(List.of(first, second));
        assertThatThrownBy(properties::validateEnabledConfiguration).hasMessageContaining("duplicato");

        first.setType("UNKNOWN");
        properties.setFeeds(List.of(first));
        assertThatThrownBy(properties::validateEnabledConfiguration).hasMessageContaining("tipo feed");
    }

    @Test
    void rejectsInvalidLimitsAndTimeouts() {
        SourceIngestionProperties properties = enabled();
        properties.setFeeds(List.of(feed("players")));
        properties.setMaxBytes(0);
        assertThatThrownBy(properties::validateEnabledConfiguration).hasMessageContaining("max-bytes");
        properties.setMaxBytes(1);
        properties.setMaxRedirects(-1);
        assertThatThrownBy(properties::validateEnabledConfiguration).hasMessageContaining("max-redirects");
        properties.setMaxRedirects(0);
        properties.setConnectTimeout(Duration.ZERO);
        assertThatThrownBy(properties::validateEnabledConfiguration).hasMessageContaining("connect-timeout");
    }

    @Test
    void rejectsZeroAndNegativeRequestTimeout() {
        SourceIngestionProperties properties = validEnabled();
        properties.setRequestTimeout(Duration.ZERO);
        assertThatThrownBy(properties::validateEnabledConfiguration)
                .hasMessageContaining("request-timeout");

        properties.setRequestTimeout(Duration.ofSeconds(-1));
        assertThatThrownBy(properties::validateEnabledConfiguration)
                .hasMessageContaining("request-timeout");
    }

    @Test
    void rejectsUnsafeFilename() {
        SourceIngestionProperties properties = validEnabled();
        properties.getFeeds().getFirst().setFilename("../../feed.csv");
        assertThatThrownBy(properties::validateEnabledConfiguration)
                .hasMessageContaining("filename");
    }

    @Test
    void rejectsMalformedUrlAndUnsupportedScheme() {
        SourceIngestionProperties properties = validEnabled();
        properties.getFeeds().getFirst().setUrl("https://exa mple.test/feed.csv");
        assertThatThrownBy(properties::validateEnabledConfiguration)
                .hasMessageContaining("URL feed");

        properties.getFeeds().getFirst().setUrl("ftp://example.test/feed.csv");
        assertThatThrownBy(properties::validateEnabledConfiguration)
                .hasMessageContaining("schema URL");
    }

    @Test
    void rejectsBlankSourceSeasonAndUrl() {
        SourceIngestionProperties properties = validEnabled();
        SourceIngestionProperties.Feed feed = properties.getFeeds().getFirst();

        feed.setSource(" ");
        assertThatThrownBy(properties::validateEnabledConfiguration)
                .hasMessageContaining("source");

        feed.setSource("FANTACALCIO");
        feed.setSeason("");
        assertThatThrownBy(properties::validateEnabledConfiguration)
                .hasMessageContaining("season");

        feed.setSeason("2026/2027");
        feed.setUrl(" ");
        assertThatThrownBy(properties::validateEnabledConfiguration)
                .hasMessageContaining("url");
    }

    private SourceIngestionProperties enabled() {
        SourceIngestionProperties properties = new SourceIngestionProperties();
        properties.setEnabled(true);
        return properties;
    }

    private SourceIngestionProperties validEnabled() {
        SourceIngestionProperties properties = enabled();
        properties.setFeeds(new java.util.ArrayList<>(List.of(feed("players"))));
        return properties;
    }

    private SourceIngestionProperties.Feed feed(String id) {
        SourceIngestionProperties.Feed feed = new SourceIngestionProperties.Feed();
        feed.setId(id);
        feed.setType("PLAYERS");
        feed.setSource("FANTACALCIO");
        feed.setSeason("2026/2027");
        feed.setUrl("https://example.test/feed.csv");
        return feed;
    }
}
