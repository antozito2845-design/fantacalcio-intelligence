package it.fantacalciointelligence.backend.reconciliation.preview;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ReconciliationPreviewServiceTest {

    @Autowired
    private ReconciliationPreviewService service;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void shouldPreviewUsingRealIdentityEngineWithoutWritingReconciliationResults() {

        long fantacalcioBatch =
                createBatch("FANTACALCIO");

        long gazzettaBatch =
                createBatch("GAZZETTA");

        insertRow(
                fantacalcioBatch,
                1,
                "FC-PREVIEW-001",
                "Mario Rossi",
                "mario rossi",
                "Roma"
        );

        insertRow(
                gazzettaBatch,
                1,
                "GA-PREVIEW-001",
                "Mario Rossi",
                "mario rossi",
                "Roma"
        );

        insertRow(
                fantacalcioBatch,
                2,
                "FC-PREVIEW-002",
                "Solo Fantacalcio",
                "solo fantacalcio",
                "Roma"
        );

        long before =
                reconciliationResultCount();

        ReconciliationPreviewResponse response =
                service.preview(
                        fantacalcioBatch,
                        gazzettaBatch,
                        null,
                        false
                );

        long after =
                reconciliationResultCount();

        assertThat(response.totalFantacalcioPlayers())
                .isEqualTo(2);

        assertThat(response.confirmedPlayers())
                .isEqualTo(1);

        assertThat(response.unresolvedPlayers())
                .isEqualTo(1);

        assertThat(response.players())
                .hasSize(2);

        assertThat(
                response.methodCounts()
                        .get(
                                "EXACT_NAME_SAME_CLUB"
                        )
        ).isEqualTo(1);

        assertThat(
                response.methodCounts()
                        .get("UNRESOLVED")
        ).isEqualTo(1);

        assertThat(after)
                .isEqualTo(before);
    }

    @Test
    void shouldReturnOnlyUnresolvedPlayersWhenRequested() {

        long fantacalcioBatch =
                createBatch("FANTACALCIO");

        long gazzettaBatch =
                createBatch("GAZZETTA");

        insertRow(
                fantacalcioBatch,
                1,
                "FC-PREVIEW-U-001",
                "Confirmed Player",
                "confirmed player",
                "Roma"
        );

        insertRow(
                gazzettaBatch,
                1,
                "GA-PREVIEW-U-001",
                "Confirmed Player",
                "confirmed player",
                "Roma"
        );

        insertRow(
                fantacalcioBatch,
                2,
                "FC-PREVIEW-U-002",
                "Unresolved Player",
                "unresolved player",
                "Roma"
        );

        ReconciliationPreviewResponse response =
                service.preview(
                        fantacalcioBatch,
                        gazzettaBatch,
                        null,
                        true
                );

        assertThat(response.totalFantacalcioPlayers())
                .isEqualTo(2);

        assertThat(response.confirmedPlayers())
                .isEqualTo(1);

        assertThat(response.unresolvedPlayers())
                .isEqualTo(1);

        assertThat(response.players())
                .hasSize(1);

        ReconciliationPreviewPlayer player =
                response.players()
                        .getFirst();

        assertThat(player.status())
                .isEqualTo("UNRESOLVED");

        assertThat(player.fantacalcioName())
                .isEqualTo(
                        "Unresolved Player"
                );

        assertThat(player.sourcesCount())
                .isEqualTo(1);
    }

    private long createBatch(
            String source
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
                    completed_at
                )
                SELECT
                    :source,
                    season.id,
                    :filename,
                    'COMPLETED',
                    0,
                    0,
                    0,
                    CURRENT_TIMESTAMP
                FROM seasons season
                WHERE season.code = '2026/2027'
                RETURNING id
                """)
                .param(
                        "source",
                        source
                )
                .param(
                        "filename",
                        "preview-"
                                + source
                                + ".csv"
                )
                .query(Long.class)
                .single();
    }

    private void insertRow(
            long batchId,
            int rowNumber,
            String externalId,
            String externalName,
            String normalizedName,
            String club
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
                    valid,
                    raw_data
                )
                VALUES (
                    :batchId,
                    :rowNumber,
                    :externalId,
                    :externalName,
                    :normalizedName,
                    :club,
                    LOWER(:club),
                    'C',
                    TRUE,
                    CAST(:rawData AS JSONB)
                )
                """)
                .param(
                        "batchId",
                        batchId
                )
                .param(
                        "rowNumber",
                        rowNumber
                )
                .param(
                        "externalId",
                        externalId
                )
                .param(
                        "externalName",
                        externalName
                )
                .param(
                        "normalizedName",
                        normalizedName
                )
                .param(
                        "club",
                        club
                )
                .param(
                        "rawData",
                        """
                        {
                          "previewTest": true
                        }
                        """
                )
                .update();
    }

    private long reconciliationResultCount() {

        return jdbcClient.sql("""
                SELECT COUNT(*)
                FROM player_reconciliation_results
                """)
                .query(Long.class)
                .single();
    }
}