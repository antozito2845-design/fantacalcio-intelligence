package it.fantacalciointelligence.backend.fixture.importer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import it.fantacalciointelligence.backend.common.BadRequestException;
import it.fantacalciointelligence.backend.common.SecretSanitizer;
import it.fantacalciointelligence.backend.common.ResourceNotFoundException;
import it.fantacalciointelligence.backend.importer.TextNormalizer;
import it.fantacalciointelligence.backend.importer.CsvImportPayload;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
public class FixtureImportService {

    private static final Set<String> REQUIRED_COLUMNS =
            Set.of(
                    "matchday",
                    "home_club",
                    "away_club"
            );

    private static final Set<String> VALID_STATUSES =
            Set.of(
                    "SCHEDULED",
                    "LIVE",
                    "FINISHED",
                    "POSTPONED",
                    "CANCELLED"
            );

    private final JdbcClient jdbcClient;
    private final TextNormalizer textNormalizer;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate requiresNewTransaction;

    public FixtureImportService(
            JdbcClient jdbcClient,
            TextNormalizer textNormalizer,
            JsonMapper jsonMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.jdbcClient = jdbcClient;
        this.textNormalizer = textNormalizer;
        this.jsonMapper = jsonMapper;

        this.transactionTemplate =
                new TransactionTemplate(
                        transactionManager
                );

        this.requiresNewTransaction =
                new TransactionTemplate(
                        transactionManager
                );

        this.requiresNewTransaction
                .setPropagationBehavior(
                        TransactionDefinition
                                .PROPAGATION_REQUIRES_NEW
                );
    }

    public FixtureImportResponse importCsv(
            String source,
            String seasonCode,
            MultipartFile file
    ) {
        return importPayload(source,seasonCode,CsvImportPayload.from(file,"fixtures.csv"));
    }

    public FixtureImportResponse importPayload(String source,String seasonCode,CsvImportPayload file) {
        String normalizedSource =
                normalizeRequired(
                        source,
                        "Fonte obbligatoria"
                )
                        .toUpperCase(
                                Locale.ROOT
                        );

        String normalizedSeason =
                normalizeRequired(
                        seasonCode,
                        "Stagione obbligatoria"
                );

        validateFile(file);

        Long seasonId =
                findSeasonId(
                        normalizedSeason
                );

        String filename =
                resolveFilename(file);

        Long batchId =
                createBatch(
                        seasonId,
                        normalizedSource,
                        filename
                );

        try {
            return Objects.requireNonNull(
                    transactionTemplate.execute(
                            status ->
                                    processImport(
                                            batchId,
                                            seasonId,
                                            normalizedSource,
                                            normalizedSeason,
                                            filename,
                                            file
                                    )
                    )
            );
        } catch (RuntimeException exception) {
            String errorMessage =
                    readableMessage(exception);

            markBatchFailed(
                    batchId,
                    errorMessage
            );

            if (exception instanceof BadRequestException) {
                throw exception;
            }

            throw new BadRequestException(
                    "Import calendario fallito: "
                            + errorMessage,
                    exception
            );
        }
    }

