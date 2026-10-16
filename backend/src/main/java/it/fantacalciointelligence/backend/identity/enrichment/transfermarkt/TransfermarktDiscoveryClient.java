package it.fantacalciointelligence.backend.identity.enrichment.transfermarkt;

import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.springframework.stereotype.Component;

import it.fantacalciointelligence.backend.common.BadRequestException;
import it.fantacalciointelligence.backend.common.ConflictException;

@Component
public class TransfermarktDiscoveryClient {

    private final TransfermarktDiscoveryProperties properties;
    private final HttpClient client;

    private long lastRequestAt;

    public TransfermarktDiscoveryClient(
            TransfermarktDiscoveryProperties properties
    ) {
        this.properties = properties;
        this.client = HttpClient.newBuilder()
                .connectTimeout(
                        properties.getConnectTimeout()
                )
                .followRedirects(
                        HttpClient.Redirect.NEVER
                )
                .build();
    }

    public synchronized SearchPage search(
            String playerName
    ) {
        if (!properties.isEnabled()) {
            throw new ConflictException(
                    "Transfermarkt discovery disabilitata"
            );
        }

        properties.validateEnabledConfiguration();

        if (playerName == null
                || playerName.isBlank()) {
            throw new BadRequestException(
                    "Nome giocatore obbligatorio"
            );
        }

        String query =
                URLEncoder.encode(
                        playerName.trim(),
                        StandardCharsets.UTF_8
                );

        URI uri =
                validate(
                        properties
                                .getSearchUrlTemplate()
                                .replace(
                                        "{query}",
                                        query
                                )
                );

        FetchPage page =
                fetchHtml(uri);

        return new SearchPage(
                page.html(),
                page.finalUrl(),
                page.httpStatus(),
                page.payloadBytes()
        );
    }

    public synchronized ProfilePage fetchProfile(
            String profileUrl
    ) {
        if (!properties.isEnabled()) {
            throw new ConflictException(
                    "Transfermarkt discovery disabilitata"
            );
        }

        properties.validateEnabledConfiguration();

        URI uri =
                validate(profileUrl);

        FetchPage page =
                fetchHtml(uri);

        return new ProfilePage(
                page.html(),
                page.finalUrl(),
                page.httpStatus(),
                page.payloadBytes()
        );
    }

