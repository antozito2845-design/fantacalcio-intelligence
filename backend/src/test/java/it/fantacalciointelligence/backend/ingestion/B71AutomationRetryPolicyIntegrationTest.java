package it.fantacalciointelligence.backend.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(
        properties = {
                "app.data-refresh.scheduler.enabled=false",
                "app.source-ingestion.enabled=false",
                "app.fantacalcio-listone.enabled=false",
                "app.production-automation.stale-threshold=2h"
        }
)
class B71AutomationRetryPolicyIntegrationTest {

    @Autowired
    private AutomationRunService service;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void cleanAutomationRuns() {

        jdbc.sql("""
                DELETE FROM automation_runs
                """)
                .update();
    }

    @Test
    void failedExecutionCanBeRetriedAndLeavesNoRunningResidue() {

        long first =
                service.begin(
                        "2026/2027"
                );

        service.stage(
                first,
                "REFRESH"
        );

        service.fail(
                first,
                new IllegalStateException(
                        "first execution failed"
                )
        );

        String firstStatus =
                status(first);

        assertThat(firstStatus)
                .isEqualTo("FAILED");

        long retry =
                service.begin(
                        "2026/2027"
                );

        assertThat(retry)
                .isNotEqualTo(first);

        service.stage(
                retry,
                "REFRESH"
        );

        service.complete(
                retry,
                null,
                0,
                9001L
        );

        AutomationRunResponse latest =
                service.latest(
                        "2026/2027"
                );

        assertThat(latest.id())
                .isEqualTo(retry);

        assertThat(latest.status())
                .isEqualTo("COMPLETED");

        assertThat(latest.stage())
                .isEqualTo("COMPLETED");

        assertThat(latest.refreshRunId())
                .isEqualTo(9001L);

        assertThat(latest.completedAt())
                .isNotNull();

        assertThat(runningCount())
                .isZero();

        assertThat(totalCount())
                .isEqualTo(2);
    }

    @Test
    void completedHistoryDoesNotPreventLaterExecution() {

        long first =
                service.begin(
                        "2026/2027"
                );

        service.complete(
                first,
                null,
                0,
                8001L
        );

        long second =
                service.begin(
                        "2026/2027"
                );

        assertThat(second)
                .isGreaterThan(first);

        service.complete(
                second,
                null,
                0,
                8002L
        );

        assertThat(runningCount())
                .isZero();

        assertThat(totalCount())
                .isEqualTo(2);
    }

    private String status(
            long id
    ) {

        return jdbc.sql("""
                SELECT status
                FROM automation_runs
                WHERE id = :id
                """)
                .param("id", id)
                .query(String.class)
                .single();
    }

    private int runningCount() {

        return jdbc.sql("""
                SELECT COUNT(*)
                FROM automation_runs
                WHERE status = 'RUNNING'
                """)
                .query(Integer.class)
                .single();
    }

    private int totalCount() {

        return jdbc.sql("""
                SELECT COUNT(*)
                FROM automation_runs
                """)
                .query(Integer.class)
                .single();
    }
}