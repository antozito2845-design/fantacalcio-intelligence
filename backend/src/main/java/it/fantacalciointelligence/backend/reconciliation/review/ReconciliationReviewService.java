package it.fantacalciointelligence.backend.reconciliation.review;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.common.BadRequestException;
import it.fantacalciointelligence.backend.common.ConflictException;
import it.fantacalciointelligence.backend.common.ResourceNotFoundException;
import it.fantacalciointelligence.backend.reconciliation.preview.ReconciliationPreviewPlayer;
import it.fantacalciointelligence.backend.reconciliation.preview.ReconciliationPreviewResponse;
import it.fantacalciointelligence.backend.reconciliation.preview.ReconciliationPreviewService;

@Service
public class ReconciliationReviewService {

    private static final Set<String> SECONDARY_SOURCES =
            Set.of(
                    "GAZZETTA",
                    "FANTAMARKT"
            );

    private final JdbcClient jdbcClient;
    private final ReconciliationPreviewService previewService;

    public ReconciliationReviewService(
            JdbcClient jdbcClient,
            ReconciliationPreviewService previewService
    ) {
        this.jdbcClient = jdbcClient;
        this.previewService = previewService;
    }

    @Transactional
    public ReconciliationReviewSyncResponse sync(
            long fantacalcioBatchId,
            long gazzettaBatchId,
            Long fantamarktBatchId
    ) {

        BatchSeason batchSeason =
                validateBatchSeasons(
                        fantacalcioBatchId,
                        gazzettaBatchId,
                        fantamarktBatchId
                );

        ReconciliationPreviewResponse preview =
                previewService.preview(
                        fantacalcioBatchId,
                        gazzettaBatchId,
                        fantamarktBatchId,
                        true
                );

        int synchronizedItems = 0;

        Set<String> unresolvedFantacalcioExternalIds =
                new HashSet<>();

        for (ReconciliationPreviewPlayer player
                : preview.players()) {

            if (player.fantacalcioExternalId() == null
                    || player.fantacalcioExternalId()
                            .isBlank()) {
                continue;
            }

            unresolvedFantacalcioExternalIds.add(
                    player.fantacalcioExternalId()
            );

            synchronizedItems +=
                    upsert(
                            batchSeason.seasonId(),
                            fantacalcioBatchId,
                            gazzettaBatchId,
                            fantamarktBatchId,
                            player
                    );
        }

        autoResolveStaleOpenItems(
                batchSeason.seasonId(),
                fantacalcioBatchId,
                unresolvedFantacalcioExternalIds
        );

        int openItems =
                countStatus(
                        batchSeason.seasonCode(),
                        "OPEN"
                );

        return new ReconciliationReviewSyncResponse(
                batchSeason.seasonCode(),
                fantacalcioBatchId,
                gazzettaBatchId,
                fantamarktBatchId,
                preview.totalFantacalcioPlayers(),
                preview.unresolvedPlayers(),
                synchronizedItems,
                openItems
        );
    }

    @Transactional
    public ReconciliationReviewSyncResponse syncLatest(
            String season
    ) {

        String seasonCode =
                requireText(
                        season,
                        "Stagione obbligatoria"
                );

        long fantacalcioBatchId =
                findLatestBatch(
                        seasonCode,
                        "FANTACALCIO"
                );

        long gazzettaBatchId =
                findLatestBatch(
                        seasonCode,
                        "GAZZETTA"
                );

        Long fantamarktBatchId =
                findLatestOptionalBatch(
                        seasonCode,
                        "FANTAMARKT"
                );

        return sync(
                fantacalcioBatchId,
                gazzettaBatchId,
                fantamarktBatchId
        );
    }

