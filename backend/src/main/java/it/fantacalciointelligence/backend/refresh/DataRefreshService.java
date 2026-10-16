package it.fantacalciointelligence.backend.refresh;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.Duration;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import it.fantacalciointelligence.backend.catalog.readiness.CatalogReadinessResponse;
import it.fantacalciointelligence.backend.catalog.readiness.CatalogReadinessService;
import it.fantacalciointelligence.backend.common.ConflictException;
import it.fantacalciointelligence.backend.common.ResourceNotFoundException;
import it.fantacalciointelligence.backend.common.SecretSanitizer;
import it.fantacalciointelligence.backend.evaluation.PlayerEvaluationGenerationResponse;
import it.fantacalciointelligence.backend.evaluation.PlayerEvaluationGenerationService;
import it.fantacalciointelligence.backend.fixture.strength.ClubStrengthGenerationResponse;
import it.fantacalciointelligence.backend.fixture.strength.ClubStrengthGenerationService;
import it.fantacalciointelligence.backend.freshness.DataFreshnessResponse;
import it.fantacalciointelligence.backend.freshness.DataFreshnessService;
import it.fantacalciointelligence.backend.identity.enrichment.transfermarkt.SelectiveIdentityEnrichmentService;
import it.fantacalciointelligence.backend.reconciliation.PlayerReconciliationService;
import it.fantacalciointelligence.backend.reconciliation.ReconciliationResponse;
import it.fantacalciointelligence.backend.reconciliation.review.ReconciliationReviewService;
import it.fantacalciointelligence.backend.transfer.detection.TransferDetectionService;

@Service
public class DataRefreshService {

    private final JdbcClient jdbcClient;

    private final PlayerReconciliationService
            reconciliationService;

    private final PlayerEvaluationGenerationService
            evaluationService;

    private final ClubStrengthGenerationService
            clubStrengthService;

    private final TransferDetectionService
            transferDetectionService;

    private final ReconciliationReviewService
            reconciliationReviewService;

    private final CatalogReadinessService
            catalogReadinessService;

    private final DataFreshnessService
            freshnessService;

    private final SelectiveIdentityEnrichmentService
            identityEnrichmentService;
    private final Duration staleThreshold;
    private final RefreshFaultInjector faultInjector;

    public DataRefreshService(
            JdbcClient jdbcClient,
            PlayerReconciliationService reconciliationService,
            PlayerEvaluationGenerationService evaluationService,
            ClubStrengthGenerationService clubStrengthService,
            TransferDetectionService transferDetectionService,
            ReconciliationReviewService reconciliationReviewService,
            CatalogReadinessService catalogReadinessService,
            DataFreshnessService freshnessService,
            SelectiveIdentityEnrichmentService identityEnrichmentService,
            RefreshFaultInjector faultInjector,
            @Value("${app.data-refresh.stale-threshold:2h}") Duration staleThreshold
    ) {
        this.jdbcClient = jdbcClient;
        this.reconciliationService =
                reconciliationService;
        this.evaluationService =
                evaluationService;
        this.clubStrengthService =
                clubStrengthService;
        this.transferDetectionService =
                transferDetectionService;
        this.reconciliationReviewService =
                reconciliationReviewService;
        this.catalogReadinessService =
                catalogReadinessService;
        this.freshnessService =
                freshnessService;
        this.identityEnrichmentService =
                identityEnrichmentService;
        this.faultInjector = faultInjector;
        if (staleThreshold == null || staleThreshold.isZero() || staleThreshold.isNegative()) {
            throw new IllegalStateException("app.data-refresh.stale-threshold deve essere maggiore di zero");
        }
        this.staleThreshold = staleThreshold;
    }

    public DataRefreshResponse runManual(
            String seasonCode
    ) {
        return run(
                seasonCode,
                "MANUAL"
        );
    }

    public DataRefreshResponse runScheduled(
            String seasonCode
    ) {
        return run(
                seasonCode,
                "SCHEDULED"
        );
    }

