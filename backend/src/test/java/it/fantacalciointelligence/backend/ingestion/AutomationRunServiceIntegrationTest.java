package it.fantacalciointelligence.backend.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import it.fantacalciointelligence.backend.common.ConflictException;

@SpringBootTest(
        properties = {
                "app.data-refresh.scheduler.enabled=false",
                "app.production-automation.stale-threshold=1s"
        }
)
class AutomationRunServiceIntegrationTest {

    @Autowired
    private AutomationRunService service;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void clean() {

        jdbc.sql("""
                DELETE FROM automation_runs
                """)
                .update();
    }

    @Test
    void completedRunPersistsCompleteExecutionState() {

        long runId =
                service.begin(
                        "2026/2027"
                );

        service.stage(
                runId,
                "LISTONE"
        );

        service.stage(
                runId,
                "INGESTION"
        );

        service.stage(
                runId,
                "REFRESH"
        );

        service.complete(
                runId,
                101L,
                3,
                202L
        );

        AutomationRunResponse latest =
                service.latest(
                        "2026/2027"
                );

        assertThat(latest.id())
                .isEqualTo(runId);

        assertThat(latest.status())
                .isEqualTo("COMPLETED");

        assertThat(latest.stage())
                .isEqualTo("COMPLETED");

        assertThat(latest.listoneRunId())
                .isEqualTo(101L);

        assertThat(latest.ingestionRunCount())
                .isEqualTo(3);

        assertThat(latest.refreshRunId())
                .isEqualTo(202L);

        assertThat(latest.completedAt())
                .isNotNull();

        assertThat(latest.errorMessage())
                .isNull();
    }

    @Test
    void databasePreventsConcurrentAutomationForSameSeason() {

        long first =
                service.begin(
                        "2026/2027"
                );

        assertThatThrownBy(() ->
                service.begin(
                        "2026/2027"
                )
        )
                .isInstanceOf(
                        ConflictException.class
                );

        service.fail(
                first,
                new IllegalStateException(
                        "test cleanup"
                )
        );
    }

    @Test
    void staleRunIsFailedAndCanBeReplaced() {

        long stale =
                service.begin(
                        "2026/2027"
                );

        jdbc.sql("""
                UPDATE automation_runs
                SET last_heartbeat_at =
                    CURRENT_TIMESTAMP - INTERVAL '2 seconds'
                WHERE id = :id
                """)
                .param("id", stale)
                .update();

        long replacement =
                service.begin(
                        "2026/2027"
                );

        assertThat(replacement)
                .isNotEqualTo(stale);

        String oldStatus =
                jdbc.sql("""
                        SELECT status
                        FROM automation_runs
                        WHERE id = :id
                        """)
                        .param("id", stale)
                        .query(String.class)
                        .single();

        assertThat(oldStatus)
                .isEqualTo("FAILED");

        service.fail(
                replacement,
                new IllegalStateException(
                        "test cleanup"
                )
        );
    }

    @Test
    void failureIsPersistedAndSanitized() {

        long runId =
                service.begin(
                        "2026/2027"
                );

        service.stage(
                runId,
                "LISTONE"
        );

        service.fail(
                runId,
                new IllegalStateException(
                        "https://example.test/feed?api_key=SUPER_SECRET"
                )
        );

        AutomationRunResponse latest =
                service.latest(
                        "2026/2027"
                );

        assertThat(latest.status())
                .isEqualTo("FAILED");

        assertThat(latest.stage())
                .isEqualTo("LISTONE");

        assertThat(latest.completedAt())
                .isNotNull();

        assertThat(latest.errorMessage())
                .doesNotContain(
                        "SUPER_SECRET"
                );
    }
}