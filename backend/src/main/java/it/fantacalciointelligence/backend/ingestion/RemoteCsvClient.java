package it.fantacalciointelligence.backend.ingestion;

import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Locale;

import org.springframework.stereotype.Component;

import it.fantacalciointelligence.backend.common.BadRequestException;

@Component
public class RemoteCsvClient {

    private final SourceIngestionProperties config;
    private final HttpClient client;

    public RemoteCsvClient(SourceIngestionProperties config) {
        this.config = config;
        this.client = HttpClient.newBuilder()
                .connectTimeout(config.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public FetchResult fetch(String value) {
        URI uri = validate(value);
        int redirects = 0;
        try {
            while (true) {
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .timeout(config.getRequestTimeout())
                        .header("User-Agent", "Fantacalcio-Intelligence-Source-Ingestion/1.0")
                        .header("Accept", "text/csv,text/plain;q=0.9,*/*;q=0.1")
                        .GET()
                        .build();
                HttpResponse<java.io.InputStream> response = client.send(
                        request,
                        HttpResponse.BodyHandlers.ofInputStream()
                );
                int status = response.statusCode();
                if (status >= 300 && status < 400) {
                    String location;
                    try (var ignored = response.body()) {
                        if (++redirects > config.getMaxRedirects()) {
                            throw new BadRequestException("Troppi redirect remoti");
                        }
                        location = response.headers().firstValue("location")
                                .orElseThrow(() -> new BadRequestException(
                                        "Redirect remoto senza destinazione"
                                ));
                    }
                    uri = validate(uri.resolve(location).toString());
                    continue;
                }
                if (status < 200 || status >= 300) {
                    throw new BadRequestException("Fonte remota ha risposto HTTP " + status);
                }
                String contentType = response.headers().firstValue("content-type")
                        .orElse("").toLowerCase(Locale.ROOT);
                if (contentType.startsWith("text/html")) {
                    response.body().close();
                    throw new BadRequestException("La fonte remota ha restituito HTML invece di CSV");
                }
                try (var input = response.body(); var output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int total = 0;
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        total += read;
                        if (total > config.getMaxBytes()) {
                            throw new BadRequestException("Payload remoto supera il limite configurato");
                        }
                        output.write(buffer, 0, read);
                    }
                    if (total == 0) {
                        throw new BadRequestException("Payload remoto vuoto");
                    }
                    return new FetchResult(output.toByteArray(), status, uri, safeFilename(uri));
                }
            }
        } catch (BadRequestException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BadRequestException("Download remoto fallito", exception);
        }
    }

    URI validate(String value) {
        try {
            URI uri = URI.create(value);
            if (!uri.isAbsolute() || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new BadRequestException("URL feed non valido");
            }
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("https") && !(scheme.equals("http") && config.isAllowHttp())) {
                throw new BadRequestException("Schema URL feed non consentito");
            }
            if (!config.isAllowPrivateHosts()) {
                for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                    if (address.isAnyLocalAddress()
                            || address.isLoopbackAddress()
                            || address.isLinkLocalAddress()
                            || address.isSiteLocalAddress()) {
                        throw new BadRequestException("Host privato/locale non consentito");
                    }
                }
            }
            return uri;
        } catch (BadRequestException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BadRequestException("URL feed non valido", exception);
        }
    }

    public static String sanitized(URI uri) {
        try {
            return new URI(
                    uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), null, null
            ).toString();
        } catch (Exception exception) {
            return uri.getScheme() + "://" + uri.getHost();
        }
    }

    private String safeFilename(URI uri) {
        String path = uri.getPath();
        String name = path == null ? "" : path.substring(path.lastIndexOf('/') + 1);
        return name.matches("[A-Za-z0-9._-]{1,255}") ? name : "remote-feed.csv";
    }

    public record FetchResult(byte[] bytes, int httpStatus, URI finalUri, String filename) {}
}
