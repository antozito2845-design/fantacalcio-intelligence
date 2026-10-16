package it.fantacalciointelligence.backend.importer.admin;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.common.BadRequestException;
import it.fantacalciointelligence.backend.common.ResourceNotFoundException;

@Service
public class ImportBatchAdminService {

    private static final Set<String> ALLOWED_SOURCES =
            Set.of(
                    "FANTACALCIO",
                    "GAZZETTA",
                    "FANTAMARKT"
            );

    private static final Set<String> ALLOWED_STATUSES =
            Set.of(
                    "PROCESSING",
                    "COMPLETED",
                    "FAILED"
            );

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final JdbcClient jdbcClient;

    public ImportBatchAdminService(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional(readOnly = true)
    public ImportBatchPageResponse findBatches(
            String source,
            String season,
            String status,
            int page,
            int size
    ) {
        int safePage = validatePage(page);
        int safeSize = validateSize(size);

        String normalizedSource =
                normalizeSource(source);

        String normalizedStatus =
                normalizeStatus(status);

        String normalizedSeason =
                normalizeOptional(season);

        StringBuilder where = new StringBuilder(
                """
                WHERE 1 = 1
                """
        );

        Map<String, Object> parameters =
                new HashMap<>();

        if (normalizedSource != null) {
            where.append(
                    " AND batch.source = :source "
            );

            parameters.put(
                    "source",
                    normalizedSource
            );
        }

        if (normalizedSeason != null) {
            where.append(
                    " AND season.code = :season "
            );

            parameters.put(
                    "season",
                    normalizedSeason
            );
        }

        if (normalizedStatus != null) {
            where.append(
                    " AND batch.status = :status "
            );

            parameters.put(
                    "status",
                    normalizedStatus
            );
        }

        long totalElements =
                jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM import_batches batch

                        JOIN seasons season
                            ON season.id = batch.season_id
                        """
                        + where
                )
                        .params(parameters)
                        .query(Long.class)
                        .single();

        parameters.put(
                "limit",
                safeSize
        );

        parameters.put(
                "offset",
                safePage * safeSize
        );

        List<ImportBatchSummaryResponse> content =
                jdbcClient.sql("""
                        SELECT
                            batch.id,
                            batch.source,
                            season.code AS season_code,
                            batch.original_filename,
                            batch.status,
                            batch.total_rows,
                            batch.valid_rows,
                            batch.invalid_rows,
                            batch.reconciled_rows,
                            batch.started_at,
                            batch.completed_at,
                            batch.reconciliation_started_at,
                            batch.reconciliation_completed_at,
                            batch.error_message

                        FROM import_batches batch

                        JOIN seasons season
                            ON season.id = batch.season_id
                        """
                        + where
                        + """
                        ORDER BY
                            batch.started_at DESC,
                            batch.id DESC

                        LIMIT :limit
                        OFFSET :offset
                        """
                )
                        .params(parameters)
                        .query(
                                this::mapBatch
                        )
                        .list();

        int totalPages =
                calculateTotalPages(
                        totalElements,
                        safeSize
                );

        return new ImportBatchPageResponse(
                content,
                safePage,
                safeSize,
                totalElements,
                totalPages
        );
    }

    @Transactional(readOnly = true)
    public ImportBatchDetailResponse findBatch(
            Long batchId,
            int errorPage,
            int errorSize
    ) {
        if (batchId == null || batchId <= 0) {
            throw new BadRequestException(
                    "ID batch non valido"
            );
        }

        int safeErrorPage =
                validatePage(errorPage);

        int safeErrorSize =
                validateSize(errorSize);

        ImportBatchSummaryResponse batch =
                jdbcClient.sql("""
                        SELECT
                            batch.id,
                            batch.source,
                            season.code AS season_code,
                            batch.original_filename,
                            batch.status,
                            batch.total_rows,
                            batch.valid_rows,
                            batch.invalid_rows,
                            batch.reconciled_rows,
                            batch.started_at,
                            batch.completed_at,
                            batch.reconciliation_started_at,
                            batch.reconciliation_completed_at,
                            batch.error_message

                        FROM import_batches batch

                        JOIN seasons season
                            ON season.id = batch.season_id

                        WHERE batch.id = :batchId
                        """)
                        .param(
                                "batchId",
                                batchId
                        )
                        .query(
                                this::mapBatch
                        )
                        .optional()
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Batch import non trovato: "
                                                + batchId
                                )
                        );

        long totalInvalidRows =
                jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM player_import_rows
                        WHERE batch_id = :batchId
                          AND valid = FALSE
                        """)
                        .param(
                                "batchId",
                                batchId
                        )
                        .query(Long.class)
                        .single();

        List<ImportRowErrorResponse> invalidRows =
                jdbcClient.sql("""
                        SELECT
                            id,
                            row_number,
                            external_id,
                            external_name,
                            club_name,
                            fantasy_role,
                            validation_error,
                            raw_data::TEXT AS raw_data

                        FROM player_import_rows

                        WHERE batch_id = :batchId
                          AND valid = FALSE

                        ORDER BY
                            row_number ASC,
                            id ASC

                        LIMIT :limit
                        OFFSET :offset
                        """)
                        .param(
                                "batchId",
                                batchId
                        )
                        .param(
                                "limit",
                                safeErrorSize
                        )
                        .param(
                                "offset",
                                safeErrorPage
                                        * safeErrorSize
                        )
                        .query(
                                this::mapInvalidRow
                        )
                        .list();

        int totalErrorPages =
                calculateTotalPages(
                        totalInvalidRows,
                        safeErrorSize
                );

        return new ImportBatchDetailResponse(
                batch,
                invalidRows,
                safeErrorPage,
                safeErrorSize,
                totalInvalidRows,
                totalErrorPages
        );
    }