    private FetchPage fetchHtml(
            URI initialUri
    ) {
        throttle();

        URI uri = initialUri;
        int redirects = 0;

        try {
            while (true) {
                HttpRequest request =
                        HttpRequest.newBuilder(uri)
                                .timeout(
                                        properties
                                                .getRequestTimeout()
                                )
                                .header(
                                        "User-Agent",
                                        "Mozilla/5.0 "
                                                + "(Windows NT 10.0; Win64; x64) "
                                                + "AppleWebKit/537.36 "
                                                + "Chrome/151 Safari/537.36"
                                )
                                .header(
                                        "Accept",
                                        "text/html,application/xhtml+xml;q=0.9,*/*;q=0.1"
                                )
                                .GET()
                                .build();

                lastRequestAt =
                        System.currentTimeMillis();

                HttpResponse<java.io.InputStream> response =
                        client.send(
                                request,
                                HttpResponse
                                        .BodyHandlers
                                        .ofInputStream()
                        );

                int status =
                        response.statusCode();

                if (status >= 300
                        && status < 400) {

                    String location;

                    try (var body =
                            response.body()) {

                        redirects++;

                        if (redirects
                                > properties
                                        .getMaxRedirects()) {
                            throw new BadRequestException(
                                    "Troppi redirect Transfermarkt"
                            );
                        }

                        location =
                                response.headers()
                                        .firstValue(
                                                "location"
                                        )
                                        .orElseThrow(() ->
                                                new BadRequestException(
                                                        "Redirect Transfermarkt senza destinazione"
                                                )
                                        );
                    }

                    uri =
                            validate(
                                    uri.resolve(
                                            location
                                    ).toString()
                            );

                    continue;
                }

                if (status < 200
                        || status >= 300) {

                    response.body().close();

                    throw new BadRequestException(
                            "Transfermarkt ha risposto HTTP "
                                    + status
                    );
                }

                String contentType =
                        response.headers()
                                .firstValue(
                                        "content-type"
                                )
                                .orElse("")
                                .toLowerCase(
                                        Locale.ROOT
                                );

                if (!contentType.contains(
                        "text/html"
                )) {
                    response.body().close();

                    throw new BadRequestException(
                            "Transfermarkt non ha restituito HTML"
                    );
                }

                try (
                        var input =
                                response.body();
                        var output =
                                new ByteArrayOutputStream()
                ) {
                    byte[] buffer =
                            new byte[8192];

                    int total = 0;
                    int read;

                    while (
                            (read =
                                    input.read(
                                            buffer
                                    )) != -1
                    ) {
                        total += read;

                        if (total
                                > properties
                                        .getMaxBytes()) {
                            throw new BadRequestException(
                                    "HTML Transfermarkt supera il limite configurato"
                            );
                        }

                        output.write(
                                buffer,
                                0,
                                read
                        );
                    }

                    if (total == 0) {
                        throw new BadRequestException(
                                "HTML Transfermarkt vuoto"
                        );
                    }

                    return new FetchPage(
                            output.toString(
                                    StandardCharsets.UTF_8
                            ),
                            uri.toString(),
                            status,
                            total
                    );
                }
            }
        }
        catch (BadRequestException exception) {
            throw exception;
        }
        catch (Exception exception) {
            throw new BadRequestException(
                    "Richiesta Transfermarkt fallita",
                    exception
            );
        }
    }

    URI validate(String value) {
        try {
            URI uri =
                    URI.create(value);

            if (!uri.isAbsolute()
                    || uri.getHost() == null
                    || uri.getUserInfo() != null) {
                throw new BadRequestException(
                        "URL Transfermarkt non valido"
                );
            }

            if (!"https".equalsIgnoreCase(
                    uri.getScheme()
            )) {
                throw new BadRequestException(
                        "Transfermarkt discovery richiede HTTPS"
                );
            }

            String host =
                    uri.getHost()
                            .toLowerCase(
                                    Locale.ROOT
                            );

            if (!host.equals(
                    "transfermarkt.com"
            )
                    && !host.endsWith(
                            ".transfermarkt.com"
                    )) {
                throw new BadRequestException(
                        "Host Transfermarkt non consentito"
                );
            }

            for (InetAddress address :
                    InetAddress.getAllByName(
                            uri.getHost()
                    )) {

                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()) {
                    throw new BadRequestException(
                            "Host Transfermarkt privato/locale non consentito"
                    );
                }
            }

            return uri;
        }
        catch (BadRequestException exception) {
            throw exception;
        }
        catch (Exception exception) {
            throw new BadRequestException(
                    "URL Transfermarkt non valido",
                    exception
            );
        }
    }

    private void throttle() {
        long waitMillis =
                properties
                        .getMinIntervalMillis()
                        - (
                                System.currentTimeMillis()
                                        - lastRequestAt
                        );

        if (waitMillis <= 0) {
            return;
        }

        try {
            Thread.sleep(waitMillis);
        }
        catch (InterruptedException exception) {
            Thread.currentThread()
                    .interrupt();

            throw new BadRequestException(
                    "Transfermarkt discovery interrotta",
                    exception
            );
        }
    }

    public record SearchPage(
            String html,
            String finalUrl,
            int httpStatus,
            long payloadBytes
    ) {
    }

    public record ProfilePage(
            String html,
            String finalUrl,
            int httpStatus,
            long payloadBytes
    ) {
    }

    private record FetchPage(
            String html,
            String finalUrl,
            int httpStatus,
            long payloadBytes
    ) {
    }
}