package it.fantacalciointelligence.backend.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import it.fantacalciointelligence.backend.common.ConflictException;

@SpringBootTest
class SourceIngestionRecoveryConcurrencyTest {

    @Autowired SourceIngestionService service;
    @Autowired SourceIngestionProperties properties;
    @Autowired JdbcClient jdbc;
    Long seasonId;
    SourceIngestionProperties.Feed feed;

    @BeforeEach
    void setup() {
        seasonId = jdbc.sql("SELECT id FROM seasons WHERE code='2026/2027'").query(Long.class).single();
        jdbc.sql("DELETE FROM source_ingestion_runs WHERE feed_id='recovery-feed'").update();
        properties.setAllowHttp(true);
        properties.setAllowPrivateHosts(true);
        properties.setStaleThreshold(Duration.ofSeconds(1));
        feed = new SourceIngestionProperties.Feed();
        feed.setId("recovery-feed"); feed.setType("PLAYERS"); feed.setSource("FANTACALCIO");
        feed.setSeason("2026/2027"); feed.setUrl("http://127.0.0.1/feed.csv");
    }

    @AfterEach
    void removeRuns() {
        jdbc.sql("DELETE FROM source_ingestion_runs WHERE feed_id='recovery-feed'").update();
    }

    @Test
    void recentStartedRemainsBlocking() {
        insertStarted("CURRENT_TIMESTAMP");
        assertThatThrownBy(() -> service.start(feed, seasonId)).isInstanceOf(ConflictException.class);
    }

    @Test
    void staleStartedIsFailedCompletedAndReplaced() {
        Long stale = insertStarted("CURRENT_TIMESTAMP - INTERVAL '2 seconds'");
        service.start(feed, seasonId);
        assertThat(jdbc.sql("""
                SELECT status='FAILED' AND completed_at IS NOT NULL
                       AND error_message='Ingestion interrotta recuperata automaticamente'
                FROM source_ingestion_runs WHERE id=:id
                """).param("id", stale).query(Boolean.class).single()).isTrue();
    }

    @Test
    void concurrentRecoveryLeavesAtMostOneStarted() throws Exception {
        insertStarted("CURRENT_TIMESTAMP - INTERVAL '2 seconds'");
        CountDownLatch ready = new CountDownLatch(2); CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> attempt(ready, start));
            var second = executor.submit(() -> attempt(ready, start));
            ready.await(); start.countDown(); first.get(); second.get();
        }
        assertThat(jdbc.sql("SELECT COUNT(*) FROM source_ingestion_runs WHERE feed_id='recovery-feed' AND status='STARTED'")
                .query(Integer.class).single()).isEqualTo(1);
    }

    private Object attempt(CountDownLatch ready, CountDownLatch start) {
        try { ready.countDown(); start.await(); return service.start(feed, seasonId); }
        catch (Exception exception) { return exception; }
    }

    private Long insertStarted(String expression) {
        return jdbc.sql("""
                INSERT INTO source_ingestion_runs(
                    feed_id,feed_type,source,season_id,sanitized_url,status,started_at
                ) VALUES('recovery-feed','PLAYERS','FANTACALCIO',:season,'http://127.0.0.1/feed.csv','STARTED',%s)
                RETURNING id
                """.formatted(expression)).param("season", seasonId).query(Long.class).single();
    }
}
