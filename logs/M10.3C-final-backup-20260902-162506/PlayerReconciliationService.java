package it.fantacalciointelligence.backend.reconciliation;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.common.ConflictException;
import it.fantacalciointelligence.backend.common.ResourceNotFoundException;
import it.fantacalciointelligence.backend.reconciliation.identity.PlayerIdentityResolutionService;
import it.fantacalciointelligence.backend.reconciliation.identity.ResolvedPlayerIdentity;

@Service
public class PlayerReconciliationService {

    private static final String FANTACALCIO = "FANTACALCIO";
    private static final String GAZZETTA = "GAZZETTA";
    private static final String FANTAMARKT = "FANTAMARKT";

    private final JdbcClient jdbcClient;
    private final PlayerIdentityResolutionService identityResolutionService;

    public PlayerReconciliationService(
            JdbcClient jdbcClient,
            PlayerIdentityResolutionService identityResolutionService
    ) {
        this.jdbcClient = jdbcClient;
        this.identityResolutionService = identityResolutionService;
    }

    @Transactional
    public ReconciliationResponse reconcile(
            String seasonCode
    ) {

        Long seasonId =
                findSeasonId(seasonCode);

        BatchSnapshot fantacalcioBatch =
                latestBatch(
                        seasonId,
                        FANTACALCIO
                );

        BatchSnapshot gazzettaBatch =
                latestBatch(
                        seasonId,
                        GAZZETTA
                );

        Optional<BatchSnapshot> fantamarktBatch =
                latestOptionalProductionBatch(
                        seasonId,
                        FANTAMARKT
                );

        markReconciliationStarted(
                fantacalcioBatch.id()
        );

        markReconciliationStarted(
                gazzettaBatch.id()
        );

        fantamarktBatch.ifPresent(batch ->
                markReconciliationStarted(
                        batch.id()
                )
        );

        List<ResolvedPlayerIdentity> identities =
                identityResolutionService.resolve(
                        fantacalcioBatch.id(),
                        gazzettaBatch.id(),
                        fantamarktBatch
                                .map(BatchSnapshot::id)
                                .orElse(null)
                );

        /*
         * Fantacalcio is the authoritative operational catalog
         * during the transfer market.
         *
         * Secondary sources enrich and confirm identity/data,
         * but they do not decide whether a current Fantacalcio
         * player can exist in the operational catalog.
         */
        List<ResolvedPlayerIdentity> catalogIdentities =
                identities.stream()
                        .filter(identity ->
                                isNotBlank(
                                        identity
                                                .fantacalcioName()
                                )
                        )
                        .toList();

        /*
         * Kept for API backward compatibility.
         *
         * Single-source Fantacalcio players are no longer
         * excluded from the operational catalog. Their lack of
         * secondary confirmation remains visible through the
         * reconciliation preview / enrichment workflow.
         */
        int excludedSingleSourcePlayers = 0;

        int matchedPlayers = 0;
        int matchedWithThreeSources = 0;
        int matchedWithTwoSources = 0;
        int createdPlayers = 0;
        int updatedPlayers = 0;

        int fantacalcioReconciledRows = 0;
        int gazzettaReconciledRows = 0;
        int fantamarktReconciledRows = 0;

        /*
         * Rebuild the official current catalog transactionally.
         */
        jdbcClient.sql("""
                DELETE FROM player_reconciliation_results
                WHERE season_id = :seasonId
                """)
                .param("seasonId", seasonId)
                .update();

        /*
         * Operational lifecycle follows the latest Fantacalcio
         * listone snapshot.
         *
         * A player that was ACTIVE but is no longer present in
         * the latest snapshot becomes INACTIVE.
         *
         * No player/history/source mapping is deleted.
         *
         * If the player appears again in a later Fantacalcio
         * snapshot, saveRegistration(...) below reactivates him.
         */
        deactivatePlayersMissingFromLatestFantacalcio(
                seasonId,
                fantacalcioBatch.id()
        );

        for (ResolvedPlayerIdentity identity
                : catalogIdentities) {

            /*
             * Fantacalcio is authoritative by default.
             *
             * A durable, explicitly verified EXCLUDED review is
             * the controlled exception: it means that this player
             * has already been manually verified as not belonging
             * to the operational Serie A catalog even if the
             * Fantacalcio snapshot is temporarily stale.
             */
            if (isExplicitlyExcluded(
                    seasonId,
                    identity.fantacalcioExternalId()
            )) {

                findMappedPlayerId(
                        FANTACALCIO,
                        identity.fantacalcioExternalId()
                ).ifPresent(playerId ->
                        deactivateRegistration(
                                playerId,
                                seasonId
                        )
                );

                continue;
            }

            String displayName =
                    firstNonBlank(
                            identity.fantacalcioName(),
                            identity.gazzettaName(),
                            identity.fantamarktName()
                    );

            Optional<Long> existingPlayerId =
                    findExistingPlayerId(
                            identity
                    );

            Long playerId;

            if (existingPlayerId.isPresent()) {

                playerId =
                        existingPlayerId.get();

                updatePlayer(
                        playerId,
                        displayName,
                        identity.normalizedName()
                );

                updatedPlayers++;

            } else {

                playerId =
                        createPlayer(
                                displayName,
                                identity.normalizedName()
                        );

                createdPlayers++;
            }

            /*
             * Source mappings are identity information.
             * They are saved independently from club resolution.
             */
            saveSourceIfPresent(
                    playerId,
                    seasonId,
                    FANTACALCIO,
                    identity.fantacalcioExternalId(),
                    identity.fantacalcioName(),
                    identity.fantacalcioRole()
            );

            saveMarketValue(
                    playerId,
                    seasonId,
                    FANTACALCIO,
                    identity.fantacalcioQuotation(),
                    identity.fantacalcioFmv()
            );

            saveSourceIfPresent(
                    playerId,
                    seasonId,
                    GAZZETTA,
                    identity.gazzettaExternalId(),
                    identity.gazzettaName(),
                    identity.gazzettaRole()
            );

            saveSourceIfPresent(
                    playerId,
                    seasonId,
                    FANTAMARKT,
                    identity.fantamarktExternalId(),
                    identity.fantamarktName(),
                    identity.fantamarktRole()
            );

            /*
             * Fantacalcio marks players that are still visible in
             * the listone but no longer play in Serie A with the
             * semantic out-of-game marker.
             *
             * They remain historically stored with their mappings,
             * roles and market data, but must not be part of the
             * current operational catalog.
             *
             * TransferDetectionService separately creates a
             * DETECTED exit claim. Official evidence is still
             * required before assigning a definitive transfer
             * lifecycle such as RELEASED.
             */
            if (isFantacalcioOutOfGame(
                    fantacalcioBatch.id(),
                    identity.fantacalcioExternalId()
            )) {

                deactivateRegistration(
                        playerId,
                        seasonId
                );

                continue;
            }

            ClubResolution clubResolution =
                    resolveCurrentClub(
                            playerId,
                            seasonId,
                            identity,
                            fantacalcioBatch
                                    .sourceObservedAt()
                    );

            /*
             * An official and sufficiently recent exit from
             * Serie A wins over an older Fantacalcio snapshot.
             *
             * The player remains stored historically but is no
             * longer included in the current official catalog.
             */
            if (clubResolution.leftSerieA()) {

                preserveReleasedRegistration(
                        playerId,
                        seasonId
                );

                continue;
            }

            saveRegistration(
                    playerId,
                    clubResolution.clubId(),
                    seasonId
            );

            saveReconciliationResult(
                    seasonId,
                    playerId,
                    identity,
                    clubResolution
            );

            matchedPlayers++;

            if (identity.sourcesCount() == 3) {
                matchedWithThreeSources++;
            } else if (identity.sourcesCount() == 2) {
                matchedWithTwoSources++;
            }

            fantacalcioReconciledRows++;

            if (isNotBlank(
                    identity.gazzettaName()
            )) {
                gazzettaReconciledRows++;
            }

            if (isNotBlank(
                    identity.fantamarktName()
            )) {
                fantamarktReconciledRows++;
            }
        }
        enforceLatestFantacalcioOperationalRegistrations(
                seasonId,
                fantacalcioBatch.id(),
                fantacalcioBatch.sourceObservedAt()
        );

        markReconciliationCompleted(
                fantacalcioBatch.id(),
                fantacalcioReconciledRows
        );

        markReconciliationCompleted(
                gazzettaBatch.id(),
                gazzettaReconciledRows
        );

        if (fantamarktBatch.isPresent()) {
            markReconciliationCompleted(
                    fantamarktBatch.get().id(),
                    fantamarktReconciledRows
            );
        }

        return new ReconciliationResponse(
                seasonCode,
                fantacalcioBatch.id(),
                gazzettaBatch.id(),
                fantamarktBatch
                        .map(BatchSnapshot::id)
                        .orElse(0L),
                matchedPlayers,
                matchedWithThreeSources,
                matchedWithTwoSources,
                excludedSingleSourcePlayers,
                createdPlayers,
                updatedPlayers,
                "COMPLETED"
        );
    }

