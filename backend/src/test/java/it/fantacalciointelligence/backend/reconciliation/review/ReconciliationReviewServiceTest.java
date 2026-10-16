package it.fantacalciointelligence.backend.reconciliation.review;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ReconciliationReviewServiceTest {

    @Autowired
    private ReconciliationReviewService service;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void shouldPersistUnresolvedPlayerInReviewQueue() {

        long fc = createBatch("FANTACALCIO");
        long ga = createBatch("GAZZETTA");

        insertRow(
                fc,
                "FC-REVIEW-001",
                "Review Player",
                "review player",
                "Roma"
        );

        ReconciliationReviewSyncResponse response =
                service.sync(
                        fc,
                        ga,
                        null
                );

        assertThat(response.unresolvedPlayers())
                .isEqualTo(1);

        assertThat(
                service.findAll(
                        "2026/2027",
                        "OPEN"
                )
        ).anySatisfy(item ->
                assertThat(
                        item.fantacalcioExternalId()
                ).isEqualTo(
                        "FC-REVIEW-001"
                )
        );
    }

    @Test
    void shouldResolveReviewByCreatingStableSourceLink() {

        long fc = createBatch("FANTACALCIO");
        long ga = createBatch("GAZZETTA");

        insertRow(
                fc,
                "FC-REVIEW-002",
                "Completely Different Name",
                "completely different name",
                "Roma"
        );

        insertRow(
                ga,
                "GA-REVIEW-002",
                "Different Secondary",
                "different secondary",
                "Roma"
        );

        service.sync(
                fc,
                ga,
                null
        );

        ReconciliationReviewItemResponse review =
                service.findAll(
                        "2026/2027",
                        "OPEN"
                )
                .stream()
                .filter(item ->
                        "FC-REVIEW-002".equals(
                                item.fantacalcioExternalId()
                        )
                )
                .findFirst()
                .orElseThrow();

        ReconciliationReviewItemResponse resolved =
                service.resolveLink(
                        review.id(),
                        new ReconciliationReviewResolveRequest(
                                "GAZZETTA",
                                "GA-REVIEW-002",
                                "Verified by test"
                        )
                );

        assertThat(resolved.reviewStatus())
                .isEqualTo("RESOLVED");

        Integer links =
                jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM verified_source_identity_links
                        WHERE fantacalcio_external_id =
                                'FC-REVIEW-002'
                          AND secondary_source =
                                'GAZZETTA'
                          AND secondary_external_id =
                                'GA-REVIEW-002'
                          AND verified = TRUE
                        """)
                        .query(Integer.class)
                        .single();

        assertThat(links)
                .isEqualTo(1);
    }

    @Test
    void shouldAutoResolveOpenReviewWhenPlayerBecomesReconciled() {

        long fc = createBatch("FANTACALCIO");
        long gaEmpty = createBatch("GAZZETTA");

        insertRow(
                fc,
                "FC-REVIEW-AUTO-001",
                "Auto Resolved Player",
                "auto resolved player",
                "Roma"
        );

        ReconciliationReviewSyncResponse firstSync =
                service.sync(
                        fc,
                        gaEmpty,
                        null
                );

        assertThat(firstSync.unresolvedPlayers())
                .isEqualTo(1);

        assertThat(
                service.findAll(
                        "2026/2027",
                        "OPEN"
                )
        ).anySatisfy(item ->
                assertThat(
                        item.fantacalcioExternalId()
                ).isEqualTo(
                        "FC-REVIEW-AUTO-001"
                )
        );

        long gaMatched = createBatch("GAZZETTA");

        insertRow(
                gaMatched,
                "GA-REVIEW-AUTO-001",
                "Auto Resolved Player",
                "auto resolved player",
                "Roma"
        );

        ReconciliationReviewSyncResponse secondSync =
                service.sync(
                        fc,
                        gaMatched,
                        null
                );

        assertThat(secondSync.unresolvedPlayers())
                .isZero();

        assertThat(secondSync.openItems())
                .isZero();

        ReconciliationReviewItemResponse resolved =
                service.findAll(
                        "2026/2027",
                        "RESOLVED"
                )
                .stream()
                .filter(item ->
                        "FC-REVIEW-AUTO-001".equals(
                                item.fantacalcioExternalId()
                        )
                )
                .findFirst()
                .orElseThrow();

        assertThat(resolved.resolutionSource())
                .isNull();

        assertThat(resolved.resolutionExternalId())
                .isNull();

        assertThat(resolved.resolutionNote())
                .isEqualTo(
                        "AUTO_RECONCILED_AFTER_SOURCE_REFRESH"
                );

        assertThat(resolved.resolvedAt())
                .isNotNull();
    }


    @Test
    void shouldExcludeOutOfSerieAPlayerWithoutCreatingIdentityLink() {

        long fc = createBatch("FANTACALCIO");
        long ga = createBatch("GAZZETTA");

        insertRow(
                fc,
                "FC-REVIEW-EXCLUDED-001",
                "Excluded Player",
                "excluded player",
                "Roma"
        );

        service.sync(
                fc,
                ga,
                null
        );

        ReconciliationReviewItemResponse review =
                service.findAll(
                        "2026/2027",
                        "OPEN"
                )
                .stream()
                .filter(item ->
                        "FC-REVIEW-EXCLUDED-001".equals(
                                item.fantacalcioExternalId()
                        )
                )
                .findFirst()
                .orElseThrow();

        ReconciliationReviewItemResponse excluded =
                service.exclude(
                        review.id(),
                        new ReconciliationReviewExcludeRequest(
                                "OUT_OF_SERIE_A",
                                "Player verified outside operational Serie A"
                        )
                );

        assertThat(excluded.reviewStatus())
                .isEqualTo("EXCLUDED");

        assertThat(excluded.exclusionReason())
                .isEqualTo("OUT_OF_SERIE_A");

        assertThat(excluded.exclusionNote())
                .isEqualTo(
                        "Player verified outside operational Serie A"
                );

        assertThat(excluded.excludedAt())
                .isNotNull();

        assertThat(excluded.resolutionSource())
                .isNull();

        assertThat(excluded.resolutionExternalId())
                .isNull();

        Integer links =
                jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM verified_source_identity_links
                        WHERE fantacalcio_external_id =
                                'FC-REVIEW-EXCLUDED-001'
                        """)
                        .query(Integer.class)
                        .single();

        assertThat(links)
                .isZero();
    }

    @Test
    void shouldPreserveExcludedPlayerAcrossFutureSync() {

        long fc = createBatch("FANTACALCIO");
        long ga = createBatch("GAZZETTA");

        insertRow(
                fc,
                "FC-REVIEW-EXCLUDED-002",
                "Persistently Excluded Player",
                "persistently excluded player",
                "Roma"
        );

        service.sync(
                fc,
                ga,
                null
        );

        ReconciliationReviewItemResponse review =
                service.findAll(
                        "2026/2027",
                        "OPEN"
                )
                .stream()
                .filter(item ->
                        "FC-REVIEW-EXCLUDED-002".equals(
                                item.fantacalcioExternalId()
                        )
                )
                .findFirst()
                .orElseThrow();

        service.exclude(
                review.id(),
                new ReconciliationReviewExcludeRequest(
                        "OUT_OF_SERIE_A",
                        "Verified outside Serie A"
                )
        );

        ReconciliationReviewSyncResponse secondSync =
                service.sync(
                        fc,
                        ga,
                        null
                );

        assertThat(secondSync.openItems())
                .isZero();

        ReconciliationReviewItemResponse excludedAfterSync =
                service.findAll(
                        "2026/2027",
                        "EXCLUDED"
                )
                .stream()
                .filter(item ->
                        "FC-REVIEW-EXCLUDED-002".equals(
                                item.fantacalcioExternalId()
                        )
                )
                .findFirst()
                .orElseThrow();

        assertThat(excludedAfterSync.reviewStatus())
                .isEqualTo("EXCLUDED");

        assertThat(excludedAfterSync.exclusionReason())
                .isEqualTo("OUT_OF_SERIE_A");

        assertThat(excludedAfterSync.excludedAt())
                .isNotNull();
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
                    completed_at,
                    source_observed_at
                )
                SELECT
                    :source,
                    season.id,
                    :filename,
                    'COMPLETED',
                    1,
                    1,
                    0,
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                FROM seasons season
                WHERE season.code = '2026/2027'
                RETURNING id
                """)
                .param("source", source)
                .param(
                        "filename",
                        "review-test-"
                                + source
                                + ".csv"
                )
                .query(Long.class)
                .single();
    }

    private void insertRow(
            long batchId,
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
                    1,
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
                .param("batchId", batchId)
                .param("externalId", externalId)
                .param("externalName", externalName)
                .param("normalizedName", normalizedName)
                .param("club", club)
                .param(
                        "rawData",
                        """
                        {
                          "reviewTest": true
                        }
                        """
                )
                .update();
    }
}
