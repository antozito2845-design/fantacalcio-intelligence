package it.fantacalciointelligence.backend.ingestion;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.source-ingestion")
public class SourceIngestionProperties implements InitializingBean {

    private boolean enabled;
    private boolean allowHttp;
    private boolean allowPrivateHosts;
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration requestTimeout = Duration.ofSeconds(20);
    private Duration staleThreshold = Duration.ofHours(2);
    private int maxBytes = 5_000_000;
    private int maxRedirects = 3;
    private List<Feed> feeds = new ArrayList<>();

    @Override
    public void afterPropertiesSet() {
        validateEnabledConfiguration();
    }

    public void validateEnabledConfiguration() {
        if (staleThreshold == null || staleThreshold.isZero() || staleThreshold.isNegative()) {
            throw invalid("stale-threshold deve essere maggiore di zero");
        }
        if (!enabled) {
            return;
        }
        if (maxBytes <= 0) {
            throw invalid("max-bytes deve essere maggiore di zero");
        }
        if (maxRedirects < 0) {
            throw invalid("max-redirects non può essere negativo");
        }
        if (connectTimeout == null || connectTimeout.isZero() || connectTimeout.isNegative()) {
            throw invalid("connect-timeout deve essere maggiore di zero");
        }
        if (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw invalid("request-timeout deve essere maggiore di zero");
        }

        List<Feed> enabledFeeds = feeds.stream().filter(Feed::isEnabled).toList();
        if (enabledFeeds.isEmpty()) {
            throw invalid("è richiesto almeno un feed abilitato");
        }

        Set<String> ids = new HashSet<>();
        for (Feed feed : enabledFeeds) {
            requireText(feed.id, "id");
            String normalizedId = feed.id.trim().toLowerCase(Locale.ROOT);
            if (!ids.add(normalizedId)) {
                throw invalid("id feed duplicato: " + feed.id.trim());
            }
            requireText(feed.type, "type");
            try {
                SourceFeedType.valueOf(feed.type.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw invalid("tipo feed non valido: " + feed.type);
            }
            requireText(feed.source, "source");
            requireText(feed.season, "season");
            requireText(feed.url, "url");
            validateUrlSyntax(feed.url);
            if (feed.filename != null
                    && (feed.filename.isBlank()
                    || !feed.filename.matches("[A-Za-z0-9._-]{1,255}"))) {
                throw invalid("filename feed non sicuro");
            }
        }
    }

    private void validateUrlSyntax(String value) {
        try {
            URI uri = URI.create(value);
            if (!uri.isAbsolute() || uri.getHost() == null || uri.getUserInfo() != null) {
                throw invalid("URL feed non valido");
            }
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("https") && !(scheme.equals("http") && allowHttp)) {
                throw invalid("schema URL feed non consentito");
            }
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null
                    && exception.getMessage().startsWith("Configurazione source ingestion non valida:")) {
                throw exception;
            }
            throw invalid("URL feed non valido");
        }
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw invalid("campo feed " + field + " mancante");
        }
    }

    private IllegalStateException invalid(String detail) {
        return new IllegalStateException("Configurazione source ingestion non valida: " + detail);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAllowHttp() {
        return allowHttp;
    }

    public void setAllowHttp(boolean allowHttp) {
        this.allowHttp = allowHttp;
    }

    public boolean isAllowPrivateHosts() {
        return allowPrivateHosts;
    }

    public void setAllowPrivateHosts(boolean allowPrivateHosts) {
        this.allowPrivateHosts = allowPrivateHosts;
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

    public Duration getStaleThreshold() {
        return staleThreshold;
    }

    public void setStaleThreshold(Duration staleThreshold) {
        this.staleThreshold = staleThreshold;
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

    public List<Feed> getFeeds() {
        return feeds;
    }

    public void setFeeds(List<Feed> feeds) {
        this.feeds = feeds == null ? new ArrayList<>() : feeds;
    }

    public static class Feed {
        private String id;
        private String type;
        private String source;
        private String season;
        private String url;
        private String filename;
        private boolean enabled = true;
        private boolean required = true;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getSeason() {
            return season;
        }

        public void setSeason(String season) {
            this.season = season;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getFilename() {
            return filename;
        }

        public void setFilename(String filename) {
            this.filename = filename;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }
    }
}
