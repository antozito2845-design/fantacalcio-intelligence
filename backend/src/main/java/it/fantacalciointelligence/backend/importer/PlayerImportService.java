package it.fantacalciointelligence.backend.importer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Types;
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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
public class PlayerImportService {

    private static final Set<String> ALLOWED_SOURCES =
            Set.of(
                    "FANTACALCIO",
                    "GAZZETTA",
                    "FANTAMARKT"
            );

    private static final Set<String> REQUIRED_COLUMNS =
            Set.of(
                    "external_id",
                    "name",
                    "club",
                    "role"
            );

    private static final Set<String> VALID_ROLES =
            Set.of(
                    "P",
                    "D",
                    "C",
                    "A"
            );

    private final JdbcClient jdbcClient;
    private final TextNormalizer textNormalizer;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate requiresNewTransaction;

    public PlayerImportService(
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

    public PlayerImportResponse importCsv(
            String source,
            String seasonCode,
            MultipartFile file
    ) {
        return importPayload(source,seasonCode,CsvImportPayload.from(file,"players.csv"));
    }

    public PlayerImportResponse importPayload(
            String source,
            String seasonCode,
            CsvImportPayload file
    ) {
        String normalizedSource =
                normalizeSource(source);

        String normalizedSeason =
                validateSeasonCode(seasonCode);

        validateFileReference(file);

        Long seasonId = findSeasonId(
                normalizedSeason
        );

        String filename = resolveFilename(file);

        Long batchId = createBatch(
                normalizedSource,
                seasonId,
                filename
        );

        try {
            return Objects.requireNonNull(
                    transactionTemplate.execute(
                            transactionStatus ->
                                    processImport(
                                            batchId,
                                            normalizedSource,
                                            normalizedSeason,
                                            filename,
                                            file
                                    )
                    )
            );
        } catch (RuntimeException exception) {
            String errorMessage =
                    readableErrorMessage(exception);

            markBatchFailed(
                    batchId,
                    errorMessage
            );

            if (exception instanceof BadRequestException) {
                throw exception;
            }

            throw new BadRequestException(
                    "Import CSV fallito: "
                            + errorMessage,
                    exception
            );
        }
    }

    private PlayerImportResponse processImport(
            Long batchId,
            String source,
            String seasonCode,
            String filename,
            CsvImportPayload file
    ) {
        int totalRows = 0;
        int validRows = 0;
        int invalidRows = 0;

        Set<String> externalIds =
                new HashSet<>();

        Set<String> playerKeys =
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

                ImportedRow importedRow =
                        parseRow(
                                parser,
                                record,
                                totalRows,
                                externalIds,
                                playerKeys
                        );

                saveImportedRow(
                        batchId,
                        importedRow
                );

                if (importedRow.valid()) {
                    validRows++;
                } else {
                    invalidRows++;
                }
            }
        } catch (IOException exception) {
            throw new BadRequestException(
                    "Impossibile leggere il file CSV",
                    exception
            );
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(
                    "Formato CSV non valido: "
                            + readableErrorMessage(exception),
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
                invalidRows
        );

        return new PlayerImportResponse(
                batchId,
                source,
                seasonCode,
                filename,
                totalRows,
                validRows,
                invalidRows,
                "COMPLETED",
                null
        );
    }