    private boolean isFantacalcioOutOfGame(
            Long fantacalcioBatchId,
            String fantacalcioExternalId
    ) {

        if (!isNotBlank(fantacalcioExternalId)) {
            return false;
        }

        Boolean outOfGame =
                jdbcClient.sql("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM player_import_rows
                            WHERE batch_id = :batchId
                              AND external_id =
                                  :fantacalcioExternalId
                              AND valid = TRUE
                              AND LOWER(
                                    COALESCE(
                                        raw_data ->> 'out_of_game',
                                        'false'
                                    )
                                  ) = 'true'
                        )
                        """)
                        .param(
                                "batchId",
                                fantacalcioBatchId
                        )
                        .param(
                                "fantacalcioExternalId",
                                fantacalcioExternalId
                        )
                        .query(Boolean.class)
                        .single();

        return Boolean.TRUE.equals(outOfGame);
    }
    private boolean isExplicitlyExcluded(
            Long seasonId,
            String fantacalcioExternalId
    ) {

        if (!isNotBlank(fantacalcioExternalId)) {
            return false;
        }

        Integer count =
                jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM reconciliation_review_items
                        WHERE season_id = :seasonId
                          AND fantacalcio_external_id =
                              :fantacalcioExternalId
                          AND review_status = 'EXCLUDED'
                        """)
                        .param(
                                "seasonId",
                                seasonId
                        )
                        .param(
                                "fantacalcioExternalId",
                                fantacalcioExternalId
                        )
                        .query(Integer.class)
                        .single();

