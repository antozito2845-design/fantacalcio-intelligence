package it.fantacalciointelligence.backend.identity.enrichment.transfermarkt;

import java.sql.Types;
import java.time.Duration;
import java.time.OffsetDateTime;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class SelectiveIdentityEnrichmentService {

    private final JdbcClient jdbc;
    private final IdentityAutomationProperties properties;
    private final TransfermarktDiscoveryProperties discoveryProperties;
    private final TransfermarktDiscoveryService discoveryService;
    private final TransfermarktProvenancePersistenceService persistenceService;

    public SelectiveIdentityEnrichmentService(
            JdbcClient jdbc,
            IdentityAutomationProperties properties,
            TransfermarktDiscoveryProperties discoveryProperties,
            TransfermarktDiscoveryService discoveryService,
            TransfermarktProvenancePersistenceService persistenceService
    ) {

        this.jdbc = jdbc;
        this.properties = properties;
        this.discoveryProperties = discoveryProperties;
        this.discoveryService = discoveryService;
        this.persistenceService = persistenceService;
    }

    public IdentityAutomationResponse runScheduled(
            String season
    ) {

        if (!properties.isEnabled()) {
            return IdentityAutomationResponse.disabled(
                    season
            );
        }

        properties.validateEnabledConfiguration();

        if (!discoveryProperties.isEnabled()) {
            throw new IllegalStateException(
                    "Identity enrichment automation abilitata ma Transfermarkt discovery disabilitata"
            );
        }

        discoveryProperties.validateEnabledConfiguration();

        TransfermarktDiscoveryResponse discovery =
                discoveryService.discoverIncomplete(
                        season,
                        properties.getBatchLimit()
                );

        /*
         * Existing persistence is deliberately authoritative here:
         * it accepts only MATCHED_VERIFIED rows and ignores all unresolved
         * statuses.
         */
        int persistedFields =
                persistenceService.persist(
                        season,
                        discovery
                );

        /*
         * Attempts are written only after persistence succeeds.
         *
         * Therefore a database/persistence failure causes the surrounding
         * refresh to fail and the remote results remain retryable later,
         * rather than falsely recording a completed automatic pass.
         */
        for (
                TransfermarktDiscoveryResponse.PlayerDiscovery player
                : discovery.players()
        ) {

            recordAttempt(
                    season,
                    player
            );
        }

        return new IdentityAutomationResponse(
                season,
                "COMPLETED",
                discovery.requestedPlayers(),
                discovery.matchedVerified(),
                discovery.reviewRequired(),
                discovery.notFound(),
                discovery.failed(),
                persistedFields
        );
    }

    private void recordAttempt(
            String season,
            TransfermarktDiscoveryResponse.PlayerDiscovery player
    ) {

        OffsetDateTime now =
                OffsetDateTime.now();

        OffsetDateTime nextRetry =
                now.plus(
                        retryFor(
                                player.status()
                        )
                );

        jdbc.sql("""
                INSERT INTO identity_enrichment_automation_attempts (
                    season_id,
                    player_id,
                    last_status,
                    attempt_count,
                    last_score,
                    transfermarkt_player_id,
                    last_reason,
                    last_attempt_at,
                    next_retry_at
                )
                SELECT
                    season.id,
                    :playerId,
                    :status,
                    1,
                    :score,
                    :transfermarktPlayerId,
                    :reason,
                    :attemptedAt,
                    :nextRetryAt
                FROM seasons season
                WHERE season.code = :season
                ON CONFLICT (
                    season_id,
                    player_id
                )
                DO UPDATE SET
                    last_status = EXCLUDED.last_status,
                    attempt_count =
                        identity_enrichment_automation_attempts.attempt_count + 1,
                    last_score = EXCLUDED.last_score,
                    transfermarkt_player_id =
                        EXCLUDED.transfermarkt_player_id,
                    last_reason = EXCLUDED.last_reason,
                    last_attempt_at = EXCLUDED.last_attempt_at,
                    next_retry_at = EXCLUDED.next_retry_at,
                    updated_at = CURRENT_TIMESTAMP
                """)
                .param(
                        "playerId",
                        player.playerId()
                )
                .param(
                        "status",
                        normalizedStatus(
                                player.status()
                        )
                )
                .param(
                        "score",
                        Math.max(
                                0,
                                Math.min(
                                        100,
                                        player.score()
                                )
                        )
                )
                .param(
                        "transfermarktPlayerId",
                        clean(
                                player.transfermarktPlayerId()
                        ),
                        Types.VARCHAR
                )
                .param(
                        "reason",
                        truncate(
                                clean(player.reason()),
                                500
                        ),
                        Types.VARCHAR
                )
                .param(
                        "attemptedAt",
                        now
                )
                .param(
                        "nextRetryAt",
                        nextRetry
                )
                .param(
                        "season",
                        season
                )
                .update();
    }

    private Duration retryFor(
            String status
    ) {

        return switch (
                normalizedStatus(status)
        ) {
            case "MATCHED_VERIFIED" ->
                    properties.getMatchedRetry();

            case "FAILED" ->
                    properties.getFailedRetry();

            default ->
                    properties.getUnresolvedRetry();
        };
    }

    private String normalizedStatus(
            String status
    ) {

        if ("MATCHED_VERIFIED".equals(status)) {
            return "MATCHED_VERIFIED";
        }

        if ("REVIEW_REQUIRED".equals(status)) {
            return "REVIEW_REQUIRED";
        }

        if ("FAILED".equals(status)) {
            return "FAILED";
        }

        return "NOT_FOUND";
    }

    private String clean(
            String value
    ) {

        if (value == null) {
            return null;
        }

        String cleaned =
                value.trim();

        return cleaned.isBlank()
                ? null
                : cleaned;
    }

    private String truncate(
            String value,
            int maximum
    ) {

        if (value == null) {
            return null;
        }

        return value.length() <= maximum
                ? value
                : value.substring(
                        0,
                        maximum
                );
    }
}