package it.fantacalciointelligence.backend.refresh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import it.fantacalciointelligence.backend.common.ConflictException;

@SpringBootTest
@Transactional
class DataRefreshServiceTest {

    @Autowired
    private DataRefreshService service;

    @Autowired
    private JdbcClient jdbcClient;

    @MockitoBean
    private RefreshFaultInjector faultInjector;

    @Test
    void shouldRunCompleteDataRefreshPipeline() {
        prepareCompletedImports();

        DataRefreshResponse response =
                service.runManual(
                        "2026/2027"
                );

        assertThat(response.id())
                .isNotNull();

        assertThat(response.season())
                .isEqualTo("2026/2027");

        assertThat(response.triggerType())
                .isEqualTo("MANUAL");

        assertThat(response.status())
                .isEqualTo("COMPLETED");

        assertThat(
                response.sourceFreshnessStatus()
        ).isIn(
                "READY",
                "WARNING"
        );

        assertThat(
                response.sourceFreshnessCheckedAt()
        ).isNotNull();

        assertThat(
                response.reconciliationMatchedPlayers()
        )
                .isEqualTo(1);

        assertThat(
                response.generatedEvaluations()
        )
                .isEqualTo(1);

        assertThat(
                response.generatedClubRatings()
        )
                .isEqualTo(1);

        assertThat(response.completedAt())
                .isNotNull();

        assertThat(response.errorMessage())
                .isNull();

        DataRefreshResponse latest =
                service.findLatest(
                        "2026/2027"
                );

        assertThat(latest.id())
                .isEqualTo(response.id());
    }

    @Test
    void shouldRejectRefreshWhenAnotherRunIsActive() {
        Long seasonId =
                findSeasonId();

        jdbcClient.sql("""
                INSERT INTO data_refresh_runs (
                    season_id,
                    trigger_type,
                    status
                )
                VALUES (
                    :seasonId,
                    'MANUAL',
                    'RUNNING'
                )
                """)
                .param("seasonId", seasonId)
                .update();

        assertThatThrownBy(() ->
                service.runManual(
                        "2026/2027"
                )
        )
                .isInstanceOf(
                        ConflictException.class
                )
                .hasMessage(
                        "Un aggiornamento dati è già in esecuzione"
                );
    }

    @Test
    void shouldBlockRefreshWhenRequiredSourceIsStale() {
        prepareCompletedImports();

        jdbcClient.sql("""
                UPDATE import_batches batch
                SET
                    source_observed_at =
                        CURRENT_TIMESTAMP
                        - INTERVAL '10 days',

                    completed_at =
                        CURRENT_TIMESTAMP
                        - INTERVAL '10 days'

                FROM seasons season

                WHERE season.id =
                        batch.season_id

                  AND season.code =
                        '2026/2027'

                  AND batch.source =
                        'FANTACALCIO'

                  AND batch.status =
                        'COMPLETED'

                  AND LOWER(batch.original_filename)
                        NOT LIKE '%test%'
                """)
                .update();

        assertThatThrownBy(() ->
                service.runManual(
                        "2026/2027"
                )
        )
                .isInstanceOf(
                        ConflictException.class
                )
                .hasMessageContaining(
                        "Dati sorgente non abbastanza aggiornati"
                );

        DataRefreshResponse latest =
                service.findLatest(
                        "2026/2027"
                );

        assertThat(
                latest.status()
        ).isEqualTo("FAILED");

        assertThat(
                latest.sourceFreshnessStatus()
        ).isEqualTo("BLOCKED");

        assertThat(
                latest.sourceFreshnessCheckedAt()
        ).isNotNull();
    }

