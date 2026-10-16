package it.fantacalciointelligence.backend.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import it.fantacalciointelligence.backend.common.BadRequestException;
import it.fantacalciointelligence.backend.fixture.importer.FixtureImportResponse;
import it.fantacalciointelligence.backend.fixture.importer.FixtureImportService;
import it.fantacalciointelligence.backend.importer.CsvImportPayload;
import it.fantacalciointelligence.backend.importer.PlayerImportResponse;
import it.fantacalciointelligence.backend.importer.PlayerImportService;
import it.fantacalciointelligence.backend.refresh.DataRefreshService;
import it.fantacalciointelligence.backend.statistics.importer.PlayerStatisticsImportResponse;
import it.fantacalciointelligence.backend.statistics.importer.PlayerStatisticsImportService;

@SpringBootTest
@Transactional
class SourceIngestionServiceIntegrationTest {
    private static HttpServer server;
    private static volatile byte[] body = "a,b\n1,2\n".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private SourceIngestionService service;
    @Autowired
    private SourceIngestionProperties properties;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private AutomatedDataUpdateService automatedUpdate;
    @MockitoBean
    private PlayerImportService players;
    @MockitoBean
    private PlayerStatisticsImportService statistics;
    @MockitoBean
    private FixtureImportService fixtures;
    @MockitoBean
    private DataRefreshService refresh;

    @BeforeAll
    static void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/feed.csv", exchange -> {
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterAll
    static void stop() {
        server.stop(0);
    }

