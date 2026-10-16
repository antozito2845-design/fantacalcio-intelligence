package it.fantacalciointelligence.backend.common;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SecretSanitizer {

    private static final int MAX_LENGTH = 2000;
    private static final Pattern AUTHORIZATION = Pattern.compile(
            "(?i)(authorization\\s*[:=]\\s*)[^\\r\\n,;]+"
    );
    private static final Pattern JDBC = Pattern.compile(
            "(?i)jdbc:postgresql://[^\\s]+"
    );
    private static final Pattern HTTP = Pattern.compile(
            "https?://[^\\s<>\"']+",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(password|passwd|pwd|token|api[_-]?key|apikey|secret)(\\s*[:=]\\s*)([^\\s&,;]+)"
    );

    private SecretSanitizer() {
    }

    public static String sanitize(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        if (message == null || message.isBlank()) {
            message = throwable == null ? "Errore sconosciuto" : throwable.getClass().getSimpleName();
        }
        return sanitize(message);
    }

    public static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "Errore sconosciuto";
        }
        String result = AUTHORIZATION.matcher(value).replaceAll("$1[REDACTED]");
        result = JDBC.matcher(result).replaceAll("[REDACTED]");
        result = replaceHttpUrls(result);
        result = SECRET.matcher(result).replaceAll("$1$2[REDACTED]");
        return result.substring(0, Math.min(MAX_LENGTH, result.length()));
    }

    private static String replaceHttpUrls(String value) {
        Matcher matcher = HTTP.matcher(value);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(output, Matcher.quoteReplacement(sanitizeUri(matcher.group())));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private static String sanitizeUri(String raw) {
        try {
            URI uri = URI.create(raw);
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), null, null)
                    .toASCIIString();
        } catch (IllegalArgumentException | URISyntaxException exception) {
            return "[REDACTED_URL]";
        }
    }
}
