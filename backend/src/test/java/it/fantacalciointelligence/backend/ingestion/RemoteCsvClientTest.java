package it.fantacalciointelligence.backend.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import it.fantacalciointelligence.backend.common.BadRequestException;

class RemoteCsvClientTest {

    private HttpServer server;
    private SourceIngestionProperties properties;
    private String baseUrl;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        properties = new SourceIngestionProperties();
        properties.setAllowHttp(true);
        properties.setAllowPrivateHosts(true);
        properties.setMaxBytes(64);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void downloadsValidCsv() {
        route("/ok", 200, "a,b\n1,2\n");
        RemoteCsvClient.FetchResult result = new RemoteCsvClient(properties)
                .fetch(baseUrl + "/ok");
        assertThat(new String(result.bytes(), StandardCharsets.UTF_8)).contains("1,2");
    }

    @Test
    void rejectsNon2xx() {
        route("/bad", 503, "no");
        assertThatThrownBy(() -> new RemoteCsvClient(properties).fetch(baseUrl + "/bad"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("503");
    }

    @Test
    void rejectsEmpty() {
        route("/empty", 200, "");
        assertThatThrownBy(() -> new RemoteCsvClient(properties).fetch(baseUrl + "/empty"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("vuoto");
    }

    @Test
    void rejectsOversize() {
        route("/large", 200, "x".repeat(65));
        assertThatThrownBy(() -> new RemoteCsvClient(properties).fetch(baseUrl + "/large"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("limite");
    }

    @Test
    void rejectsObviousHtmlResponse() {
        server.createContext("/html", exchange -> {
            byte[] body = "<html>login</html>".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        assertThatThrownBy(() -> new RemoteCsvClient(properties).fetch(baseUrl + "/html"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("HTML");
    }

    @Test
    void rejectsInvalidSchemeAndPrivateHostByDefault() {
        assertThatThrownBy(() -> new RemoteCsvClient(properties).fetch("file:///tmp/a"))
                .isInstanceOf(BadRequestException.class);

        properties.setAllowPrivateHosts(false);
        assertThatThrownBy(() -> new RemoteCsvClient(properties).fetch(baseUrl + "/ok"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("privato");
    }

    @Test
    void redirectTargetIsRevalidated() {
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "file:///tmp/x");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        assertThatThrownBy(() -> new RemoteCsvClient(properties).fetch(baseUrl + "/redirect"))
                .isInstanceOf(BadRequestException.class);
    }

    private void route(String path, int status, String content) {
        server.createContext(path, exchange -> {
            byte[] body = content.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
    }
}