    private ImportedRow parseRow(
            CSVParser parser,
            CSVRecord record,
            int rowNumber,
            Set<String> externalIds,
            Set<String> playerKeys
    ) {
        String externalId =
                value(
                        record,
                        "external_id"
                );

        String name =
                value(
                        record,
                        "name"
                );

        String club =
                value(
                        record,
                        "club"
                );

        String role =
                normalizeRole(
                        value(
                                record,
                                "role"
                        )
                );

        String quotationText =
                value(
                        record,
                        "quotation"
                );

        String fmvText =
                value(
                        record,
                        "fmv"
                );

        String normalizedName =
                textNormalizer.normalize(name);

        String normalizedClub =
                textNormalizer.normalize(club);

        ParsedDecimal quotation =
                parseDecimal(
                        quotationText,
                        "Quotazione"
                );

        ParsedDecimal fmv =
                parseDecimal(
                        fmvText,
                        "FMV"
                );

        List<String> errors =
                new ArrayList<>();

        if (isBlank(externalId)) {
            errors.add(
                    "ID esterno mancante"
            );
        }

        if (isBlank(name)) {
            errors.add(
                    "Nome giocatore mancante"
            );
        }

        if (isBlank(club)) {
            errors.add(
                    "Squadra mancante"
            );
        }

        if (isBlank(role)) {
            errors.add(
                    "Ruolo mancante"
            );
        } else if (!VALID_ROLES.contains(role)) {
            errors.add(
                    "Ruolo non valido: " + role
            );
        }

        if (quotation.error() != null) {
            errors.add(
                    quotation.error()
            );
        }

        if (fmv.error() != null) {
            errors.add(
                    fmv.error()
            );
        }

        if (!isBlank(externalId)) {
            String externalIdKey =
                    externalId.toLowerCase(
                            Locale.ROOT
                    );

            if (!externalIds.add(externalIdKey)) {
                errors.add(
                        "ID esterno duplicato nel file"
                );
            }
        }

        if (
                !normalizedName.isBlank()
                && !normalizedClub.isBlank()
        ) {
            String playerKey =
                    normalizedName
                            + "|"
                            + normalizedClub;

            if (!playerKeys.add(playerKey)) {
                errors.add(
                        "Giocatore duplicato nel file"
                );
            }
        }

        Map<String, String> rawData =
                rawData(
                        parser,
                        record
                );

        String validationError =
                errors.isEmpty()
                        ? null
                        : String.join(
                                "; ",
                                errors
                        );

        return new ImportedRow(
                rowNumber,
                externalId,
                name,
                normalizedName,
                club,
                normalizedClub,
                role,
                quotation.value(),
                fmv.value(),
                errors.isEmpty(),
                validationError,
                toJson(rawData)
        );
    }