    public DataRefreshResponse findLatest(
            String seasonCode
    ) {

        return jdbcClient.sql("""
                SELECT
                    refresh.id,
                    season.code AS season,
                    refresh.trigger_type,
                    refresh.status,
                    refresh.source_freshness_status,
                    refresh.source_freshness_checked_at,
                    refresh.reconciliation_matched_players,
                    refresh.generated_evaluations,
                    refresh.generated_club_ratings,
                    refresh.started_at,
                    refresh.completed_at,
                    refresh.error_message

                FROM data_refresh_runs refresh

                JOIN seasons season
                    ON season.id =
                        refresh.season_id

                WHERE season.code = :season

                ORDER BY
                    refresh.started_at DESC,
                    refresh.id DESC

                LIMIT 1
                """)
                .param(
                        "season",
                        seasonCode
                )
                .query((resultSet, rowNumber) ->
                        mapResponse(resultSet)
                )
                .optional()
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Nessun aggiornamento trovato "
                                        + "per la stagione "
                                        + seasonCode
                        )
                );
    }

    private DataRefreshResponse run(
            String seasonCode,
            String triggerType
    ) {

        Long seasonId =
                findSeasonId(
                        seasonCode
                );

        Long refreshId =
                createRefreshRun(
                        seasonId,
                        triggerType
                );

        try {
            DataFreshnessResponse freshness =
                    freshnessService.evaluate(
                            seasonCode
                    );

            updateRefreshFreshness(
                    refreshId,
                    freshness.overallStatus()
            );

            if (freshness.blocked()) {
                throw new ConflictException(
                        "Dati sorgente non abbastanza aggiornati: "
                                + String.join(
                                        "; ",
                                        freshness.blockers()
                                )
                );
            }

            /*
             * Transfer detection MUST run against the previous
             * active registration before reconciliation applies
             * the newest Fantacalcio snapshot.
             */
            transferDetectionService.detectLatest(
                    seasonCode
            );
            faultInjector.checkpoint("AFTER_TRANSFER_DETECTION");

            ReconciliationResponse reconciliation =
                    reconciliationService.reconcile(
                            seasonCode
                    );
            faultInjector.checkpoint("AFTER_RECONCILIATION");

            /*
             * Persist unresolved identities after reconciliation
             * so an administrator has a durable review queue.
             */
            reconciliationReviewService.syncLatest(
                    seasonCode
            );
            faultInjector.checkpoint("AFTER_REVIEW_SYNC");

            /*
             * B71 production identity enrichment.
             *
             * It MUST run after reconciliation because Transfermarkt
             * discovery reads the newly reconciled operational catalog.
             *
             * Manual refreshes never initiate remote identity discovery.
             */
            if ("SCHEDULED".equals(triggerType)) {

                identityEnrichmentService.runScheduled(
                        seasonCode
                );

                faultInjector.checkpoint(
                        "AFTER_IDENTITY_ENRICHMENT"
                );
            }

            CatalogReadinessResponse readiness =
                    catalogReadinessService.evaluateLatest(
                            seasonCode
                    );

            /*
             * WARNING is allowed: unresolved identities and
             * pending transfers are expected during the market.
             *
             * BLOCKED means structural catalog corruption such
             * as reconciled players without a role or an ACTIVE
             * registration.
             */
            if (readiness.blocked()) {
                throw new ConflictException(
                        "Catalogo non pronto: "
                                + String.join(
                                        "; ",
                                        readiness.blockers()
                                )
                );
            }

            PlayerEvaluationGenerationResponse evaluations =
                    evaluationService.generate(
                            seasonCode
                    );
            faultInjector.checkpoint("AFTER_EVALUATIONS");

            ClubStrengthGenerationResponse clubRatings =
                    clubStrengthService.generate(
                            seasonCode
                    );
            faultInjector.checkpoint("AFTER_CLUB_STRENGTH");
            faultInjector.checkpoint("BEFORE_COMPLETE");

            completeRefreshRun(
                    refreshId,
                    reconciliation.matchedPlayers(),
                    evaluations.generatedEvaluations(),
                    clubRatings.generatedRatings()
            );

            return findById(refreshId);

        } catch (RuntimeException exception) {

            markRefreshRunFailed(
                    refreshId,
                    readableMessage(exception)
            );

            throw exception;
        }
    }

    private Long findSeasonId(
            String seasonCode
    ) {

        return jdbcClient.sql("""
                SELECT id
                FROM seasons
                WHERE code = :season
                """)
                .param(
                        "season",
                        seasonCode
                )
                .query(Long.class)
                .optional()
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Stagione non trovata: "
                                        + seasonCode
                        )
                );
    }

    Long createRefreshRun(
            Long seasonId,
            String triggerType
    ) {

        recoverStaleRefresh(seasonId);
        try {
            return jdbcClient.sql("""
                    INSERT INTO data_refresh_runs (
                        season_id,
                        trigger_type,
                        status
                    )
                    VALUES (
                        :seasonId,
                        :triggerType,
                        'RUNNING'
                    )
                    RETURNING id
                    """)
                    .param(
                            "seasonId",
                            seasonId
                    )
                    .param(
                            "triggerType",
                            triggerType
                    )
                    .query(Long.class)
                    .single();

        } catch (DataIntegrityViolationException exception) {

            throw new ConflictException(
                    "Un aggiornamento dati è già in esecuzione"
            );
        }
    }

    private void recoverStaleRefresh(Long seasonId) {
        jdbcClient.sql("""
                UPDATE data_refresh_runs
                SET status = 'FAILED', completed_at = CURRENT_TIMESTAMP,
                    error_message = 'Esecuzione interrotta recuperata automaticamente'
                WHERE season_id = :seasonId
                  AND status = 'RUNNING'
                  AND started_at < CURRENT_TIMESTAMP - (:staleSeconds * INTERVAL '1 second')
                """)
                .param("seasonId", seasonId)
                .param("staleSeconds", staleThreshold.toSeconds())
                .update();
    }

    private void updateRefreshFreshness(
            Long refreshId,
            String freshnessStatus
    ) {

        jdbcClient.sql("""
                UPDATE data_refresh_runs
                SET
                    source_freshness_status =
                        :freshnessStatus,

                    source_freshness_checked_at =
                        CURRENT_TIMESTAMP

                WHERE id = :refreshId
                """)
                .param(
                        "freshnessStatus",
                        freshnessStatus
                )
                .param(
                        "refreshId",
                        refreshId
                )
                .update();
    }

    private void completeRefreshRun(
            Long refreshId,
            int matchedPlayers,
            int generatedEvaluations,
            int generatedClubRatings
    ) {

        jdbcClient.sql("""
                UPDATE data_refresh_runs
                SET
                    status = 'COMPLETED',

                    reconciliation_matched_players =
                        :matchedPlayers,

                    generated_evaluations =
                        :generatedEvaluations,

                    generated_club_ratings =
                        :generatedClubRatings,

                    completed_at =
                        CURRENT_TIMESTAMP,

                    error_message =
                        NULL

                WHERE id = :refreshId
                """)
                .param(
                        "matchedPlayers",
                        matchedPlayers
                )
                .param(
                        "generatedEvaluations",
                        generatedEvaluations
                )
                .param(
                        "generatedClubRatings",
                        generatedClubRatings
                )
                .param(
                        "refreshId",
                        refreshId
                )
                .update();
    }

    private void markRefreshRunFailed(
            Long refreshId,
            String errorMessage
    ) {

        jdbcClient.sql("""
                UPDATE data_refresh_runs
                SET
                    status = 'FAILED',

                    completed_at =
                        CURRENT_TIMESTAMP,

                    error_message =
                        :errorMessage

                WHERE id = :refreshId
                """)
                .param(
                        "errorMessage",
                        truncate(
                                errorMessage,
                                2000
                        ),
                        Types.VARCHAR
                )
                .param(
                        "refreshId",
                        refreshId
                )
                .update();
    }

    private DataRefreshResponse findById(
            Long refreshId
    ) {

        return jdbcClient.sql("""
                SELECT
                    refresh.id,
                    season.code AS season,
                    refresh.trigger_type,
                    refresh.status,
                    refresh.source_freshness_status,
                    refresh.source_freshness_checked_at,
                    refresh.reconciliation_matched_players,
                    refresh.generated_evaluations,
                    refresh.generated_club_ratings,
                    refresh.started_at,
                    refresh.completed_at,
                    refresh.error_message

                FROM data_refresh_runs refresh

                JOIN seasons season
                    ON season.id =
                        refresh.season_id

                WHERE refresh.id = :refreshId
                """)
                .param(
                        "refreshId",
                        refreshId
                )
                .query((resultSet, rowNumber) ->
                        mapResponse(resultSet)
                )
                .single();
    }

    private DataRefreshResponse mapResponse(
            java.sql.ResultSet resultSet
    ) throws java.sql.SQLException {

        return new DataRefreshResponse(
                resultSet.getLong("id"),
                resultSet.getString("season"),

                resultSet.getString(
                        "trigger_type"
                ),

                resultSet.getString("status"),

                resultSet.getString(
                        "source_freshness_status"
                ),

                resultSet.getObject(
                        "source_freshness_checked_at",
                        OffsetDateTime.class
                ),

                resultSet.getInt(
                        "reconciliation_matched_players"
                ),

                resultSet.getInt(
                        "generated_evaluations"
                ),

                resultSet.getInt(
                        "generated_club_ratings"
                ),

                resultSet.getObject(
                        "started_at",
                        OffsetDateTime.class
                ),

                resultSet.getObject(
                        "completed_at",
                        OffsetDateTime.class
                ),

                resultSet.getString(
                        "error_message"
                )
        );
    }

    private String readableMessage(
            Throwable exception
    ) {

        return SecretSanitizer.sanitize(exception);
    }

    private String truncate(
            String value,
            int maximumLength
    ) {

        if (value == null) {
            return null;
        }

        if (value.length()
                <= maximumLength) {

            return value;
        }

        return value.substring(
                0,
                maximumLength
        );
    }
}