    @Transactional(readOnly = true)
    public List<ReconciliationReviewItemResponse> findAll(
            String season,
            String status
    ) {

        String seasonCode =
                requireText(
                        season,
                        "Stagione obbligatoria"
                );

        String normalizedStatus =
                status == null || status.isBlank()
                        ? null
                        : status.trim().toUpperCase();

        if (normalizedStatus != null
                && !Set.of(
                        "OPEN",
                        "RESOLVED",
                        "IGNORED",
                        "EXCLUDED"
                ).contains(normalizedStatus)) {

            throw new BadRequestException(
                    "Stato review non supportato: "
                            + normalizedStatus
            );
        }

        StringBuilder sql =
                new StringBuilder("""
                        SELECT
                            review.id,
                            season.code AS season,
                            review.fantacalcio_batch_id,
                            review.gazzetta_batch_id,
                            review.fantamarkt_batch_id,
                            review.fantacalcio_external_id,
                            review.fantacalcio_name,
                            review.fantacalcio_club,
                            review.gazzetta_external_id,
                            review.gazzetta_name,
                            review.gazzetta_club,
                            review.fantamarkt_external_id,
                            review.fantamarkt_name,
                            review.fantamarkt_club,
                            review.review_status,
                            review.resolution_source,
                            review.resolution_external_id,
                            review.resolution_note,
                            review.exclusion_reason,
                            review.exclusion_note,
                            review.first_seen_at,
                            review.last_seen_at,
                            review.resolved_at,
                            review.excluded_at

                        FROM reconciliation_review_items review

                        JOIN seasons season
                            ON season.id = review.season_id

                        WHERE season.code = :season
                        """);

        if (normalizedStatus != null) {
            sql.append("""
                    AND review.review_status = :status
                    """);
        }

        sql.append("""
                ORDER BY
                    CASE review.review_status
                        WHEN 'OPEN' THEN 1
                        WHEN 'RESOLVED' THEN 2
                        ELSE 3
                    END,
                    review.last_seen_at DESC,
                    review.id DESC
                """);

        var query =
                jdbcClient.sql(sql.toString())
                        .param(
                                "season",
                                seasonCode
                        );

        if (normalizedStatus != null) {
            query = query.param(
                    "status",
                    normalizedStatus
            );
        }

        return query
                .query((resultSet, rowNumber) ->
                        mapResponse(resultSet)
                )
                .list();
    }

    @Transactional
    public ReconciliationReviewItemResponse resolveLink(
            Long reviewId,
            ReconciliationReviewResolveRequest request
    ) {

        if (request == null) {
            throw new BadRequestException(
                    "Richiesta di risoluzione mancante"
            );
        }

        String source =
                requireText(
                        request.secondarySource(),
                        "Fonte secondaria obbligatoria"
                ).toUpperCase();

        if (!SECONDARY_SOURCES.contains(source)) {
            throw new BadRequestException(
                    "Fonte secondaria non supportata: "
                            + source
            );
        }

        String externalId =
                requireText(
                        request.secondaryExternalId(),
                        "External ID secondario obbligatorio"
                );

        Map<String, Object> review =
                findReviewRow(reviewId);

        long secondaryBatchId =
                findSecondaryBatchId(
                        review,
                        source
                );

        ensureSecondaryExternalIdExists(
                secondaryBatchId,
                externalId
        );

        String fantacalcioExternalId =
                stringValue(
                        review,
                        "fantacalcio_external_id"
                );

        try {
            jdbcClient.sql("""
                    INSERT INTO verified_source_identity_links (
                        fantacalcio_external_id,
                        secondary_source,
                        secondary_external_id,
                        verification_method,
                        verified,
                        verification_note
                    )
                    VALUES (
                        :fantacalcioExternalId,
                        :secondarySource,
                        :secondaryExternalId,
                        'MANUAL_VERIFICATION',
                        TRUE,
                        :note
                    )
                    ON CONFLICT (
                        fantacalcio_external_id,
                        secondary_source
                    )
                    DO UPDATE SET
                        secondary_external_id =
                            EXCLUDED.secondary_external_id,
                        verification_method =
                            'MANUAL_VERIFICATION',
                        verified = TRUE,
                        verification_note =
                            EXCLUDED.verification_note,
                        updated_at =
                            CURRENT_TIMESTAMP
                    """)
                    .param(
                            "fantacalcioExternalId",
                            fantacalcioExternalId
                    )
                    .param(
                            "secondarySource",
                            source
                    )
                    .param(
                            "secondaryExternalId",
                            externalId
                    )
                    .param(
                            "note",
                            request.note(),
                            Types.VARCHAR
                    )
                    .update();

        } catch (DataIntegrityViolationException exception) {

            throw new ConflictException(
                    "L'identità secondaria "
                            + source
                            + "/"
                            + externalId
                            + " risulta già collegata "
                            + "a un altro giocatore",
                    exception
            );
        }

        jdbcClient.sql("""
                UPDATE reconciliation_review_items
                SET review_status = 'RESOLVED',
                    resolution_source = :source,
                    resolution_external_id = :externalId,
                    resolution_note = :note,
                    resolved_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :reviewId
                """)
                .param("source", source)
                .param(
                        "externalId",
                        externalId
                )
                .param(
                        "note",
                        request.note(),
                        Types.VARCHAR
                )
                .param(
                        "reviewId",
                        reviewId
                )
                .update();

        return findById(reviewId);
    }

