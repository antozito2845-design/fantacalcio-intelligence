package it.fantacalciointelligence.backend.reconciliation;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class PlayerReconciliationBatchSnapshotTest {

    @Autowired
    private PlayerReconciliationService service;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void shouldUseCompletedAtWhenSourceObservedAtIsMissing() {

        Long seasonId = seasonId();

        OffsetDateTime completedAt =
                OffsetDateTime.parse(
                        "2098-08-07T12:00:00+02:00"
                );

        long batchId =
                createLegacyBatch(
                        seasonId,
                        "FANTACALCIO",
                        OffsetDateTime.parse(
                                "2098-08-07T11:00:00+02:00"
                        ),
                        completedAt
                );

        Object snapshot =
                ReflectionTestUtils.invokeMethod(
                        service,
                        "latestBatch",
                        seasonId,
                        "FANTACALCIO"
                );

        assertThat(snapshot)
                .isNotNull();

        Long selectedBatchId =
                ReflectionTestUtils.invokeMethod(
                        snapshot,
                        "id"
                );

        OffsetDateTime observedAt =
                ReflectionTestUtils.invokeMethod(
                        snapshot,
                        "sourceObservedAt"
                );

        assertThat(selectedBatchId)
                .isEqualTo(batchId);

        assertThat(observedAt)
                .isEqualTo(completedAt);
    }

    private Long seasonId() {

        return jdbcClient.sql("""
                SELECT id
                FROM seasons
                WHERE code = '2026/2027'
                """)
                .query(Long.class)
                .single();
    }

    private long createLegacyBatch(
            Long seasonId,
            String source,
            OffsetDateTime startedAt,
            OffsetDateTime completedAt
    ) {

        return jdbcClient.sql("""
                INSERT INTO import_batches (
                    source,
                    season_id,
                    original_filename,
                    status,
                    total_rows,
                    valid_rows,
                    invalid_rows,
                    started_at,
                    completed_at,
                    source_observed_at
                )
                VALUES (
                    :source,
                    :seasonId,
                    :filename,
                    'COMPLETED',
                    0,
                    0,
                    0,
                    :startedAt,
                    :completedAt,
                    NULL
                )
                RETURNING id
                """)
                .param(
                        "source",
                        source
                )
                .param(
                        "seasonId",
                        seasonId
                )
                .param(
                        "filename",
                        "legacy-fallback-production.csv"
                )
                .param(
                        "startedAt",
                        startedAt
                )
                .param(
                        "completedAt",
                        completedAt
                )
                .query(Long.class)
                .single();
    }
}