    @BeforeEach
    void config() {
        body = "a,b\n1,2\n".getBytes(StandardCharsets.UTF_8);
        reset(players, statistics, fixtures);
        properties.setEnabled(true);
        properties.setAllowHttp(true);
        properties.setAllowPrivateHosts(true);
        feed("feed", "PLAYERS", "FANTACALCIO");
        when(players.importPayload(any(), any(), any(CsvImportPayload.class)))
                .thenReturn(defaultPlayerResponse());
    }
    @Test void dispatchesAllExplicitFeedTypes(){assertThat(service.ingestOne("feed").status()).isEqualTo("COMPLETED");
        verify(players).importPayload(eq("FANTACALCIO"),eq("2026/2027"),any(CsvImportPayload.class));
        feed("stats","STATISTICS","FBREF");when(statistics.importPayload(any(),any(),any(CsvImportPayload.class)))
            .thenReturn(new PlayerStatisticsImportResponse(9002L,"FBREF","2026/2027","x",1,1,0,1,"COMPLETED",null));
        assertThat(service.ingestOne("stats").status()).isEqualTo("COMPLETED");
        feed("fixtures","FIXTURES","LEGA_SERIE_A");when(fixtures.importPayload(any(),any(),any(CsvImportPayload.class)))
            .thenReturn(new FixtureImportResponse(9003L,"LEGA_SERIE_A","2026/2027","x",1,1,0,1,"COMPLETED",null));
        assertThat(service.ingestOne("fixtures").status()).isEqualTo("COMPLETED");}
    @Test void sameChecksumSkipsAndChangedPayloadImports(){assertThat(service.ingestOne("feed").status()).isEqualTo("COMPLETED");
        assertThat(service.ingestOne("feed").status()).isEqualTo("SKIPPED_UNCHANGED");verify(players,times(1)).importPayload(any(),any(),any(CsvImportPayload.class));
        body="a,b\n3,4\n".getBytes(StandardCharsets.UTF_8);assertThat(service.ingestOne("feed").status()).isEqualTo("COMPLETED");
        verify(players,times(2)).importPayload(any(),any(),any(CsvImportPayload.class));}
    @Test void checksumRestorationImportsAgainAfterDifferentEffectivePayload(){
        assertThat(service.ingestOne("feed").status()).isEqualTo("COMPLETED");
        byte[] payloadA=body;
        body="a,b\n3,4\n".getBytes(StandardCharsets.UTF_8);
        assertThat(service.ingestOne("feed").status()).isEqualTo("COMPLETED");
        body=payloadA;
        assertThat(service.ingestOne("feed").status()).isEqualTo("COMPLETED");
        verify(players,times(3)).importPayload(any(),any(),any(CsvImportPayload.class));
    }
    @Test void failedRunDoesNotReplaceLatestEffectiveChecksum(){
        assertThat(service.ingestOne("feed").status()).isEqualTo("COMPLETED");
        when(players.importPayload(any(),any(),any(CsvImportPayload.class)))
                .thenThrow(new IllegalStateException("import failed"));
        body="a,b\n3,4\n".getBytes(StandardCharsets.UTF_8);
        assertThat(service.ingestOne("feed").status()).isEqualTo("FAILED");
        reset(players);
        when(players.importPayload(any(),any(),any(CsvImportPayload.class)))
                .thenReturn(new PlayerImportResponse(9001L,"FANTACALCIO","2026/2027","x",1,1,0,"COMPLETED",null));
        body="a,b\n1,2\n".getBytes(StandardCharsets.UTF_8);
        assertThat(service.ingestOne("feed").status()).isEqualTo("SKIPPED_UNCHANGED");
    }
    @Test void failedRestoredPayloadDoesNotOverrideDifferentEffectiveChecksum(){
        byte[] payloadA=body;
        assertThat(service.ingestOne("feed").status()).isEqualTo("COMPLETED");
        body="a,b\n3,4\n".getBytes(StandardCharsets.UTF_8);
        assertThat(service.ingestOne("feed").status()).isEqualTo("COMPLETED");
        when(players.importPayload(any(),any(),any(CsvImportPayload.class)))
                .thenThrow(new IllegalStateException("https://example.test/feed.csv?api_key=SUPER_SECRET"));
        body=payloadA;
        assertThat(service.ingestOne("feed").status()).isEqualTo("FAILED");
        assertThat(service.latest("2026/2027").getFirst().errorMessage()).doesNotContain("SUPER_SECRET");
        reset(players);
        when(players.importPayload(any(),any(),any(CsvImportPayload.class)))
                .thenReturn(new PlayerImportResponse(9001L,"FANTACALCIO","2026/2027","x",1,1,0,"COMPLETED",null));
        assertThat(service.ingestOne("feed").status()).isEqualTo("COMPLETED");
    }
    @Test void enabledIngestionWithoutFeedForSeasonFailsClearly(){
        assertThatThrownBy(()->service.ingestAll("2099/2100"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("Nessun feed");
    }
    @Test void orchestrationProcessesRemoteIngestionBeforeRefreshAndAllowsUnchangedPayload(){
        automatedUpdate.run("2026/2027");
        automatedUpdate.run("2026/2027");
        InOrder order=inOrder(players,refresh);
        order.verify(players).importPayload(eq("FANTACALCIO"),eq("2026/2027"),any(CsvImportPayload.class));
        order.verify(refresh,times(2)).runScheduled("2026/2027");
        verify(players,times(1)).importPayload(any(),any(),any(CsvImportPayload.class));
        assertThat(jdbc.sql("SELECT COUNT(*) FROM source_ingestion_runs WHERE feed_id='feed'")
                .query(Long.class).single()).isEqualTo(2L);
    }
    @Test void failedFetchIsAuditedWithoutReplacingSuccess(){assertThat(service.ingestOne("feed").status()).isEqualTo("COMPLETED");
        properties.getFeeds().get(0).setUrl("http://127.0.0.1:"+server.getAddress().getPort()+"/missing");
        assertThat(service.ingestOne("feed").status()).isEqualTo("FAILED");
        assertThat(service.latest("2026/2027")).singleElement().satisfies(latest->{
            assertThat(latest.status()).isEqualTo("FAILED");assertThat(latest.required()).isTrue();});}
    @Test void successfulRemoteImportMarksBatchAsObserved(){long batch=jdbc.sql("""
            INSERT INTO import_batches(source,season_id,original_filename,status)
            SELECT 'FANTACALCIO',id,'remote.csv','COMPLETED' FROM seasons WHERE code='2026/2027' RETURNING id
            """).query(Long.class).single();
        when(players.importPayload(any(),any(),any(CsvImportPayload.class)))
                .thenReturn(new PlayerImportResponse(batch,"FANTACALCIO","2026/2027","x",1,1,0,"COMPLETED",null));
        assertThat(service.ingestOne("feed").status()).isEqualTo("COMPLETED");
        assertThat(jdbc.sql("SELECT source_observed_at IS NOT NULL FROM import_batches WHERE id=:id")
                .param("id",batch).query(Boolean.class).single()).isTrue();}
    @Test void databasePreventsConcurrentStartedRunForSameFeedAndSeason(){String insert="""
            INSERT INTO source_ingestion_runs(feed_id,feed_type,source,season_id,sanitized_url,status)
            SELECT 'locked-feed','PLAYERS','FANTACALCIO',id,'https://example.test/feed.csv','STARTED'
            FROM seasons WHERE code='2026/2027'
            """;
        jdbc.sql(insert).update();
        assertThatThrownBy(()->jdbc.sql(insert).update()).isInstanceOf(DataIntegrityViolationException.class);}
    private PlayerImportResponse defaultPlayerResponse() {
        return new PlayerImportResponse(
                9001L,
                "FANTACALCIO",
                "2026/2027",
                "x",
                1,
                1,
                0,
                "COMPLETED",
                null
        );
    }

    private void feed(String id, String type, String source) {
        SourceIngestionProperties.Feed feed = new SourceIngestionProperties.Feed();
        feed.setId(id);
        feed.setType(type);
        feed.setSource(source);
        feed.setSeason("2026/2027");
        feed.setUrl(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/feed.csv"
        );
        feed.setRequired(true);
        feed.setEnabled(true);
        properties.setFeeds(new java.util.ArrayList<>(List.of(feed)));
    }
}