    @Test
    void faultAtEveryCheckpointMarksFailedSanitizesAndAllowsRetry() {
        for (String checkpoint : java.util.List.of(
                "AFTER_TRANSFER_DETECTION",
                "AFTER_RECONCILIATION",
                "AFTER_REVIEW_SYNC",
                "AFTER_EVALUATIONS",
                "AFTER_CLUB_STRENGTH",
                "BEFORE_COMPLETE"
        )) {
            prepareCompletedImports();
            doThrow(new IllegalStateException("password=NEVER_STORE_ME at " + checkpoint))
                    .when(faultInjector).checkpoint(checkpoint);

            assertThatThrownBy(() -> service.runManual("2026/2027"))
                    .isInstanceOf(IllegalStateException.class);
            DataRefreshResponse failed = service.findLatest("2026/2027");
            assertThat(failed.status()).isEqualTo("FAILED");
            assertThat(failed.completedAt()).isNotNull();
            assertThat(failed.errorMessage()).contains("[REDACTED]").doesNotContain("NEVER_STORE_ME");

            reset(faultInjector);
            assertThat(service.runManual("2026/2027").status()).isEqualTo("COMPLETED");
        }
    }

    private void prepareCompletedImports() {
        jdbcClient.sql("""
                DELETE FROM season_clubs membership
                USING seasons season, clubs club
                WHERE membership.season_id=season.id AND membership.club_id=club.id
                  AND season.code='2026/2027' AND club.name<>'Inter'
                """).update();

        Long fantacalcioBatchId =
                createBatch(
                        "FANTACALCIO",
                        "refresh-fantacalcio.csv",
                        1
                );

        Long gazzettaBatchId =
                createBatch(
                        "GAZZETTA",
                        "refresh-gazzetta.csv",
                        1
                );

        Long fantamarktBatchId =
                createBatch(
                        "FANTAMARKT",
                        "refresh-fantamarkt.csv",
                        1
                );

        insertPlayerRow(fantacalcioBatchId, 1, "REFRESH-FC-001",
                "Refresh Pipeline Test", "Inter", "A", 25, 110);
        insertPlayerRow(gazzettaBatchId, 1, "REFRESH-GA-001",
                "Refresh Pipeline Test", "Inter", "A", 24, 105);
        insertPlayerRow(fantamarktBatchId, 1, "REFRESH-FM-001",
                "Refresh Pipeline Test", "Inter", "A", 26, 115);
    }

    private Long createBatch(
            String source,
            String filename,
            int rows
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
                    completed_at,
                    source_observed_at
                )

                SELECT
                    :source,
                    season.id,
                    :filename,
                    'COMPLETED',
                    :rows,
                    :rows,
                    0,
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP

                FROM seasons season
                WHERE season.code = '2026/2027'

                RETURNING id
                """)
                .param("source", source)
                .param("filename", filename)
                .param("rows", rows)
                .query(Long.class)
                .single();
    }

    private void insertPlayerRow(
            Long batchId,
            int rowNumber,
            String externalId,
            String name,
            String club,
            String role,
            int quotation,
            int fantasyMarketValue
    ) {
        jdbcClient.sql("""
                INSERT INTO player_import_rows (
                    batch_id,
                    row_number,
                    external_id,
                    external_name,
                    normalized_name,
                    club_name,
                    normalized_club_name,
                    fantasy_role,
                    quotation,
                    fantasy_market_value,
                    valid,
                    raw_data
                )
                VALUES (
                    :batchId,
                    :rowNumber,
                    :externalId,
                    :name,
                    'refresh pipeline test',
                    :club,
                    LOWER(:club),
                    :role,
                    :quotation,
                    :fantasyMarketValue,
                    TRUE,
                    CAST(:rawData AS JSONB)
                )
                """)
                .param("batchId", batchId)
                .param("rowNumber", rowNumber)
                .param("externalId", externalId)
                .param("name", name)
                .param("club", club)
                .param("role", role)
                .param("quotation", quotation)
                .param(
                        "fantasyMarketValue",
                        fantasyMarketValue
                )
                .param(
                        "rawData",
                        """
                        {
                          "test": true,
                          "block": 38
                        }
                        """
                )
                .update();
    }

    private Long findSeasonId() {
        return jdbcClient.sql("""
                SELECT id
                FROM seasons
                WHERE code = '2026/2027'
                """)
                .query(Long.class)
                .single();
    }
}