    @Transactional
    public ReconciliationReviewItemResponse exclude(
            Long reviewId,
            ReconciliationReviewExcludeRequest request
    ) {

        if (request == null) {
            throw new BadRequestException(
                    "Richiesta di esclusione mancante"
            );
        }

        findReviewRow(reviewId);

        String reason =
                requireText(
                        request.reason(),
                        "Motivo esclusione obbligatorio"
                ).toUpperCase();

        if (!Set.of(
                "OUT_OF_SERIE_A"
        ).contains(reason)) {

            throw new BadRequestException(
                    "Motivo esclusione non supportato: "
                            + reason
            );
        }

        String note =
                requireText(
                        request.note(),
                        "Nota esclusione obbligatoria"
                );

        jdbcClient.sql("""
                UPDATE reconciliation_review_items
                SET review_status = 'EXCLUDED',
                    resolution_source = NULL,
                    resolution_external_id = NULL,
                    resolution_note = NULL,
                    resolved_at = NULL,
                    exclusion_reason = :reason,
                    exclusion_note = :note,
                    excluded_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :reviewId
                """)
                .param("reason", reason)
                .param("note", note)
                .param("reviewId", reviewId)
                .update();

        return findById(reviewId);
    }
    @Transactional
    public ReconciliationReviewItemResponse ignore(
            Long reviewId,
            String note
    ) {

        findReviewRow(reviewId);

        String reason =
                requireText(
                        note,
                        "Motivo obbligatorio"
                );

        jdbcClient.sql("""
                UPDATE reconciliation_review_items
                SET review_status = 'IGNORED',
                    resolution_source = NULL,
                    resolution_external_id = NULL,
                    resolution_note = :note,
                    resolved_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :reviewId
                """)
                .param("note", reason)
                .param("reviewId", reviewId)
                .update();

        return findById(reviewId);
    }