        return count > 0;
    }

    private void deactivateRegistration(
            Long playerId,
            Long seasonId
    ) {

        jdbcClient.sql("""
                UPDATE player_registrations
                SET registration_status = 'INACTIVE',
                    left_at = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE player_id = :playerId
                  AND season_id = :seasonId
                  AND registration_status = 'ACTIVE'
                """)
                .param(
                        "playerId",
                        playerId
                )
                .param(
                        "seasonId",
                        seasonId
                )
                .update();
    }
    private int deactivatePlayersMissingFromLatestFantacalcio(
            Long seasonId,
            Long fantacalcioBatchId
    ) {

        return jdbcClient.sql("""
                UPDATE player_registrations registration

                SET registration_status = 'INACTIVE',
                    left_at = NULL,
                    updated_at = CURRENT_TIMESTAMP

                WHERE registration.season_id = :seasonId
                  AND registration.registration_status = 'ACTIVE'

                  AND NOT EXISTS (
                      SELECT 1

                      FROM player_source_mappings mapping

                      JOIN player_import_rows import_row
                        ON import_row.batch_id =
                           :fantacalcioBatchId

                       AND import_row.valid = TRUE

                       AND import_row.external_id =
                           mapping.external_id

                      WHERE mapping.player_id =
                            registration.player_id

                        AND mapping.source =
                            'FANTACALCIO'
                  )
                """)
                .param(
                        "seasonId",
                        seasonId
                )
                .param(
                        "fantacalcioBatchId",
                        fantacalcioBatchId
                )
                .update();
    }
    private ClubResolution resolveCurrentClub(
            Long playerId,
            Long seasonId,
            ResolvedPlayerIdentity identity,
            OffsetDateTime fantacalcioObservedAt
    ) {

        Optional<OfficialTransferClaim> officialClaim =
                findLatestOfficialTransferClaim(
                        playerId,
                        seasonId
                );

        if (officialClaim.isPresent()) {

            OfficialTransferClaim claim =
                    officialClaim.get();

            OffsetDateTime officialTimestamp =
                    firstNonNull(
                            claim.sourcePublishedAt(),
                            claim.verifiedAt(),
                            claim.observedAt()
                    );

            /*
             * If we have trustworthy temporal provenance for
             * Fantacalcio and that snapshot is strictly newer,
             * Fantacalcio becomes the current source again.
             *
             * For legacy imports sourceObservedAt is NULL:
             * in that case an official verified event wins.
             */
            boolean fantacalcioIsNewer =
                    fantacalcioObservedAt != null
                            && officialTimestamp != null
                            && officialTimestamp.isBefore(
                                    fantacalcioObservedAt
                            );

            if (!fantacalcioIsNewer) {

                if (claim.toClubId() == null) {

                    return new ClubResolution(
                            null,
                            null,
                            null,
                            claim.source(),
                            claim.verifiedAt(),
                            true
                    );
                }

                return new ClubResolution(
                        claim.toClubId(),
                        claim.toClubName(),
                        normalizeClub(
                                claim.toClubName()
                        ),
                        claim.source(),
                        claim.verifiedAt(),
                        false
                );
            }
        }

        Long fantacalcioClubId =
                findClubId(
                        identity.fantacalcioClub()
                );

        return new ClubResolution(
                fantacalcioClubId,
                identity.fantacalcioClub(),
                firstNonBlank(
                        identity
                                .fantacalcioNormalizedClub(),
                        normalizeClub(
                                identity.fantacalcioClub()
                        )
                ),
                FANTACALCIO,
                null,
                false
        );
    }

    private Optional<OfficialTransferClaim>
            findLatestOfficialTransferClaim(
                    Long playerId,
                    Long seasonId
            ) {

        return jdbcClient.sql("""
                SELECT
                    claim.to_club_id,
                    target_club.name AS to_club_name,
                    claim.source,
                    claim.source_published_at,
                    claim.observed_at,
                    claim.verified_at

                FROM player_transfer_claims claim

                LEFT JOIN clubs target_club
                    ON target_club.id =
                       claim.to_club_id

                WHERE claim.player_id = :playerId
                  AND claim.season_id = :seasonId
                  AND claim.verification_status = 'OFFICIAL'
                  AND claim.source IN (
                      'FIGC',
                      'LEGA_SERIE_A',
                      'OFFICIAL_CLUB'
                  )

                ORDER BY
                    COALESCE(
                        claim.source_published_at,
                        claim.verified_at,
                        claim.observed_at
                    ) DESC,
                    claim.id DESC

                LIMIT 1
                """)
                .param("playerId", playerId)
                .param("seasonId", seasonId)
                .query((resultSet, rowNumber) ->
                        new OfficialTransferClaim(
                                nullableLong(
                                        resultSet,
                                        "to_club_id"
                                ),
                                resultSet.getString(
                                        "to_club_name"
                                ),
                                resultSet.getString(
                                        "source"
                                ),
                                resultSet.getObject(
                                        "source_published_at",
                                        OffsetDateTime.class
                                ),
                                resultSet.getObject(
                                        "observed_at",
                                        OffsetDateTime.class
                                ),
                                resultSet.getObject(
                                        "verified_at",
                                        OffsetDateTime.class
                                )
                        )
                )
                .optional();
    }

    private Optional<Long> findExistingPlayerId(
            ResolvedPlayerIdentity identity
    ) {

        Set<Long> mappedPlayerIds =
                new LinkedHashSet<>();

        findMappedPlayerId(
                FANTACALCIO,
                identity.fantacalcioExternalId()
        ).ifPresent(mappedPlayerIds::add);

        findMappedPlayerId(
                GAZZETTA,
                identity.gazzettaExternalId()
        ).ifPresent(mappedPlayerIds::add);

        findMappedPlayerId(
                FANTAMARKT,
                identity.fantamarktExternalId()
        ).ifPresent(mappedPlayerIds::add);

        if (mappedPlayerIds.size() > 1) {
            throw new ConflictException(
                    "Le fonti risultano associate "
                            + "a giocatori diversi: "
                            + identity.normalizedName()
            );
        }

        if (mappedPlayerIds.size() == 1) {
            return mappedPlayerIds
                    .stream()
                    .findFirst();
        }

        List<Long> sameNamePlayerIds =
                jdbcClient.sql("""
                        SELECT id
                        FROM players
                        WHERE normalized_name =
                              :normalizedName
                        ORDER BY id
                        """)
                        .param(
                                "normalizedName",
                                identity.normalizedName()
                        )
                        .query(Long.class)
                        .list();

        if (sameNamePlayerIds.isEmpty()) {
            return Optional.empty();
        }

        if (sameNamePlayerIds.size() == 1) {
            return Optional.of(
                    sameNamePlayerIds.getFirst()
            );
        }

        /*
         * Club is deliberately NOT used here.
         * Identity and current registration are independent.
         */
        throw new ConflictException(
                "IdentitÃƒÂ  interna ambigua per il giocatore: "
                        + identity.normalizedName()
        );
    }

    private Optional<Long> findMappedPlayerId(
            String source,
            String externalId
    ) {

        if (!isNotBlank(externalId)) {
            return Optional.empty();
        }

        return jdbcClient.sql("""
                SELECT player_id
                FROM player_source_mappings
                WHERE source = :source
                  AND external_id = :externalId
                ORDER BY id
                LIMIT 1
                """)
                .param("source", source)
                .param("externalId", externalId)
                .query(Long.class)
                .optional();
    }

    private Long findSeasonId(
            String seasonCode
    ) {

        return jdbcClient.sql("""
                SELECT id
                FROM seasons
                WHERE code = :seasonCode
                """)
                .param(
                        "seasonCode",
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

    private BatchSnapshot latestBatch(
            Long seasonId,
            String source
    ) {

        return jdbcClient.sql("""
                SELECT
                    id,
                    COALESCE(
                        source_observed_at,
                        completed_at,
                        started_at
                    ) AS source_observed_at

                FROM import_batches

                WHERE season_id = :seasonId
                  AND source = :source
                  AND status = 'COMPLETED'
                  AND LOWER(original_filename)
                      NOT LIKE '%test%'

                ORDER BY
                    completed_at DESC,
                    id DESC

                LIMIT 1
                """)
                .param("seasonId", seasonId)
                .param("source", source)
                .query((resultSet, rowNumber) ->
                        new BatchSnapshot(
                                resultSet.getLong("id"),
                                resultSet.getObject(
                                        "source_observed_at",
                                        OffsetDateTime.class
                                )
                        )
                )
                .optional()
                .orElseThrow(() ->
                        new ConflictException(
                                "Nessun import completato per "
                                        + source
                        )
                );
    }

    private Optional<BatchSnapshot>
            latestOptionalProductionBatch(
                    Long seasonId,
                    String source
            ) {

        return jdbcClient.sql("""
                SELECT
                    id,
                    COALESCE(
                        source_observed_at,
                        completed_at,
                        started_at
                    ) AS source_observed_at

                FROM import_batches

                WHERE season_id = :seasonId
                  AND source = :source
                  AND status = 'COMPLETED'
                  AND LOWER(original_filename)
                      NOT LIKE '%test%'

                ORDER BY
                    completed_at DESC,
                    id DESC

                LIMIT 1
                """)
                .param("seasonId", seasonId)
                .param("source", source)
                .query((resultSet, rowNumber) ->
                        new BatchSnapshot(
                                resultSet.getLong("id"),
                                resultSet.getObject(
                                        "source_observed_at",
                                        OffsetDateTime.class
                                )
                        )
                )
                .optional();
    }
    private Long findClubId(
            String clubName
    ) {

        if (!isNotBlank(clubName)) {
            throw new ConflictException(
                    "Squadra Fantacalcio mancante "
                            + "durante la riconciliazione"
            );
        }

        return jdbcClient.sql("""
                SELECT id
                FROM clubs
                WHERE LOWER(name) =
                      LOWER(:clubName)
                   OR LOWER(short_name) =
                      LOWER(:clubName)
                   OR LOWER(slug) =
                      LOWER(:clubName)
                ORDER BY id
                LIMIT 1
                """)
                .param("clubName", clubName)
                .query(Long.class)
                .optional()
                .orElseThrow(() ->
                        new ConflictException(
                                "Squadra non trovata: "
                                        + clubName
                        )
                );
    }

    private Long createPlayer(
            String displayName,
            String normalizedName
    ) {

        return jdbcClient.sql("""
                INSERT INTO players (
                    last_name,
                    display_name,
                    normalized_name,
                    active
                )
                VALUES (
                    :displayName,
                    :displayName,
                    :normalizedName,
                    TRUE
                )
                RETURNING id
                """)
                .param(
                        "displayName",
                        displayName
                )
                .param(
                        "normalizedName",
                        normalizedName
                )
                .query(Long.class)
                .single();
    }

    private void updatePlayer(
            Long playerId,
            String displayName,
            String normalizedName
    ) {

        jdbcClient.sql("""
                UPDATE players
                SET display_name = :displayName,
                    last_name = :displayName,
                    normalized_name = :normalizedName,
                    active = TRUE,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :playerId
                """)
                .param(
                        "displayName",
                        displayName
                )
                .param(
                        "normalizedName",
                        normalizedName
                )
                .param(
                        "playerId",
                        playerId
                )
                .update();
    }
    private void enforceLatestFantacalcioOperationalRegistrations(
            Long seasonId,
            Long fantacalcioBatchId,
            OffsetDateTime snapshotObservedAt
    ) {

        jdbcClient.sql("""
                UPDATE player_registrations registration
                SET
                    club_id = target_club.id,
                    left_at = NULL,
                    registration_status = 'ACTIVE',
                    updated_at = CURRENT_TIMESTAMP

                FROM player_source_mappings mapping

                JOIN player_import_rows import_row
                    ON import_row.external_id = mapping.external_id
                   AND import_row.batch_id = :batchId
                   AND import_row.valid = TRUE

                JOIN clubs target_club
                    ON (
                        LOWER(target_club.name) = LOWER(import_row.club_name)
                        OR LOWER(target_club.short_name) = LOWER(import_row.club_name)
                        OR LOWER(target_club.slug) = LOWER(import_row.normalized_club_name)
                    )

                JOIN season_clubs membership
                    ON membership.club_id = target_club.id
                   AND membership.season_id = :seasonId

                WHERE registration.player_id = mapping.player_id
                  AND registration.season_id = :seasonId
                  AND mapping.source = 'FANTACALCIO'
                  AND mapping.verified = TRUE

                  AND LOWER(
                        COALESCE(
                            import_row.raw_data ->> 'out_of_game',
                            'false'
                        )
                      ) <> 'true'

                  AND NOT EXISTS (
                      SELECT 1
                      FROM reconciliation_review_items review
                      WHERE review.season_id = :seasonId
                        AND review.fantacalcio_external_id = mapping.external_id
                        AND review.review_status = 'EXCLUDED'
                  )

                  AND NOT EXISTS (
                      SELECT 1
                      FROM player_transfer_claims claim
                      WHERE claim.player_id = mapping.player_id
                        AND claim.season_id = :seasonId
                        AND claim.to_club_id IS NULL
                        AND claim.verification_status = 'OFFICIAL'
                        AND claim.source IN (
                            'FIGC',
                            'LEGA_SERIE_A',
                            'OFFICIAL_CLUB'
                        )
                        AND :snapshotObservedAt IS NOT NULL
                        AND COALESCE(
                            claim.source_published_at,
                            claim.verified_at,
                            claim.observed_at
                        ) > :snapshotObservedAt
                  )
                """)
                .param("batchId", fantacalcioBatchId)
                .param("seasonId", seasonId)
                .param(
                        "snapshotObservedAt",
                        snapshotObservedAt,
                        Types.TIMESTAMP_WITH_TIMEZONE
                )
                .update();
    }

    private void saveRegistration(
            Long playerId,
            Long clubId,
            Long seasonId
    ) {

        jdbcClient.sql("""
                INSERT INTO player_registrations (
                    player_id,
                    club_id,
                    season_id,
                    left_at,
                    registration_status
                )
                VALUES (
                    :playerId,
                    :clubId,
                    :seasonId,
                    NULL,
                    'ACTIVE'
                )
                ON CONFLICT (
                    player_id,
                    season_id
                )
                DO UPDATE SET
                    club_id = EXCLUDED.club_id,
                    left_at = NULL,
                    registration_status = 'ACTIVE',
                    updated_at = CURRENT_TIMESTAMP
                """)
                .param("playerId", playerId)
                .param("clubId", clubId)
                .param("seasonId", seasonId)
                .update();
    }

    private void preserveReleasedRegistration(
            Long playerId,
            Long seasonId
    ) {

        jdbcClient.sql("""
                UPDATE player_registrations
                SET registration_status = 'RELEASED',
                    updated_at = CURRENT_TIMESTAMP
                WHERE player_id = :playerId
                  AND season_id = :seasonId
                  AND registration_status <> 'RELEASED'
                """)
                .param("playerId", playerId)
                .param("seasonId", seasonId)
                .update();
    }

    private void saveMarketValue(
            Long playerId,
            Long seasonId,
            String source,
            java.math.BigDecimal quotation,
            java.math.BigDecimal fantasyMarketValue
    ) {

        if (quotation == null
                && fantasyMarketValue == null) {
            return;
        }

        jdbcClient.sql("""
                INSERT INTO player_market_values (
                    player_id,
                    season_id,
                    source,
                    quotation,
                    fantasy_market_value
                )
                VALUES (
                    :playerId,
                    :seasonId,
                    :source,
                    :quotation,
                    :fantasyMarketValue
                )
                ON CONFLICT (
                    player_id,
                    season_id,
                    source
                )
                DO UPDATE SET
                    quotation =
                        EXCLUDED.quotation,
                    fantasy_market_value =
                        EXCLUDED.fantasy_market_value,
                    updated_at =
                        CURRENT_TIMESTAMP
                """)
                .param("playerId", playerId)
                .param("seasonId", seasonId)
                .param("source", source)
                .param(
                        "quotation",
                        quotation,
                        Types.NUMERIC
                )
                .param(
                        "fantasyMarketValue",
                        fantasyMarketValue,
                        Types.NUMERIC
                )
                .update();
    }

    private void saveSourceIfPresent(
            Long playerId,
            Long seasonId,
            String source,
            String externalId,
            String externalName,
            String role
    ) {

        if (!isNotBlank(externalName)) {
            return;
        }

        saveSourceMapping(
                playerId,
                source,
                externalId,
                externalName
        );

        if (isNotBlank(role)) {

            saveRole(
                    playerId,
                    seasonId,
                    source,
                    role
            );
        }
    }

    private void saveSourceMapping(
            Long playerId,
            String source,
            String externalId,
            String externalName
    ) {

        Optional<Map<String, Object>> existingMapping;

        if (isNotBlank(externalId)) {

            existingMapping =
                    findMappingByExternalId(
                            source,
                            externalId
                    );

        } else {

            existingMapping =
                    findUniqueMappingByExternalName(
                            source,
                            externalName
                    );
        }

        if (existingMapping.isPresent()) {

            Long existingPlayerId =
                    numberValue(
                            existingMapping.get(),
                            "player_id"
                    );

            if (!existingPlayerId.equals(
                    playerId
            )) {

                throw new ConflictException(
                        "Il mapping "
                                + source
                                + " ÃƒÂ¨ giÃƒÂ  associato "
                                + "a un altro giocatore: "
                                + externalName
                );
            }

            Long mappingId =
                    numberValue(
                            existingMapping.get(),
                            "id"
                    );

            jdbcClient.sql("""
                    UPDATE player_source_mappings
                    SET external_id = :externalId,
                        external_name = :externalName,
                        verified = TRUE,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = :mappingId
                    """)
                    .param(
                            "externalId",
                            externalId,
                            Types.VARCHAR
                    )
                    .param(
                            "externalName",
                            externalName
                    )
                    .param(
                            "mappingId",
                            mappingId
                    )
                    .update();

            return;
        }

        jdbcClient.sql("""
                INSERT INTO player_source_mappings (
                    player_id,
                    source,
                    external_id,
                    external_name,
                    verified
                )
                VALUES (
                    :playerId,
                    :source,
                    :externalId,
                    :externalName,
                    TRUE
                )
                """)
                .param("playerId", playerId)
                .param("source", source)
                .param(
                        "externalId",
                        externalId,
                        Types.VARCHAR
                )
                .param(
                        "externalName",
                        externalName
                )
                .update();
    }

    private Optional<Map<String, Object>>
            findMappingByExternalId(
                    String source,
                    String externalId
            ) {

        if (!isNotBlank(externalId)) {
            return Optional.empty();
        }

        return jdbcClient.sql("""
                SELECT id, player_id
                FROM player_source_mappings
                WHERE source = :source
                  AND external_id = :externalId
                ORDER BY id
                LIMIT 1
                """)
                .param("source", source)
                .param(
                        "externalId",
                        externalId
                )
                .query()
                .listOfRows()
                .stream()
                .findFirst();
    }

    private Optional<Map<String, Object>>
            findUniqueMappingByExternalName(
                    String source,
                    String externalName
            ) {

        List<Map<String, Object>> mappings =
                jdbcClient.sql("""
                        SELECT id, player_id
                        FROM player_source_mappings
                        WHERE source = :source
                          AND external_name = :externalName
                        ORDER BY id
                        LIMIT 2
                        """)
                        .param("source", source)
                        .param(
                                "externalName",
                                externalName
                        )
                        .query()
                        .listOfRows();

        if (mappings.size() == 1) {
            return Optional.of(
                    mappings.getFirst()
            );
        }

        return Optional.empty();
    }

    private void saveRole(
            Long playerId,
            Long seasonId,
            String source,
            String role
    ) {

        jdbcClient.sql("""
                INSERT INTO player_roles (
                    player_id,
                    season_id,
                    source,
                    fantasy_role,
                    primary_role
                )
                VALUES (
                    :playerId,
                    :seasonId,
                    :source,
                    :role,
                    TRUE
                )
                ON CONFLICT (
                    player_id,
                    season_id,
                    source
                )
                DO UPDATE SET
                    fantasy_role =
                        EXCLUDED.fantasy_role,
                    primary_role = TRUE
                """)
                .param("playerId", playerId)
                .param("seasonId", seasonId)
                .param("source", source)
                .param("role", role)
                .update();
    }

    private void saveReconciliationResult(
            Long seasonId,
            Long playerId,
            ResolvedPlayerIdentity identity,
            ClubResolution clubResolution
    ) {

        jdbcClient.sql("""
                INSERT INTO player_reconciliation_results (
                    season_id,
                    player_id,
                    normalized_name,
                    normalized_club_name,
                    sources_count,
                    identity_resolution_method,
                    club_resolution_source,
                    club_verified_at
                )
                VALUES (
                    :seasonId,
                    :playerId,
                    :normalizedName,
                    :normalizedClubName,
                    :sourcesCount,
                    :identityResolutionMethod,
                    :clubResolutionSource,
                    :clubVerifiedAt
                )
                """)
                .param("seasonId", seasonId)
                .param("playerId", playerId)
                .param(
                        "normalizedName",
                        identity.normalizedName()
                )
                .param(
                        "normalizedClubName",
                        clubResolution
                                .normalizedClubName()
                )
                .param(
                        "sourcesCount",
                        identity.sourcesCount()
                )
                .param(
                        "identityResolutionMethod",
                        identity
                                .identityResolutionMethod()
                )
                .param(
                        "clubResolutionSource",
                        clubResolution.source()
                )
                .param(
                        "clubVerifiedAt",
                        clubResolution.verifiedAt(),
                        Types.TIMESTAMP_WITH_TIMEZONE
                )
                .update();
    }

    private void markReconciliationStarted(
            long batchId
    ) {

        jdbcClient.sql("""
                UPDATE import_batches
                SET reconciliation_started_at =
                        CURRENT_TIMESTAMP,
                    reconciliation_completed_at = NULL,
                    reconciled_rows = 0
                WHERE id = :batchId
                """)
                .param("batchId", batchId)
                .update();
    }

    private void markReconciliationCompleted(
            long batchId,
            int reconciledRows
    ) {

        jdbcClient.sql("""
                UPDATE import_batches
                SET reconciliation_completed_at =
                        CURRENT_TIMESTAMP,
                    reconciled_rows =
                        :reconciledRows
                WHERE id = :batchId
                """)
                .param(
                        "reconciledRows",
                        reconciledRows
                )
                .param(
                        "batchId",
                        batchId
                )
                .update();
    }

    private String firstNonBlank(
            String... values
    ) {

        for (String value : values) {
            if (isNotBlank(value)) {
                return value.trim();
            }
        }

        throw new ConflictException(
                "Nessun valore valido disponibile "
                        + "durante la riconciliazione"
        );
    }

    private String normalizeClub(
            String club
    ) {

        if (!isNotBlank(club)) {
            return null;
        }

        return club
                .trim()
                .toLowerCase();
    }

    @SafeVarargs
    private final <T> T firstNonNull(
            T... values
    ) {

        for (T value : values) {
            if (value != null) {
                return value;
            }
        }

        return null;
    }

    private boolean isNotBlank(
            String value
    ) {
        return value != null
                && !value.isBlank();
    }

    private Long numberValue(
            Map<String, Object> row,
            String key
    ) {
        return ((Number) row.get(key))
                .longValue();
    }

    private Long nullableLong(
            java.sql.ResultSet resultSet,
            String column
    ) throws java.sql.SQLException {

        long value =
                resultSet.getLong(column);

        return resultSet.wasNull()
                ? null
                : value;
    }

    private record BatchSnapshot(
            long id,
            OffsetDateTime sourceObservedAt
    ) {
    }

    private record OfficialTransferClaim(
            Long toClubId,
            String toClubName,
            String source,
            OffsetDateTime sourcePublishedAt,
            OffsetDateTime observedAt,
            OffsetDateTime verifiedAt
    ) {
    }

    private record ClubResolution(
            Long clubId,
            String clubName,
            String normalizedClubName,
            String source,
            OffsetDateTime verifiedAt,
            boolean leftSerieA
    ) {
    }
}