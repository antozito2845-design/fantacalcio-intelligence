package it.fantacalciointelligence.backend.ingestion;

import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.springframework.stereotype.Component;

import it.fantacalciointelligence.backend.common.BadRequestException;

@Component
public class FantacalcioListoneClient {

    private final FantacalcioListoneProperties properties;
    private final HttpClient client;

    public FantacalcioListoneClient(FantacalcioListoneProperties properties) {
        this.properties = properties;
        this.client = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public FetchResult fetch() {
        properties.validate();

        URI uri = validate(properties.getUrl());
        int redirects = 0;

        try {
            while (true) {
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .timeout(properties.getRequestTimeout())
                        .header(
                                "User-Agent",
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                                        + "AppleWebKit/537.36 Chrome/151 Safari/537.36"
                        )
                        .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.1")
                        .GET()
                        .build();

                HttpResponse<java.io.InputStream> response = client.send(
                        request,
                        HttpResponse.BodyHandlers.ofInputStream()
                );

                int status = response.statusCode();

                if (status >= 300 && status < 400) {
                    String location;
                    try (var body = response.body()) {
                        if (++redirects > properties.getMaxRedirects()) {
                            throw new BadRequestException("Troppi redirect Fantacalcio");
                        }
                        location = response.headers()
                                .firstValue("location")
                                .orElseThrow(() -> new BadRequestException(
                                        "Redirect Fantacalcio senza destinazione"
                                ));
                    }
                    uri = validate(uri.resolve(location).toString());
                    continue;
                }

                if (status < 200 || status >= 300) {
                    response.body().close();
                    throw new BadRequestException(
                            "Fantacalcio listone ha risposto HTTP " + status
                    );
                }

                String contentType = response.headers()
                        .firstValue("content-type")
                        .orElse("")
                        .toLowerCase(Locale.ROOT);

                if (!contentType.contains("text/html")) {
                    response.body().close();
                    throw new BadRequestException(
                            "Fantacalcio listone non ha restituito HTML"
                    );
                }

                try (
                        var input = response.body();
                        var output = new ByteArrayOutputStream()
                ) {
                    byte[] buffer = new byte[8192];
                    int total = 0;
                    int read;

                    while ((read = input.read(buffer)) != -1) {
                        total += read;
                        if (total > properties.getMaxBytes()) {
                            throw new BadRequestException(
                                    "HTML Fantacalcio supera il limite configurato"
                            );
                        }
                        output.write(buffer, 0, read);
                    }

                    if (total == 0) {
                        throw new BadRequestException("HTML Fantacalcio vuoto");
                    }

                    return new FetchResult(
                            output.toString(StandardCharsets.UTF_8),
                            status,
                            uri,
                            total
                    );
                }
            }
        } catch (BadRequestException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BadRequestException(
                    "Download listone Fantacalcio fallito",
                    exception
            );
        }
    }

    URI validate(String value) {
        try {
            URI uri = URI.create(value);

            if (!uri.isAbsolute()
                    || uri.getHost() == null
                    || uri.getUserInfo() != null) {
                throw new BadRequestException("URL listone Fantacalcio non valido");
            }

            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                throw new BadRequestException(
                        "Il listone Fantacalcio richiede HTTPS"
                );
            }

            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (!host.equals("fantacalcio.it")
                    && !host.endsWith(".fantacalcio.it")) {
                throw new BadRequestException(
                        "Host listone Fantacalcio non consentito"
                );
            }

            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()) {
                    throw new BadRequestException(
                            "Host privato/locale non consentito"
                    );
                }
            }

            return uri;
        } catch (BadRequestException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BadRequestException(
                    "URL listone Fantacalcio non valido",
                    exception
            );
        }
    }

    public record FetchResult(
            String html,
            int httpStatus,
            URI finalUri,
            long payloadBytes
    ) {
    }
}