    private ImportBatchSummaryResponse mapBatch(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new ImportBatchSummaryResponse(
                resultSet.getLong("id"),
                resultSet.getString("source"),
                resultSet.getString("season_code"),
                resultSet.getString(
                        "original_filename"
                ),
                resultSet.getString("status"),
                resultSet.getInt("total_rows"),
                resultSet.getInt("valid_rows"),
                resultSet.getInt("invalid_rows"),
                resultSet.getInt("reconciled_rows"),
                resultSet.getObject(
                        "started_at",
                        OffsetDateTime.class
                ),
                resultSet.getObject(
                        "completed_at",
                        OffsetDateTime.class
                ),
                resultSet.getObject(
                        "reconciliation_started_at",
                        OffsetDateTime.class
                ),
                resultSet.getObject(
                        "reconciliation_completed_at",
                        OffsetDateTime.class
                ),
                resultSet.getString(
                        "error_message"
                )
        );
    }

    private ImportRowErrorResponse mapInvalidRow(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new ImportRowErrorResponse(
                resultSet.getLong("id"),
                resultSet.getInt("row_number"),
                resultSet.getString("external_id"),
                resultSet.getString("external_name"),
                resultSet.getString("club_name"),
                resultSet.getString("fantasy_role"),
                resultSet.getString(
                        "validation_error"
                ),
                resultSet.getString("raw_data")
        );
    }

    private String normalizeSource(
            String source
    ) {
        String normalized =
                normalizeOptional(source);

        if (normalized == null) {
            return null;
        }

        normalized = normalized.toUpperCase(
                Locale.ROOT
        );

        if (!ALLOWED_SOURCES.contains(normalized)) {
            throw new BadRequestException(
                    "Fonte non valida: " + source
            );
        }

        return normalized;
    }

    private String normalizeStatus(
            String status
    ) {
        String normalized =
                normalizeOptional(status);

        if (normalized == null) {
            return null;
        }

        normalized = normalized.toUpperCase(
                Locale.ROOT
        );

        if (!ALLOWED_STATUSES.contains(normalized)) {
            throw new BadRequestException(
                    "Stato import non valido: "
                            + status
            );
        }

        return normalized;
    }

    private String normalizeOptional(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private int validatePage(int page) {
        if (page < 0) {
            throw new BadRequestException(
                    "La pagina non può essere negativa"
            );
        }

        return page;
    }

    private int validateSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }

        if (size > MAX_PAGE_SIZE) {
            return MAX_PAGE_SIZE;
        }

        return size;
    }

    private int calculateTotalPages(
            long totalElements,
            int size
    ) {
        if (totalElements == 0) {
            return 0;
        }

        return (int) Math.ceil(
                (double) totalElements / size
        );
    }
}
