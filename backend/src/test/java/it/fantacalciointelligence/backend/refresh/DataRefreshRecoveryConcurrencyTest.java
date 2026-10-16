package it.fantacalciointelligence.backend.refresh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import it.fantacalciointelligence.backend.common.ConflictException;

@SpringBootTest(properties = "app.data-refresh.stale-threshold=1s")
class DataRefreshRecoveryConcurrencyTest {

    @Autowired DataRefreshService service;
    @Autowired JdbcClient jdbc;
    Long seasonId;

    @BeforeEach
    void cleanup() {
        seasonId = jdbc.sql("SELECT id FROM seasons WHERE code='2026/2027'").query(Long.class).single();
        jdbc.sql("DELETE FROM data_refresh_runs WHERE season_id=:season").param("season", seasonId).update();
    }

    @AfterEach
    void removeRuns() {
        jdbc.sql("DELETE FROM data_refresh_runs WHERE season_id=:season").param("season", seasonId).update();
    }

    @Test
    void recentRunRemainsBlocking() {
        insertRunning("CURRENT_TIMESTAMP");
        assertThatThrownBy(() -> service.createRefreshRun(seasonId, "MANUAL"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void staleRunIsFailedCompletedAndReplaced() {
        Long staleId = insertRunning("CURRENT_TIMESTAMP - INTERVAL '2 seconds'");
        Long newId = service.createRefreshRun(seasonId, "MANUAL");
        assertThat(newId).isNotEqualTo(staleId);
        assertThat(jdbc.sql("""
                SELECT status='FAILED' AND completed_at IS NOT NULL
                       AND error_message='Esecuzione interrotta recuperata automaticamente'
                FROM data_refresh_runs WHERE id=:id
                """).param("id", staleId).query(Boolean.class).single()).isTrue();
    }

    @Test
    void concurrentRecoveryLeavesAtMostOneRunning() throws Exception {
        insertRunning("CURRENT_TIMESTAMP - INTERVAL '2 seconds'");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> attempt(ready, start));
            var second = executor.submit(() -> attempt(ready, start));
            ready.await();
            start.countDown();
            first.get();
            second.get();
        }
        assertThat(jdbc.sql("SELECT COUNT(*) FROM data_refresh_runs WHERE season_id=:season AND status='RUNNING'")
                .param("season", seasonId).query(Integer.class).single()).isEqualTo(1);
    }

    private Object attempt(CountDownLatch ready, CountDownLatch start) {
        try {
            ready.countDown(); start.await();
            return service.createRefreshRun(seasonId, "MANUAL");
        } catch (Exception exception) {
            return exception;
        }
    }

    private Long insertRunning(String timestampExpression) {
        return jdbc.sql("""
                INSERT INTO data_refresh_runs(season_id,trigger_type,status,started_at)
                VALUES(:season,'MANUAL','RUNNING',%s) RETURNING id
                """.formatted(timestampExpression)).param("season", seasonId).query(Long.class).single();
    }
}