    private void saveImportedRow(
            Long batchId,
            ImportedRow row
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
                    validation_error,
                    raw_data
                )
                VALUES (
                    :batchId,
                    :rowNumber,
                    :externalId,
                    :externalName,
                    :normalizedName,
                    :clubName,
                    :normalizedClubName,
                    :fantasyRole,
                    :quotation,
                    :fmv,
                    :valid,
                    :validationError,
                    CAST(:rawData AS JSONB)
                )
                """)
                .param(
                        "batchId",
                        batchId
                )
                .param(
                        "rowNumber",
                        row.rowNumber()
                )
                .param(
                        "externalId",
                        row.externalId(),
                        Types.VARCHAR
                )
                .param(
                        "externalName",
                        emptyIfNull(
                                row.externalName()
                        )
                )
                .param(
                        "normalizedName",
                        emptyIfNull(
                                row.normalizedName()
                        )
                )
                .param(
                        "clubName",
                        row.clubName(),
                        Types.VARCHAR
                )
                .param(
                        "normalizedClubName",
                        row.normalizedClubName(),
                        Types.VARCHAR
                )
                .param(
                        "fantasyRole",
                        VALID_ROLES.contains(
                                row.fantasyRole()
                        )
                                ? row.fantasyRole()
                                : null,
                        Types.VARCHAR
                )
                .param(
                        "quotation",
                        row.quotation(),
                        Types.NUMERIC
                )
                .param(
                        "fmv",
                        row.fmv(),
                        Types.NUMERIC
                )
                .param(
                        "valid",
                        row.valid()
                )
                .param(
                        "validationError",
                        row.validationError(),
                        Types.VARCHAR
                )
                .param(
                        "rawData",
                        row.rawData()
                )
                .update();
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

    private Map<String, String> rawData(
            CSVParser parser,
            CSVRecord record
    ) {
        Map<String, String> rawData =
                new LinkedHashMap<>();

        for (String header : parser.getHeaderNames()) {
            rawData.put(
                    header,
                    record.isMapped(header)
                            ? record.get(header)
                            : null
            );
        }

        return rawData;
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

    private Long createBatch(
            String source,
            Long seasonId,
            String filename
    ) {
        return Objects.requireNonNull(
                requiresNewTransaction.execute(
                        transactionStatus ->
                                jdbcClient.sql("""
                                        INSERT INTO import_batches (
                                            source,
                                            season_id,
                                            original_filename,
                                            status,
                                            error_message
                                        )
                                        VALUES (
                                            :source,
                                            :seasonId,
                                            :filename,
                                            'PROCESSING',
                                            NULL
                                        )
                                        RETURNING id
                                        """)
                                        .param(
                                                "source",
                                                source
                                        )
                                        .param(
                                                "seasonId",
                                                seasonId
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
            int invalidRows
    ) {
        jdbcClient.sql("""
                UPDATE import_batches
                SET status = 'COMPLETED',
                    total_rows = :totalRows,
                    valid_rows = :validRows,
                    invalid_rows = :invalidRows,
                    error_message = NULL,
                    completed_at = CURRENT_TIMESTAMP
                WHERE id = :batchId
                """)
                .param(
                        "totalRows",
                        totalRows
                )
                .param(
                        "validRows",
                        validRows
                )
                .param(
                        "invalidRows",
                        invalidRows
                )
                .param(
                        "batchId",
                        batchId
                )
                .update();
    }

    private void markBatchFailed(
            Long batchId,
            String errorMessage
    ) {
        requiresNewTransaction.executeWithoutResult(
                transactionStatus ->
                        jdbcClient.sql("""
                                UPDATE import_batches
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
                                .param(
                                        "batchId",
                                        batchId
                                )
                                .update()
        );
    }

    private String normalizeSource(
            String source
    ) {
        if (source == null || source.isBlank()) {
            throw new BadRequestException(
                    "Fonte obbligatoria"
            );
        }

        String normalizedSource =
                source.trim()
                        .toUpperCase(
                                Locale.ROOT
                        );

        if (!ALLOWED_SOURCES.contains(
                normalizedSource
        )) {
            throw new BadRequestException(
                    "Fonte non valida: "
                            + source
            );
        }

        return normalizedSource;
    }

    private String validateSeasonCode(
            String seasonCode
    ) {
        if (
                seasonCode == null
                || seasonCode.isBlank()
        ) {
            throw new BadRequestException(
                    "Stagione obbligatoria"
            );
        }

        return seasonCode.trim();
    }

    private void validateFileReference(
            CsvImportPayload file
    ) {
        if (file == null) {
            throw new BadRequestException(
                    "File CSV obbligatorio"
            );
        }
    }

    private String resolveFilename(
            CsvImportPayload file
    ) {
        String originalFilename =
                file.filename();

        if (
                originalFilename == null
                || originalFilename.isBlank()
        ) {
            return "players.csv";
        }

        String normalizedFilename =
                originalFilename
                        .replace("\\", "/");

        int separatorIndex =
                normalizedFilename
                        .lastIndexOf('/');

        if (separatorIndex >= 0) {
            normalizedFilename =
                    normalizedFilename.substring(
                            separatorIndex + 1
                    );
        }

        return truncate(
                normalizedFilename,
                255
        );
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

    private String normalizeRole(
            String role
    ) {
        if (role == null || role.isBlank()) {
            return null;
        }

        return role.trim()
                .toUpperCase(
                        Locale.ROOT
                );
    }

    private ParsedDecimal parseDecimal(
            String rawValue,
            String fieldName
    ) {
        if (rawValue == null) {
            return new ParsedDecimal(
                    null,
                    null
            );
        }

        try {
            BigDecimal value =
                    new BigDecimal(
                            rawValue.replace(
                                    ",",
                                    "."
                            )
                    );

            if (value.signum() < 0) {
                return new ParsedDecimal(
                        null,
                        fieldName
                                + " non può essere negativa"
                );
            }

            return new ParsedDecimal(
                    value,
                    null
            );
        } catch (NumberFormatException exception) {
            return new ParsedDecimal(
                    null,
                    fieldName
                            + " non valida: "
                            + rawValue
            );
        }
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
                    "Impossibile convertire "
                            + "la riga in JSON",
                    exception
            );
        }
    }

    private String readableErrorMessage(
            Throwable exception
    ) {
        return SecretSanitizer.sanitize(exception);
    }

    private String emptyIfNull(
            String value
    ) {
        return value == null
                ? ""
                : value;
    }

    private boolean isBlank(
            String value
    ) {
        return value == null
                || value.isBlank();
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

    private record ParsedDecimal(
            BigDecimal value,
            String error
    ) {
    }

    private record ImportedRow(
            int rowNumber,
            String externalId,
            String externalName,
            String normalizedName,
            String clubName,
            String normalizedClubName,
            String fantasyRole,
            BigDecimal quotation,
            BigDecimal fmv,
            boolean valid,
            String validationError,
            String rawData
    ) {
    }
}