    @Transactional(readOnly = true)
    public ReconciliationReviewItemResponse findById(
            Long reviewId
    ) {

        return jdbcClient.sql("""
                SELECT
                    review.id,
                    season.code AS season,
                    review.fantacalcio_batch_id,
                    review.gazzetta_batch_id,
                    review.fantamarkt_batch_id,
                    review.fantacalcio_external_id,
                    review.fantacalcio_name,
                    review.fantacalcio_club,
                    review.gazzetta_external_id,
                    review.gazzetta_name,
                    review.gazzetta_club,
                    review.fantamarkt_external_id,
                    review.fantamarkt_name,
                    review.fantamarkt_club,
                    review.review_status,
                    review.resolution_source,
                    review.resolution_external_id,
                    review.resolution_note,
                            review.exclusion_reason,
                            review.exclusion_note,
                            review.first_seen_at,
                            review.last_seen_at,
                            review.resolved_at,
                            review.excluded_at

                FROM reconciliation_review_items review

                JOIN seasons season
                    ON season.id = review.season_id

                WHERE review.id = :reviewId
                """)
                .param("reviewId", reviewId)
                .query((resultSet, rowNumber) ->
                        mapResponse(resultSet)
                )
                .optional()
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Elemento review non trovato: "
                                        + reviewId
                        )
                );
    }

    private int upsert(
            Long seasonId,
            long fantacalcioBatchId,
            long gazzettaBatchId,
            Long fantamarktBatchId,
            ReconciliationPreviewPlayer player
    ) {

        return jdbcClient.sql("""
                INSERT INTO reconciliation_review_items (
                    season_id,
                    fantacalcio_batch_id,
                    gazzetta_batch_id,
                    fantamarkt_batch_id,
                    fantacalcio_external_id,
                    fantacalcio_name,
                    fantacalcio_club,
                    gazzetta_external_id,
                    gazzetta_name,
                    gazzetta_club,
                    fantamarkt_external_id,
                    fantamarkt_name,
                    fantamarkt_club,
                    review_status
                )
                VALUES (
                    :seasonId,
                    :fantacalcioBatchId,
                    :gazzettaBatchId,
                    :fantamarktBatchId,
                    :fantacalcioExternalId,
                    :fantacalcioName,
                    :fantacalcioClub,
                    :gazzettaExternalId,
                    :gazzettaName,
                    :gazzettaClub,
                    :fantamarktExternalId,
                    :fantamarktName,
                    :fantamarktClub,
                    'OPEN'
                )
                ON CONFLICT (
                    season_id,
                    fantacalcio_external_id
                )
                DO UPDATE SET
                    fantacalcio_batch_id =
                        EXCLUDED.fantacalcio_batch_id,
                    gazzetta_batch_id =
                        EXCLUDED.gazzetta_batch_id,
                    fantamarkt_batch_id =
                        EXCLUDED.fantamarkt_batch_id,
                    fantacalcio_name =
                        EXCLUDED.fantacalcio_name,
                    fantacalcio_club =
                        EXCLUDED.fantacalcio_club,
                    gazzetta_external_id =
                        EXCLUDED.gazzetta_external_id,
                    gazzetta_name =
                        EXCLUDED.gazzetta_name,
                    gazzetta_club =
                        EXCLUDED.gazzetta_club,
                    fantamarkt_external_id =
                        EXCLUDED.fantamarkt_external_id,
                    fantamarkt_name =
                        EXCLUDED.fantamarkt_name,
                    fantamarkt_club =
                        EXCLUDED.fantamarkt_club,
                    last_seen_at =
                        CURRENT_TIMESTAMP,
                    updated_at =
                        CURRENT_TIMESTAMP,
                    review_status =
                        CASE
                            WHEN reconciliation_review_items
                                     .review_status IN (
                                         'IGNORED',
                                         'EXCLUDED'
                                     )
                            THEN reconciliation_review_items
                                     .review_status
                            ELSE 'OPEN'
                        END,
                    resolved_at =
                        CASE
                            WHEN reconciliation_review_items
                                     .review_status = 'IGNORED'
                            THEN reconciliation_review_items
                                     .resolved_at
                            ELSE NULL
                        END
                """)
                .param("seasonId", seasonId)
                .param(
                        "fantacalcioBatchId",
                        fantacalcioBatchId
                )
                .param(
                        "gazzettaBatchId",
                        gazzettaBatchId
                )
                .param(
                        "fantamarktBatchId",
                        fantamarktBatchId,
                        Types.BIGINT
                )
                .param(
                        "fantacalcioExternalId",
                        player.fantacalcioExternalId()
                )
                .param(
                        "fantacalcioName",
                        player.fantacalcioName()
                )
                .param(
                        "fantacalcioClub",
                        player.fantacalcioClub(),
                        Types.VARCHAR
                )
                .param(
                        "gazzettaExternalId",
                        player.gazzettaExternalId(),
                        Types.VARCHAR
                )
                .param(
                        "gazzettaName",
                        player.gazzettaName(),
                        Types.VARCHAR
                )
                .param(
                        "gazzettaClub",
                        player.gazzettaClub(),
                        Types.VARCHAR
                )
                .param(
                        "fantamarktExternalId",
                        player.fantamarktExternalId(),
                        Types.VARCHAR
                )
                .param(
                        "fantamarktName",
                        player.fantamarktName(),
                        Types.VARCHAR
                )
                .param(
                        "fantamarktClub",
                        player.fantamarktClub(),
                        Types.VARCHAR
                )
                .update();
    }

    private BatchSeason validateBatchSeasons(
            long fantacalcioBatchId,
            long gazzettaBatchId,
            Long fantamarktBatchId
    ) {

        BatchSeason fantacalcio =
                batchSeason(
                        fantacalcioBatchId,
                        "FANTACALCIO"
                );

        BatchSeason gazzetta =
                batchSeason(
                        gazzettaBatchId,
                        "GAZZETTA"
                );

        if (!fantacalcio.seasonId()
                .equals(gazzetta.seasonId())) {

            throw new ConflictException(
                    "I batch Fantacalcio e Gazzetta "
                            + "appartengono a stagioni diverse"
            );
        }

        if (fantamarktBatchId != null) {

            BatchSeason fantamarkt =
                    batchSeason(
                            fantamarktBatchId,
                            "FANTAMARKT"
                    );

            if (!fantacalcio.seasonId()
                    .equals(fantamarkt.seasonId())) {

                throw new ConflictException(
                        "Il batch Fantamarkt appartiene "
                                + "a una stagione diversa"
                );
            }
        }

        return fantacalcio;
    }

    private BatchSeason batchSeason(
            long batchId,
            String expectedSource
    ) {

        return jdbcClient.sql("""
                SELECT
                    season.id AS season_id,
                    season.code AS season_code,
                    batch.source,
                    batch.status

                FROM import_batches batch

                JOIN seasons season
                    ON season.id = batch.season_id

                WHERE batch.id = :batchId
                """)
                .param("batchId", batchId)
                .query((resultSet, rowNumber) ->
                        new BatchSeason(
                                resultSet.getLong(
                                        "season_id"
                                ),
                                resultSet.getString(
                                        "season_code"
                                ),
                                resultSet.getString(
                                        "source"
                                ),
                                resultSet.getString(
                                        "status"
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
                                        + " non appartiene a "
                                        + expectedSource
                        );
                    }

                    if (!"COMPLETED".equals(
                            batch.status()
                    )) {
                        throw new ConflictException(
                                "Batch "
                                        + batchId
                                        + " non completato"
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

    private long findLatestBatch(
            String season,
            String source
    ) {

        return findLatestOptionalBatch(
                season,
                source
        ) == null
                ? throwMissingBatch(
                        season,
                        source
                )
                : findLatestOptionalBatch(
                        season,
                        source
                );
    }

    private long throwMissingBatch(
            String season,
            String source
    ) {
        throw new ResourceNotFoundException(
                "Nessun batch COMPLETED "
                        + source
                        + " per "
                        + season
        );
    }

    private Long findLatestOptionalBatch(
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

    private int autoResolveStaleOpenItems(
            Long seasonId,
            long fantacalcioBatchId,
            Set<String> unresolvedFantacalcioExternalIds
    ) {

        List<String> openExternalIds =
                jdbcClient.sql("""
                        SELECT review.fantacalcio_external_id

                        FROM reconciliation_review_items review

                        WHERE review.season_id = :seasonId
                          AND review.review_status = 'OPEN'

                          AND EXISTS (
                              SELECT 1
                              FROM player_import_rows row
                              WHERE row.batch_id = :fantacalcioBatchId
                                AND row.external_id =
                                    review.fantacalcio_external_id
                                AND row.valid = TRUE
                          )
                        """)
                        .param("seasonId", seasonId)
                        .param(
                                "fantacalcioBatchId",
                                fantacalcioBatchId
                        )
                        .query(String.class)
                        .list();

        List<String> resolvedExternalIds =
                openExternalIds.stream()
                        .filter(externalId ->
                                !unresolvedFantacalcioExternalIds
                                        .contains(externalId)
                        )
                        .toList();

        if (resolvedExternalIds.isEmpty()) {
            return 0;
        }

        int updated = 0;

        for (String externalId : resolvedExternalIds) {
            updated +=
                    jdbcClient.sql("""
                            UPDATE reconciliation_review_items
                            SET review_status = 'RESOLVED',
                                resolution_source = NULL,
                                resolution_external_id = NULL,
                                resolution_note =
                                    'AUTO_RECONCILED_AFTER_SOURCE_REFRESH',
                                resolved_at = CURRENT_TIMESTAMP,
                                updated_at = CURRENT_TIMESTAMP
                            WHERE season_id = :seasonId
                              AND fantacalcio_external_id = :externalId
                              AND review_status = 'OPEN'
                            """)
                            .param("seasonId", seasonId)
                            .param("externalId", externalId)
                            .update();
        }

        return updated;
    }


    private int countStatus(
            String season,
            String status
    ) {

        return jdbcClient.sql("""
                SELECT COUNT(*)

                FROM reconciliation_review_items review

                JOIN seasons season
                    ON season.id = review.season_id

                WHERE season.code = :season
                  AND review.review_status = :status
                """)
                .param("season", season)
                .param("status", status)
                .query(Integer.class)
                .single();
    }

    private Map<String, Object> findReviewRow(
            Long reviewId
    ) {

        if (reviewId == null) {
            throw new BadRequestException(
                    "reviewId obbligatorio"
            );
        }

        return jdbcClient.sql("""
                SELECT
                    id,
                    fantacalcio_external_id,
                    gazzetta_batch_id,
                    fantamarkt_batch_id,
                    review_status

                FROM reconciliation_review_items

                WHERE id = :reviewId
                """)
                .param("reviewId", reviewId)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Elemento review non trovato: "
                                        + reviewId
                        )
                );
    }

    private long findSecondaryBatchId(
            Map<String, Object> review,
            String source
    ) {

        Object value =
                "GAZZETTA".equals(source)
                        ? review.get("gazzetta_batch_id")
                        : review.get("fantamarkt_batch_id");

        if (!(value instanceof Number number)) {
            throw new ConflictException(
                    "Nessun batch "
                            + source
                            + " disponibile per questa review"
            );
        }

        return number.longValue();
    }

    private void ensureSecondaryExternalIdExists(
            long batchId,
            String externalId
    ) {

        int count =
                jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM player_import_rows
                        WHERE batch_id = :batchId
                          AND external_id = :externalId
                          AND valid = TRUE
                        """)
                        .param("batchId", batchId)
                        .param(
                                "externalId",
                                externalId
                        )
                        .query(Integer.class)
                        .single();

        if (count != 1) {
            throw new ConflictException(
                    "External ID secondario non trovato "
                            + "nel batch selezionato: "
                            + externalId
            );
        }
    }

    private ReconciliationReviewItemResponse mapResponse(
            java.sql.ResultSet resultSet
    ) throws java.sql.SQLException {

        long fantamarktBatch =
                resultSet.getLong(
                        "fantamarkt_batch_id"
                );

        Long fantamarktBatchId =
                resultSet.wasNull()
                        ? null
                        : fantamarktBatch;

        return new ReconciliationReviewItemResponse(
                resultSet.getLong("id"),
                resultSet.getString("season"),

                resultSet.getLong(
                        "fantacalcio_batch_id"
                ),
                resultSet.getLong(
                        "gazzetta_batch_id"
                ),
                fantamarktBatchId,

                resultSet.getString(
                        "fantacalcio_external_id"
                ),
                resultSet.getString(
                        "fantacalcio_name"
                ),
                resultSet.getString(
                        "fantacalcio_club"
                ),

                resultSet.getString(
                        "gazzetta_external_id"
                ),
                resultSet.getString(
                        "gazzetta_name"
                ),
                resultSet.getString(
                        "gazzetta_club"
                ),

                resultSet.getString(
                        "fantamarkt_external_id"
                ),
                resultSet.getString(
                        "fantamarkt_name"
                ),
                resultSet.getString(
                        "fantamarkt_club"
                ),

                resultSet.getString(
                        "review_status"
                ),

                resultSet.getString(
                        "resolution_source"
                ),
                resultSet.getString(
                        "resolution_external_id"
                ),
                resultSet.getString(
                        "resolution_note"
                ),

                resultSet.getString(
                        "exclusion_reason"
                ),
                resultSet.getString(
                        "exclusion_note"
                ),

                resultSet.getObject(
                        "first_seen_at",
                        OffsetDateTime.class
                ),
                resultSet.getObject(
                        "last_seen_at",
                        OffsetDateTime.class
                ),
                resultSet.getObject(
                        "resolved_at",
                        OffsetDateTime.class
                ),
                resultSet.getObject(
                        "excluded_at",
                        OffsetDateTime.class
                )
        );
    }

    private String stringValue(
            Map<String, Object> row,
            String key
    ) {

        Object value = row.get(key);

        return value == null
                ? null
                : value.toString();
    }

    private String requireText(
            String value,
            String message
    ) {

        if (value == null || value.isBlank()) {
            throw new BadRequestException(message);
        }

        return value.trim();
    }

    private record BatchSeason(
            Long seasonId,
            String seasonCode,
            String source,
            String status
    ) {
    }
}






