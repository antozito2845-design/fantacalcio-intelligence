package it.fantacalciointelligence.backend.identity.enrichment.transfermarkt;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.transfermarkt-discovery")
public class TransfermarktDiscoveryProperties {

    private boolean enabled = false;
    private String searchUrlTemplate =
            "https://www.transfermarkt.com/schnellsuche/ergebnis/schnellsuche?query={query}";
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration requestTimeout = Duration.ofSeconds(15);
    private int maxBytes = 1_500_000;
    private int maxRedirects = 3;
    private long minIntervalMillis = 1_200L;

    public void validateEnabledConfiguration() {
        if (!enabled) {
            return;
        }
        if (searchUrlTemplate == null
                || searchUrlTemplate.isBlank()
                || !searchUrlTemplate.contains("{query}")) {
            throw new IllegalStateException(
                    "app.transfermarkt-discovery.search-url-template deve contenere {query}"
            );
        }
        if (connectTimeout == null
                || connectTimeout.isZero()
                || connectTimeout.isNegative()) {
            throw new IllegalStateException(
                    "app.transfermarkt-discovery.connect-timeout deve essere > 0"
            );
        }
        if (requestTimeout == null
                || requestTimeout.isZero()
                || requestTimeout.isNegative()) {
            throw new IllegalStateException(
                    "app.transfermarkt-discovery.request-timeout deve essere > 0"
            );
        }
        if (maxBytes <= 0) {
            throw new IllegalStateException(
                    "app.transfermarkt-discovery.max-bytes deve essere > 0"
            );
        }
        if (maxRedirects < 0) {
            throw new IllegalStateException(
                    "app.transfermarkt-discovery.max-redirects non puo essere negativo"
            );
        }
        if (minIntervalMillis < 0) {
            throw new IllegalStateException(
                    "app.transfermarkt-discovery.min-interval-millis non puo essere negativo"
            );
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSearchUrlTemplate() {
        return searchUrlTemplate;
    }

    public void setSearchUrlTemplate(String searchUrlTemplate) {
        this.searchUrlTemplate = searchUrlTemplate;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public int getMaxBytes() {
        return maxBytes;
    }

    public void setMaxBytes(int maxBytes) {
        this.maxBytes = maxBytes;
    }

    public int getMaxRedirects() {
        return maxRedirects;
    }

    public void setMaxRedirects(int maxRedirects) {
        this.maxRedirects = maxRedirects;
    }

    public long getMinIntervalMillis() {
        return minIntervalMillis;
    }

    public void setMinIntervalMillis(long minIntervalMillis) {
        this.minIntervalMillis = minIntervalMillis;
    }
}