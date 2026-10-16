package it.fantacalciointelligence.backend.ingestion;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.fantacalcio-listone")
public class FantacalcioListoneProperties {

    private boolean enabled;

    private String url =
            "https://www.fantacalcio.it/quotazioni-fantacalcio/2026-27";

    private Duration connectTimeout =
            Duration.ofSeconds(5);

    private Duration requestTimeout =
            Duration.ofSeconds(20);

    private Duration staleThreshold =
            Duration.ofHours(2);

    private int maxBytes =
            3_000_000;

    private int maxRedirects =
            3;

    public void validate() {

        if (!enabled) {
            return;
        }

        if (
                url == null
                        || url.isBlank()
        ) {
            throw new IllegalStateException(
                    "app.fantacalcio-listone.url obbligatorio"
            );
        }

        if (
                connectTimeout == null
                        || connectTimeout.isZero()
                        || connectTimeout.isNegative()
        ) {
            throw new IllegalStateException(
                    "app.fantacalcio-listone.connect-timeout deve essere > 0"
            );
        }

        if (
                requestTimeout == null
                        || requestTimeout.isZero()
                        || requestTimeout.isNegative()
        ) {
            throw new IllegalStateException(
                    "app.fantacalcio-listone.request-timeout deve essere > 0"
            );
        }

        if (
                staleThreshold == null
                        || staleThreshold.isZero()
                        || staleThreshold.isNegative()
        ) {
            throw new IllegalStateException(
                    "app.fantacalcio-listone.stale-threshold deve essere > 0"
            );
        }

        if (maxBytes <= 0) {
            throw new IllegalStateException(
                    "app.fantacalcio-listone.max-bytes deve essere > 0"
            );
        }

        if (maxRedirects < 0) {
            throw new IllegalStateException(
                    "app.fantacalcio-listone.max-redirects non puo' essere negativo"
            );
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(
            boolean enabled
    ) {
        this.enabled = enabled;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(
            String url
    ) {
        this.url = url;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(
            Duration connectTimeout
    ) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(
            Duration requestTimeout
    ) {
        this.requestTimeout = requestTimeout;
    }

    public Duration getStaleThreshold() {
        return staleThreshold;
    }

    public void setStaleThreshold(
            Duration staleThreshold
    ) {
        this.staleThreshold = staleThreshold;
    }

    public int getMaxBytes() {
        return maxBytes;
    }

    public void setMaxBytes(
            int maxBytes
    ) {
        this.maxBytes = maxBytes;
    }

    public int getMaxRedirects() {
        return maxRedirects;
    }

    public void setMaxRedirects(
            int maxRedirects
    ) {
        this.maxRedirects = maxRedirects;
    }
}