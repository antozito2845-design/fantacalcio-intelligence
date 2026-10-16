package it.fantacalciointelligence.backend.transfer.detection;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.common.ConflictException;
import it.fantacalciointelligence.backend.common.ResourceNotFoundException;

@Service
public class TransferDetectionService {

    private final JdbcClient jdbcClient;

    public TransferDetectionService(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional
    public TransferDetectionResponse detectLatest(
            String season
    ) {

        long fantacalcioBatchId =
                requireLatestBatch(
                        season,
                        "FANTACALCIO"
                );

        Long gazzettaBatchId =
                optionalLatestBatch(
                        season,
                        "GAZZETTA"
                );

        Long fantamarktBatchId =
                optionalLatestBatch(
                        season,
                        "FANTAMARKT"
                );

        return detect(
                fantacalcioBatchId,
                gazzettaBatchId,
                fantamarktBatchId
        );
    }

    @Transactional
    public TransferDetectionResponse detect(
            long fantacalcioBatchId,
            Long gazzettaBatchId,
            Long fantamarktBatchId
    ) {

        BatchInfo fantacalcio =
                batchInfo(
                        fantacalcioBatchId,
                        "FANTACALCIO"
                );

        if (gazzettaBatchId != null) {
            ensureSameSeason(
                    fantacalcio,
                    batchInfo(
                            gazzettaBatchId,
                            "GAZZETTA"
                    )
            );
        }

        if (fantamarktBatchId != null) {
            ensureSameSeason(
                    fantacalcio,
                    batchInfo(
                            fantamarktBatchId,
                            "FANTAMARKT"
                    )
            );
        }

        List<TransferCandidate> candidates =
                loadClubChangeCandidates(
                        fantacalcio
                );

        List<SerieAExitCandidate> exitCandidates =
                loadSerieAExitCandidates(
                        fantacalcio
                );

        int createdClaims = 0;
        int corroboratedClaims = 0;
        int existingOpenClaims = 0;
        int staleClaimsSkipped = 0;

        List<Long> createdClaimIds =
                new ArrayList<>();

        for (TransferCandidate candidate
                : candidates) {

            if (!lockAndMatchCurrentRegistration(candidate)) {
                staleClaimsSkipped++;
                continue;
            }

            if (hasNewerOfficialEvidence(
                    candidate,
                    fantacalcio.observedAt()
            )) {
                staleClaimsSkipped++;
                continue;
            }

            if (hasExistingOpenClaim(
                    candidate
            )) {
                existingOpenClaims++;
                continue;
            }

            boolean corroborated =
                    isCorroborated(
                            candidate,
                            gazzettaBatchId,
                            "GAZZETTA"
                    )
                    || isCorroborated(
                            candidate,
                            fantamarktBatchId,
                            "FANTAMARKT"
                    );

            String status =
                    corroborated
                            ? "PENDING_VERIFICATION"
                            : "DETECTED";

            Long claimId =
                    insertDetectedClaim(
                            candidate,
                            fantacalcio,
                            status
                    );

            createdClaims++;
            createdClaimIds.add(claimId);

            if (corroborated) {
                corroboratedClaims++;
            }
        }

        for (SerieAExitCandidate candidate : exitCandidates) {

            if (!lockAndMatchCurrentRegistration(candidate)) {
                staleClaimsSkipped++;
                continue;
            }

            if (hasNewerOfficialEvidence(
                    candidate.playerId(),
                    candidate.seasonId(),
                    fantacalcio.observedAt()
            )) {
                staleClaimsSkipped++;
                continue;
            }

            if (hasExistingOpenExitClaim(candidate)) {
                existingOpenClaims++;
                continue;
            }

            Long claimId =
                    insertDetectedExitClaim(
                            candidate,
                            fantacalcio
                    );

            createdClaims++;
            createdClaimIds.add(claimId);
        }

        int missingFromFantacalcio =
                countMissingFromFantacalcio(
                        fantacalcio
                );

        return new TransferDetectionResponse(
                fantacalcio.season(),
                fantacalcio.id(),
                gazzettaBatchId,
                fantamarktBatchId,
                candidates.size(),
                exitCandidates.size(),
                createdClaims,
                corroboratedClaims,
                existingOpenClaims,
                staleClaimsSkipped,
                missingFromFantacalcio,
                List.copyOf(createdClaimIds)
        );
    }

    private List<TransferCandidate>
            loadClubChangeCandidates(
                    BatchInfo batch
            ) {

        return jdbcClient.sql("""
                SELECT DISTINCT
                    mapping.player_id,
                    player.display_name AS player_name,

                    registration.club_id
                        AS from_club_id,

                    current_club.name
                        AS from_club_name,

                    target_club.id
                        AS to_club_id,

                    target_club.name
                        AS to_club_name

                FROM player_import_rows import_row

                JOIN player_source_mappings mapping
                    ON mapping.source = 'FANTACALCIO'
                    AND mapping.external_id =
                        import_row.external_id

                JOIN players player
                    ON player.id =
                        mapping.player_id

                JOIN player_registrations registration
                    ON registration.player_id =
                        player.id
                    AND registration.season_id =
                        :seasonId
                    AND registration.registration_status =
                        'ACTIVE'

                JOIN clubs current_club
                    ON current_club.id =
                        registration.club_id

                JOIN LATERAL (
                    SELECT
                        club.id,
                        club.name

                    FROM clubs club

                    JOIN season_clubs membership
                        ON membership.club_id = club.id
                        AND membership.season_id = :seasonId

                    WHERE LOWER(club.name) =
                            LOWER(import_row.club_name)

                       OR LOWER(club.short_name) =
                            LOWER(import_row.club_name)

                       OR LOWER(club.slug) =
                            LOWER(
                                import_row
                                    .normalized_club_name
                            )

                    ORDER BY club.id
                    LIMIT 1
                ) target_club
                    ON TRUE

                WHERE import_row.batch_id = :batchId
                  AND import_row.valid = TRUE
                  AND player.active = TRUE
                  AND target_club.id <>
                        registration.club_id

                ORDER BY mapping.player_id
                """)
                .param(
                        "seasonId",
                        batch.seasonId()
                )
                .param(
                        "batchId",
                        batch.id()
                )
                .query((resultSet, rowNumber) ->
                        new TransferCandidate(
                                resultSet.getLong(
                                        "player_id"
                                ),
                                resultSet.getString(
                                        "player_name"
                                ),
                                batch.seasonId(),
                                resultSet.getLong(
                                        "from_club_id"
                                ),
                                resultSet.getString(
                                        "from_club_name"
                                ),
                                resultSet.getLong(
                                        "to_club_id"
                                ),
                                resultSet.getString(
                                        "to_club_name"
                                )
                        )
                )
                .list();
    }

    private List<SerieAExitCandidate> loadSerieAExitCandidates(
            BatchInfo batch
    ) {

        return jdbcClient.sql("""
                SELECT DISTINCT
                    mapping.player_id,
                    player.display_name AS player_name,
                    registration.club_id AS from_club_id,
                    current_club.name AS from_club_name

                FROM player_import_rows import_row

                JOIN player_source_mappings mapping
                    ON mapping.source = 'FANTACALCIO'
                   AND mapping.external_id =
                       import_row.external_id

                JOIN players player
                    ON player.id = mapping.player_id

                JOIN player_registrations registration
                    ON registration.player_id = player.id
                   AND registration.season_id = :seasonId
                   AND registration.registration_status = 'ACTIVE'

                JOIN clubs current_club
                    ON current_club.id = registration.club_id

                WHERE import_row.batch_id = :batchId
                  AND import_row.valid = TRUE
                  AND player.active = TRUE
                  AND LOWER(
                        COALESCE(
                            import_row.raw_data ->> 'out_of_game',
                            'false'
                        )
                      ) = 'true'

                ORDER BY mapping.player_id
                """)
                .param("seasonId", batch.seasonId())
                .param("batchId", batch.id())
                .query((resultSet, rowNumber) ->
                        new SerieAExitCandidate(
                                resultSet.getLong("player_id"),
                                resultSet.getString("player_name"),
                                batch.seasonId(),
                                resultSet.getLong("from_club_id"),
                                resultSet.getString("from_club_name")
                        )
                )
                .list();
    }

    private boolean lockAndMatchCurrentRegistration(
            SerieAExitCandidate candidate
    ) {

        return jdbcClient.sql("""
                SELECT club_id, registration_status
                FROM player_registrations
                WHERE player_id = :playerId
                  AND season_id = :seasonId
                FOR UPDATE
                """)
                .param("playerId", candidate.playerId())
                .param("seasonId", candidate.seasonId())
                .query((resultSet, rowNumber) ->
                        resultSet.getLong("club_id")
                                == candidate.fromClubId()
                        && "ACTIVE".equals(
                                resultSet.getString(
                                        "registration_status"
                                )
                        )
                )
                .optional()
                .orElse(false);
    }

    private boolean hasExistingOpenExitClaim(
            SerieAExitCandidate candidate
    ) {

        int count =
                jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM player_transfer_claims
                        WHERE player_id = :playerId
                          AND season_id = :seasonId
                          AND from_club_id = :fromClubId
                          AND to_club_id IS NULL
                          AND verification_status IN (
                              'DETECTED',
                              'PENDING_VERIFICATION'
                          )
                        """)
                        .param(
                                "playerId",
                                candidate.playerId()
                        )
                        .param(
                                "seasonId",
                                candidate.seasonId()
                        )
                        .param(
                                "fromClubId",
                                candidate.fromClubId()
                        )
                        .query(Integer.class)
                        .single();

        return count > 0;
    }

    private Long insertDetectedExitClaim(
            SerieAExitCandidate candidate,
            BatchInfo batch
    ) {

        String detectionKey =
                "AUTO:"
                        + batch.id()
                        + ":EXIT:"
                        + candidate.playerId()
                        + ":"
                        + candidate.fromClubId();

        String sourceReference =
                batch.sourceReference() == null
                        || batch.sourceReference().isBlank()
                        ? "import-batch:" + batch.id()
                        : batch.sourceReference();

        return jdbcClient.sql("""
                INSERT INTO player_transfer_claims (
                    player_id,
                    season_id,
                    from_club_id,
                    to_club_id,
                    destination_club_name,
                    source,
                    source_reference,
                    source_published_at,
                    observed_at,
                    effective_at,
                    verification_status,
                    detection_key
                )
                VALUES (
                    :playerId,
                    :seasonId,
                    :fromClubId,
                    NULL,
                    NULL,
                    'FANTACALCIO',
                    :sourceReference,
                    NULL,
                    :observedAt,
                    NULL,
                    'DETECTED',
                    :detectionKey
                )
                RETURNING id
                """)
                .param(
                        "playerId",
                        candidate.playerId()
                )
                .param(
                        "seasonId",
                        candidate.seasonId()
                )
                .param(
                        "fromClubId",
                        candidate.fromClubId()
                )
                .param(
                        "sourceReference",
                        sourceReference
                )
                .param(
                        "observedAt",
                        batch.observedAt(),
                        Types.TIMESTAMP_WITH_TIMEZONE
                )
                .param(
                        "detectionKey",
                        detectionKey
                )
                .query(Long.class)
                .single();
    }
    private boolean isCorroborated(
            TransferCandidate candidate,
            Long batchId,
            String source
    ) {

        if (batchId == null) {
            return false;
        }

        int count =
                jdbcClient.sql("""
                        SELECT COUNT(*)

                        FROM player_source_mappings mapping

                        JOIN player_import_rows import_row
                            ON import_row.external_id =
                                mapping.external_id

                        WHERE mapping.player_id = :playerId
                          AND mapping.source = :source
                          AND import_row.batch_id = :batchId
                          AND import_row.valid = TRUE

                          AND LOWER(import_row.club_name) =
                              LOWER(:targetClub)
                        """)
                        .param(
                                "playerId",
                                candidate.playerId()
                        )
                        .param(
                                "source",
                                source
                        )
                        .param(
                                "batchId",
                                batchId
                        )
                        .param(
                                "targetClub",
                                candidate.toClubName()
                        )
                        .query(Integer.class)
                        .single();

        return count > 0;
    }

    private boolean hasExistingOpenClaim(
            TransferCandidate candidate
    ) {

        int count =
                jdbcClient.sql("""
                        SELECT COUNT(*)

                        FROM player_transfer_claims

                        WHERE player_id = :playerId
                          AND season_id = :seasonId
                          AND from_club_id = :fromClubId
                          AND to_club_id = :toClubId
                          AND verification_status IN (
                              'DETECTED',
                              'PENDING_VERIFICATION'
                          )
                        """)
                        .param(
                                "playerId",
                                candidate.playerId()
                        )
                        .param(
                                "seasonId",
                                candidate.seasonId()
                        )
                        .param(
                                "fromClubId",
                                candidate.fromClubId()
                        )
                        .param(
                                "toClubId",
                                candidate.toClubId()
                        )
                        .query(Integer.class)
                        .single();

        return count > 0;
    }

    private boolean lockAndMatchCurrentRegistration(
            TransferCandidate candidate
    ) {
        return jdbcClient.sql("""
                SELECT club_id, registration_status
                FROM player_registrations
                WHERE player_id = :playerId
                  AND season_id = :seasonId
                FOR UPDATE
                """)
                .param("playerId", candidate.playerId())
                .param("seasonId", candidate.seasonId())
                .query((resultSet, rowNumber) ->
                        resultSet.getLong("club_id") == candidate.fromClubId()
                        && "ACTIVE".equals(
                                resultSet.getString("registration_status")
                        )
                )
                .optional()
                .orElse(false);
    }

    private boolean hasNewerOfficialEvidence(
            TransferCandidate candidate,
            OffsetDateTime snapshotAt
    ) {
        return hasNewerOfficialEvidence(
                candidate.playerId(),
                candidate.seasonId(),
                snapshotAt
        );
    }

    private boolean hasNewerOfficialEvidence(
            Long playerId,
            Long seasonId,
            OffsetDateTime snapshotAt
    ) {

        if (snapshotAt == null) {
            return false;
        }

        int count =
                jdbcClient.sql("""
                        SELECT COUNT(*)

                        FROM player_transfer_claims

                        WHERE player_id = :playerId
                          AND season_id = :seasonId
                          AND verification_status = 'OFFICIAL'
                          AND verification_source IN (
                              'FIGC',
                              'LEGA_SERIE_A',
                              'OFFICIAL_CLUB'
                          )
                          AND COALESCE(
                              verification_published_at,
                              verified_at,
                              observed_at
                          ) > :snapshotAt
                        """)
                        .param(
                                "playerId",
                                playerId
                        )
                        .param(
                                "seasonId",
                                seasonId
                        )
                        .param(
                                "snapshotAt",
                                snapshotAt
                        )
                        .query(Integer.class)
                        .single();

        return count > 0;
    }

    private Long insertDetectedClaim(
            TransferCandidate candidate,
            BatchInfo batch,
            String status
    ) {

        String detectionKey =
                "AUTO:"
                        + batch.id()
                        + ":"
                        + candidate.playerId()
                        + ":"
                        + candidate.fromClubId()
                        + ":"
                        + candidate.toClubId();

        String sourceReference =
                batch.sourceReference() == null
                        || batch.sourceReference().isBlank()
                        ? "import-batch:" + batch.id()
                        : batch.sourceReference();

        return jdbcClient.sql("""
                INSERT INTO player_transfer_claims (
                    player_id,
                    season_id,
                    from_club_id,
                    to_club_id,
                    destination_club_name,
                    source,
                    source_reference,
                    source_published_at,
                    observed_at,
                    effective_at,
                    verification_status,
                    detection_key
                )
                VALUES (
                    :playerId,
                    :seasonId,
                    :fromClubId,
                    :toClubId,
                    :destinationClubName,
                    'FANTACALCIO',
                    :sourceReference,
                    NULL,
                    :observedAt,
                    NULL,
                    :status,
                    :detectionKey
                )
                RETURNING id
                """)
                .param(
                        "playerId",
                        candidate.playerId()
                )
                .param(
                        "seasonId",
                        candidate.seasonId()
                )
                .param(
                        "fromClubId",
                        candidate.fromClubId()
                )
                .param(
                        "toClubId",
                        candidate.toClubId()
                )
                .param(
                        "destinationClubName",
                        candidate.toClubName()
                )
                .param(
                        "sourceReference",
                        sourceReference
                )
                .param(
                        "observedAt",
                        batch.observedAt(),
                        Types.TIMESTAMP_WITH_TIMEZONE
                )
                .param(
                        "status",
                        status
                )
                .param(
                        "detectionKey",
                        detectionKey
                )
                .query(Long.class)
                .single();
    }

    private int countMissingFromFantacalcio(
            BatchInfo batch
    ) {

        return jdbcClient.sql("""
                SELECT COUNT(DISTINCT player.id)

                FROM players player

                JOIN player_registrations registration
                    ON registration.player_id =
                        player.id
                    AND registration.season_id =
                        :seasonId
                    AND registration.registration_status =
                        'ACTIVE'

                JOIN player_source_mappings mapping
                    ON mapping.player_id = player.id
                    AND mapping.source = 'FANTACALCIO'
                    AND mapping.external_id IS NOT NULL

                WHERE player.active = TRUE

                  AND NOT EXISTS (
                      SELECT 1

                      FROM player_import_rows import_row

                      WHERE import_row.batch_id =
                                :batchId
                        AND import_row.valid = TRUE
                        AND import_row.external_id =
                                mapping.external_id
                  )
                """)
                .param(
                        "seasonId",
                        batch.seasonId()
                )
                .param(
                        "batchId",
                        batch.id()
                )
                .query(Integer.class)
                .single();
    }

    private long requireLatestBatch(
            String season,
            String source
    ) {

        Long batchId =
                optionalLatestBatch(
                        season,
                        source
                );

        if (batchId == null) {
            throw new ResourceNotFoundException(
                    "Nessun batch COMPLETED "
                            + source
                            + " per "
                            + season
            );
        }

        return batchId;
    }

    private Long optionalLatestBatch(
            String season,
            String source
    ) {

        return jdbcClient.sql("""
                SELECT batch.id

                FROM import_batches batch

                JOIN seasons season
                    ON season.id = batch.season_id

                WHERE season.code = :season
                  AND batch.source = :source
                  AND batch.status = 'COMPLETED'

                ORDER BY
                    COALESCE(
                        batch.source_observed_at,
                        batch.completed_at,
                        batch.started_at
                    ) DESC,
                    batch.id DESC

                LIMIT 1
                """)
                .param("season", season)
                .param("source", source)
                .query(Long.class)
                .optional()
                .orElse(null);
    }

    private BatchInfo batchInfo(
            long batchId,
            String expectedSource
    ) {

        return jdbcClient.sql("""
                SELECT
                    batch.id,
                    season.id AS season_id,
                    season.code AS season,
                    batch.source,
                    batch.status,
                    batch.source_reference,

                    COALESCE(
                        batch.source_observed_at,
                        batch.completed_at,
                        batch.started_at
                    ) AS observed_at

                FROM import_batches batch

                JOIN seasons season
                    ON season.id = batch.season_id

                WHERE batch.id = :batchId
                """)
                .param("batchId", batchId)
                .query((resultSet, rowNumber) ->
                        new BatchInfo(
                                resultSet.getLong("id"),
                                resultSet.getLong(
                                        "season_id"
                                ),
                                resultSet.getString(
                                        "season"
                                ),
                                resultSet.getString(
                                        "source"
                                ),
                                resultSet.getString(
                                        "status"
                                ),
                                resultSet.getString(
                                        "source_reference"
                                ),
                                resultSet.getObject(
                                        "observed_at",
                                        OffsetDateTime.class
                                )
                        )
                )
                .optional()
                .map(batch -> {

                    if (!expectedSource.equals(
                            batch.source()
                    )) {
                        throw new ConflictException(
                                "Batch "
                                        + batchId
                                        + " atteso come "
                                        + expectedSource
                                        + " ma trovato "
                                        + batch.source()
                        );
                    }

                    if (!"COMPLETED".equals(
                            batch.status()
                    )) {
                        throw new ConflictException(
                                "Batch non completato: "
                                        + batchId
                        );
                    }

                    return batch;
                })
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Batch non trovato: "
                                        + batchId
                        )
                );
    }

    private void ensureSameSeason(
            BatchInfo reference,
            BatchInfo secondary
    ) {

        if (!reference.seasonId()
                .equals(secondary.seasonId())) {

            throw new ConflictException(
                    "I batch selezionati appartengono "
                            + "a stagioni diverse"
            );
        }
    }

    private record BatchInfo(
            Long id,
            Long seasonId,
            String season,
            String source,
            String status,
            String sourceReference,
            OffsetDateTime observedAt
    ) {
    }

    private record SerieAExitCandidate(
            Long playerId,
            String playerName,
            Long seasonId,
            Long fromClubId,
            String fromClubName
    ) {
    }

    private record TransferCandidate(
            Long playerId,
            String playerName,
            Long seasonId,
            Long fromClubId,
            String fromClubName,
            Long toClubId,
            String toClubName
    ) {
    }
}