    private FixtureImportResponse processImport(
            Long batchId,
            Long seasonId,
            String source,
            String seasonCode,
            String filename,
            CsvImportPayload file
    ) {
        int totalRows = 0;
        int validRows = 0;
        int invalidRows = 0;
        int importedFixtures = 0;

        Set<String> fixtureKeys =
                new HashSet<>();

        try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        file.openStream(),
                                        StandardCharsets.UTF_8
                                )
                        );

                CSVParser parser =
                        CSVFormat.DEFAULT.builder()
                                .setHeader()
                                .setSkipHeaderRecord(true)
                                .setIgnoreEmptyLines(true)
                                .setIgnoreSurroundingSpaces(true)
                                .setTrim(true)
                                .get()
                                .parse(reader)
        ) {
            validateHeaders(
                    parser.getHeaderNames()
            );

            for (CSVRecord record : parser) {
                totalRows++;

                ParsedFixtureRow row =
                        parseRow(
                                parser,
                                record,
                                totalRows,
                                fixtureKeys
                        );

                saveImportRow(
                        batchId,
                        row
                );

                if (!row.valid()) {
                    invalidRows++;
                    continue;
                }

                Long homeClubId =
                        findClubId(
                                seasonId,
                                row.normalizedHomeClub()
                        );

                Long awayClubId =
                        findClubId(
                                seasonId,
                                row.normalizedAwayClub()
                        );

                if (homeClubId == null
                        || awayClubId == null) {

                    String error =
                            missingClubError(
                                    row,
                                    homeClubId,
                                    awayClubId
                            );

                    markRowInvalid(
                            batchId,
                            row.rowNumber(),
                            error
                    );

                    invalidRows++;
                    continue;
                }

                upsertFixture(
                        seasonId,
                        row,
                        homeClubId,
                        awayClubId
                );

                validRows++;
                importedFixtures++;
            }
        } catch (IOException exception) {
            throw new BadRequestException(
                    "Impossibile leggere il file CSV",
                    exception
            );
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(
                    "Formato CSV non valido: "
                            + readableMessage(exception),
                    exception
            );
        }

        if (totalRows == 0) {
            throw new BadRequestException(
                    "Il file CSV non contiene righe dati"
            );
        }

        completeBatch(
                batchId,
                totalRows,
                validRows,
                invalidRows,
                importedFixtures
        );

        return new FixtureImportResponse(
                batchId,
                source,
                seasonCode,
                filename,
                totalRows,
                validRows,
                invalidRows,
                importedFixtures,
                "COMPLETED",
                null
        );
    }

    private ParsedFixtureRow parseRow(
            CSVParser parser,
            CSVRecord record,
            int rowNumber,
            Set<String> fixtureKeys
    ) {
        List<String> errors =
                new ArrayList<>();

        Integer matchday =
                parseInteger(
                        value(record, "matchday"),
                        "Giornata",
                        errors
                );

        String homeClub =
                value(record, "home_club");

        String awayClub =
                value(record, "away_club");

        String normalizedHomeClub =
                textNormalizer.normalize(
                        homeClub
                );

        String normalizedAwayClub =
                textNormalizer.normalize(
                        awayClub
                );

        OffsetDateTime kickoffAt =
                parseKickoff(
                        value(record, "kickoff_at"),
                        errors
                );

        String status =
                normalizeStatus(
                        value(record, "status"),
                        errors
                );

        Integer homeGoals =
                parseInteger(
                        value(record, "home_goals"),
                        "Gol casa",
                        errors
                );

        Integer awayGoals =
                parseInteger(
                        value(record, "away_goals"),
                        "Gol trasferta",
                        errors
                );

        if (matchday == null) {
            errors.add(
                    "Giornata mancante"
            );
        } else if (matchday < 1 || matchday > 38) {
            errors.add(
                    "Giornata non valida: "
                            + matchday
            );
        }

        if (homeClub == null) {
            errors.add(
                    "Squadra di casa mancante"
            );
        }

        if (awayClub == null) {
            errors.add(
                    "Squadra ospite mancante"
            );
        }

        if (
                !normalizedHomeClub.isBlank()
                && normalizedHomeClub.equals(
                        normalizedAwayClub
                )
        ) {
            errors.add(
                    "La squadra di casa e quella ospite coincidono"
            );
        }

        if (homeGoals != null && homeGoals < 0) {
            errors.add(
                    "Gol casa non validi"
            );
        }

        if (awayGoals != null && awayGoals < 0) {
            errors.add(
                    "Gol trasferta non validi"
            );
        }

        if ("FINISHED".equals(status)
                && (
                    homeGoals == null
                    || awayGoals == null
                )) {

            errors.add(
                    "Una partita terminata deve avere entrambi i risultati"
            );
        }

        if (
                matchday != null
                && !normalizedHomeClub.isBlank()
                && !normalizedAwayClub.isBlank()
        ) {
            String fixtureKey =
                    matchday
                            + "|"
                            + normalizedHomeClub
                            + "|"
                            + normalizedAwayClub;

            if (!fixtureKeys.add(fixtureKey)) {
                errors.add(
                        "Partita duplicata nel file"
                );
            }
        }

        return new ParsedFixtureRow(
                rowNumber,
                matchday,
                homeClub,
                normalizedHomeClub,
                awayClub,
                normalizedAwayClub,
                kickoffAt,
                status,
                homeGoals,
                awayGoals,
                errors.isEmpty(),
                errors.isEmpty()
                        ? null
                        : String.join(
                                "; ",
                                errors
                        ),
                toJson(
                        rawData(
                                parser,
                                record
                        )
                )
        );
    }

    private void upsertFixture(
            Long seasonId,
            ParsedFixtureRow row,
            Long homeClubId,
            Long awayClubId
    ) {
        jdbcClient.sql("""
                INSERT INTO fixtures (
                    season_id,
                    matchday,
                    home_club_id,
                    away_club_id,
                    kickoff_at,
                    status,
                    home_goals,
                    away_goals
                )
                VALUES (
                    :seasonId,
                    :matchday,
                    :homeClubId,
                    :awayClubId,
                    :kickoffAt,
                    :status,
                    :homeGoals,
                    :awayGoals
                )

                ON CONFLICT (
                    season_id,
                    matchday,
                    home_club_id,
                    away_club_id
                )
                DO UPDATE SET
                    kickoff_at =
                        EXCLUDED.kickoff_at,

                    status =
                        EXCLUDED.status,

                    home_goals =
                        EXCLUDED.home_goals,

                    away_goals =
                        EXCLUDED.away_goals,

                    updated_at =
                        CURRENT_TIMESTAMP
                """)
                .param(
                        "seasonId",
                        seasonId
                )
                .param(
                        "matchday",
                        row.matchday()
                )
                .param(
                        "homeClubId",
                        homeClubId
                )
                .param(
                        "awayClubId",
                        awayClubId
                )
                .param(
                        "kickoffAt",
                        row.kickoffAt(),
                        Types.TIMESTAMP_WITH_TIMEZONE
                )
                .param(
                        "status",
                        row.status()
                )
                .param(
                        "homeGoals",
                        row.homeGoals(),
                        Types.INTEGER
                )
                .param(
                        "awayGoals",
                        row.awayGoals(),
                        Types.INTEGER
                )
                .update();
    }

    private void saveImportRow(
            Long batchId,
            ParsedFixtureRow row
    ) {
        jdbcClient.sql("""
                INSERT INTO fixture_import_rows (
                    batch_id,
                    row_number,
                    matchday,
                    home_club_name,
                    normalized_home_club,
                    away_club_name,
                    normalized_away_club,
                    kickoff_at,
                    fixture_status,
                    home_goals,
                    away_goals,
                    valid,
                    validation_error,
                    raw_data
                )
                VALUES (
                    :batchId,
                    :rowNumber,
                    :matchday,
                    :homeClubName,
                    :normalizedHomeClub,
                    :awayClubName,
                    :normalizedAwayClub,
                    :kickoffAt,
                    :status,
                    :homeGoals,
                    :awayGoals,
                    :valid,
                    :validationError,
                    CAST(:rawData AS JSONB)
                )
                """)
                .param("batchId", batchId)
                .param("rowNumber", row.rowNumber())
                .param(
                        "matchday",
                        row.matchday(),
                        Types.INTEGER
                )
                .param(
                        "homeClubName",
                        row.homeClubName(),
                        Types.VARCHAR
                )
                .param(
                        "normalizedHomeClub",
                        row.normalizedHomeClub(),
                        Types.VARCHAR
                )
                .param(
                        "awayClubName",
                        row.awayClubName(),
                        Types.VARCHAR
                )
                .param(
                        "normalizedAwayClub",
                        row.normalizedAwayClub(),
                        Types.VARCHAR
                )
                .param(
                        "kickoffAt",
                        row.kickoffAt(),
                        Types.TIMESTAMP_WITH_TIMEZONE
                )
                .param(
                        "status",
                        row.status(),
                        Types.VARCHAR
                )
                .param(
                        "homeGoals",
                        row.homeGoals(),
                        Types.INTEGER
                )
                .param(
                        "awayGoals",
                        row.awayGoals(),
                        Types.INTEGER
                )
                .param("valid", row.valid())
                .param(
                        "validationError",
                        row.validationError(),
                        Types.VARCHAR
                )
                .param("rawData", row.rawData())
                .update();
    }

    private void markRowInvalid(
            Long batchId,
            int rowNumber,
            String error
    ) {
        jdbcClient.sql("""
                UPDATE fixture_import_rows
                SET valid = FALSE,
                    validation_error = :error
                WHERE batch_id = :batchId
                  AND row_number = :rowNumber
                """)
                .param("error", error)
                .param("batchId", batchId)
                .param("rowNumber", rowNumber)
                .update();
    }

    private Long findClubId(
            Long seasonId,
            String normalizedClub
    ) {
        if (
                normalizedClub == null
                || normalizedClub.isBlank()
        ) {
            return null;
        }

        return jdbcClient.sql("""
                SELECT club.id
                FROM season_clubs season_club
                JOIN clubs club ON club.id = season_club.club_id
                WHERE season_club.season_id = :seasonId
                  AND (
                      LOWER(club.slug) = LOWER(:club)
                      OR LOWER(club.name) = LOWER(:club)
                      OR LOWER(club.short_name) = LOWER(:club)
                  )
                ORDER BY id
                LIMIT 1
                """)
                .param("seasonId", seasonId)
                .param("club", normalizedClub)
                .query(Long.class)
                .optional()
                .orElse(null);
    }

    private Long findSeasonId(
            String seasonCode
    ) {
        return jdbcClient.sql("""
                SELECT id
                FROM seasons
                WHERE code = :seasonCode
                """)
                .param("seasonCode", seasonCode)
                .query(Long.class)
                .optional()
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Stagione non trovata: "
                                        + seasonCode
                        )
                );
    }

    private Long createBatch(
            Long seasonId,
            String source,
            String filename
    ) {
        return Objects.requireNonNull(
                requiresNewTransaction.execute(
                        status ->
                                jdbcClient.sql("""
                                        INSERT INTO fixture_import_batches (
                                            season_id,
                                            source,
                                            original_filename,
                                            status
                                        )
                                        VALUES (
                                            :seasonId,
                                            :source,
                                            :filename,
                                            'PROCESSING'
                                        )
                                        RETURNING id
                                        """)
                                        .param(
                                                "seasonId",
                                                seasonId
                                        )
                                        .param(
                                                "source",
                                                source
                                        )
                                        .param(
                                                "filename",
                                                filename
                                        )
                                        .query(Long.class)
                                        .single()
                )
        );
    }

    private void completeBatch(
            Long batchId,
            int totalRows,
            int validRows,
            int invalidRows,
            int importedFixtures
    ) {
        jdbcClient.sql("""
                UPDATE fixture_import_batches
                SET status = 'COMPLETED',
                    total_rows = :totalRows,
                    valid_rows = :validRows,
                    invalid_rows = :invalidRows,
                    imported_fixtures = :importedFixtures,
                    error_message = NULL,
                    completed_at = CURRENT_TIMESTAMP
                WHERE id = :batchId
                """)
                .param("totalRows", totalRows)
                .param("validRows", validRows)
                .param("invalidRows", invalidRows)
                .param(
                        "importedFixtures",
                        importedFixtures
                )
                .param("batchId", batchId)
                .update();
    }

    private void markBatchFailed(
            Long batchId,
            String errorMessage
    ) {
        requiresNewTransaction.executeWithoutResult(
                status ->
                        jdbcClient.sql("""
                                UPDATE fixture_import_batches
                                SET status = 'FAILED',
                                    error_message = :errorMessage,
                                    completed_at =
                                        CURRENT_TIMESTAMP
                                WHERE id = :batchId
                                """)
                                .param(
                                        "errorMessage",
                                        truncate(
                                                errorMessage,
                                                2000
                                        )
                                )
                                .param("batchId", batchId)
                                .update()
        );
    }

    private void validateHeaders(
            List<String> headers
    ) {
        Set<String> normalizedHeaders =
                new HashSet<>();

        for (String header : headers) {
            if (header != null) {
                normalizedHeaders.add(
                        header.trim()
                                .toLowerCase(
                                        Locale.ROOT
                                )
                );
            }
        }

        List<String> missingColumns =
                REQUIRED_COLUMNS.stream()
                        .filter(column ->
                                !normalizedHeaders.contains(
                                        column
                                )
                        )
                        .sorted()
                        .toList();

        if (!missingColumns.isEmpty()) {
            throw new BadRequestException(
                    "Colonne obbligatorie mancanti: "
                            + String.join(
                                    ", ",
                                    missingColumns
                            )
            );
        }
    }

    private String normalizeStatus(
            String status,
            List<String> errors
    ) {
        if (status == null || status.isBlank()) {
            return "SCHEDULED";
        }

        String normalized =
                status.trim()
                        .toUpperCase(
                                Locale.ROOT
                        );

        if (!VALID_STATUSES.contains(normalized)) {
            errors.add(
                    "Stato partita non valido: "
                            + status
            );

            return null;
        }

        return normalized;
    }

    private OffsetDateTime parseKickoff(
            String value,
            List<String> errors
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException exception) {
            errors.add(
                    "Data e ora non valide: "
                            + value
                            + ". Usare formato ISO, esempio "
                            + "2026-08-22T18:00:00+02:00"
            );

            return null;
        }
    }

    private Integer parseInteger(
            String value,
            String field,
            List<String> errors
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            errors.add(
                    field
                            + " non valido: "
                            + value
            );

            return null;
        }
    }

    private String missingClubError(
            ParsedFixtureRow row,
            Long homeClubId,
            Long awayClubId
    ) {
        List<String> errors =
                new ArrayList<>();

        if (homeClubId == null) {
            errors.add(
                    "Squadra di casa non trovata: "
                            + row.homeClubName()
            );
        }

        if (awayClubId == null) {
            errors.add(
                    "Squadra ospite non trovata: "
                            + row.awayClubName()
            );
        }

        return String.join(
                "; ",
                errors
        );
    }

    private Map<String, String> rawData(
            CSVParser parser,
            CSVRecord record
    ) {
        Map<String, String> result =
                new LinkedHashMap<>();

        for (String header : parser.getHeaderNames()) {
            result.put(
                    header,
                    record.isMapped(header)
                            ? record.get(header)
                            : null
            );
        }

        return result;
    }

    private String toJson(
            Map<String, String> data
    ) {
        try {
            return jsonMapper.writeValueAsString(
                    data
            );
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Impossibile convertire la riga in JSON",
                    exception
            );
        }
    }

    private String value(
            CSVRecord record,
            String column
    ) {
        if (!record.isMapped(column)) {
            return null;
        }

        String value = record.get(column);

        return value == null || value.isBlank()
                ? null
                : value.trim();
    }

    private void validateFile(
            CsvImportPayload file
    ) {
        if (file == null || file.empty()) {
            throw new BadRequestException(
                    "File CSV obbligatorio"
            );
        }
    }

    private String resolveFilename(
            CsvImportPayload file
    ) {
        String filename =
                file.filename();

        if (filename == null || filename.isBlank()) {
            return "fixtures.csv";
        }

        filename =
                filename.replace("\\", "/");

        int separator =
                filename.lastIndexOf('/');

        if (separator >= 0) {
            filename =
                    filename.substring(
                            separator + 1
                    );
        }

        return truncate(
                filename,
                255
        );
    }

    private String normalizeRequired(
            String value,
            String errorMessage
    ) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(
                    errorMessage
            );
        }

        return value.trim();
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

        if (value.length() <= maximumLength) {
            return value;
        }

        return value.substring(
                0,
                maximumLength
        );
    }

    private record ParsedFixtureRow(
            int rowNumber,
            Integer matchday,
            String homeClubName,
            String normalizedHomeClub,
            String awayClubName,
            String normalizedAwayClub,
            OffsetDateTime kickoffAt,
            String status,
            Integer homeGoals,
            Integer awayGoals,
            boolean valid,
            String validationError,
            String rawData
    ) {
    }
}
