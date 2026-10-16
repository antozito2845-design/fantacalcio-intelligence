package it.fantacalciointelligence.backend.statistics.importer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
import it.fantacalciointelligence.backend.importer.CsvImportPayload;
import it.fantacalciointelligence.backend.common.ResourceNotFoundException;
import it.fantacalciointelligence.backend.importer.TextNormalizer;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
public class PlayerStatisticsImportService {

    private static final Set<String> ALLOWED_SOURCES =
            Set.of(
                    "FANTACALCIO",
                    "GAZZETTA",
                    "FANTAMARKT",
                    "FBREF"
            );

    private static final Set<String> REQUIRED_COLUMNS =
            Set.of(
                    "external_id",
                    "name",
                    "appearances"
            );

    private final JdbcClient jdbcClient;
    private final TextNormalizer textNormalizer;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate requiresNewTransaction;

    public PlayerStatisticsImportService(
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

        this.requiresNewTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
    }

    public PlayerStatisticsImportResponse importCsv(
            String source,
            String seasonCode,
            MultipartFile file
    ) {
        return importPayload(source,seasonCode,CsvImportPayload.from(file,"player-statistics.csv"));
    }

    public PlayerStatisticsImportResponse importPayload(String source,String seasonCode,CsvImportPayload file) {
        String normalizedSource =
                normalizeSource(source);

        String normalizedSeason =
                validateSeasonCode(seasonCode);

        validateFileReference(file);

        Long seasonId =
                findSeasonId(normalizedSeason);

        String filename =
                resolveFilename(file);

        Long batchId =
                createBatch(
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
                                            seasonId,
                                            filename,
                                            file
                                    )
                    )
            );
        }
        catch (RuntimeException exception) {
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
                    "Import statistiche CSV fallito: "
                            + errorMessage,
                    exception
            );
        }
    }

    private PlayerStatisticsImportResponse processImport(
            Long batchId,
            String source,
            String seasonCode,
            Long seasonId,
            String filename,
            CsvImportPayload file
    ) {
        int totalRows = 0;
        int validRows = 0;
        int invalidRows = 0;
        int importedStatistics = 0;

        Set<String> rowKeys =
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
                    parser.getHeaderNames(),
                    source
            );

            for (CSVRecord record : parser) {
                totalRows++;

                ImportedStatisticsRow row =
                        parseRow(
                                parser,
                                record,
                                totalRows,
                                source,
                                rowKeys
                        );

                saveImportedRow(
                        batchId,
                        row
                );

                if (row.valid()) {
                    upsertStatistics(
                            row,
                            seasonId,
                            source,
                            batchId
                    );

                    validRows++;
                    importedStatistics++;
                }
                else {
                    invalidRows++;
                }
            }
        }
        catch (IOException exception) {
            throw new BadRequestException(
                    "Impossibile leggere il file CSV",
                    exception
            );
        }
        catch (IllegalArgumentException exception) {
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
                invalidRows,
                importedStatistics
        );

        return new PlayerStatisticsImportResponse(
                batchId,
                source,
                seasonCode,
                filename,
                totalRows,
                validRows,
                invalidRows,
                importedStatistics,
                "COMPLETED",
                null
        );
    }

    private ImportedStatisticsRow parseRow(
            CSVParser parser,
            CSVRecord record,
            int rowNumber,
            String source,
            Set<String> rowKeys
    ) {
        String externalId =
                value(
                        record,
                        "external_id"
                );

        String externalName =
                value(
                        record,
                        "name"
                );

        String clubName =
                value(
                        record,
                        "club"
                );

        List<String> historicalClubs = parseHistoricalClubs(
                value(record, "clubs_played"),
                clubName
        );

        String normalizedName =
                textNormalizer.normalize(
                        externalName
                );

        String normalizedClubName =
                textNormalizer.normalize(
                        clubName
                );

        List<String> errors =
                new ArrayList<>();

        if (isBlank(externalId)) {
            errors.add(
                    "ID esterno mancante"
            );
        }

        if (isBlank(externalName)) {
            errors.add(
                    "Nome giocatore mancante"
            );
        }

        if (!"FBREF".equals(source) && isBlank(clubName)) {
            errors.add(
                    "Squadra mancante"
            );
        }

        if ("FBREF".equals(source) && historicalClubs.isEmpty()) {
            errors.add("Club storici FBref mancanti");
        }

        ParsedInteger appearances =
                parseInteger(
                        value(record, "appearances"),
                        "Presenze",
                        true
                );

        ParsedInteger starts =
                parseInteger(
                        value(record, "starts"),
                        "Titolarità",
                        false
                );

        ParsedInteger minutesPlayed =
                parseInteger(
                        value(record, "minutes_played"),
                        "Minuti giocati",
                        false
                );

        ParsedDecimal averageRating =
                parseDecimal(
                        value(record, "average_rating"),
                        "Media voto",
                        BigDecimal.ZERO,
                        BigDecimal.TEN
                );

        ParsedDecimal fantasyAverage =
                parseDecimal(
                        value(record, "fantasy_average"),
                        "Fantamedia",
                        BigDecimal.ZERO,
                        BigDecimal.valueOf(20)
                );

        if ("FBREF".equals(source)
                && (averageRating.value() != null || fantasyAverage.value() != null)) {
            errors.add("FBref non può fornire media voto o fantamedia");
        }

        ParsedInteger goals =
                parseInteger(
                        value(record, "goals"),
                        "Gol",
                        false
                );

        ParsedInteger assists =
                parseInteger(
                        value(record, "assists"),
                        "Assist",
                        false
                );

        ParsedInteger penaltiesScored =
                parseInteger(
                        value(record, "penalties_scored"),
                        "Rigori segnati",
                        false
                );

        ParsedInteger penaltiesTaken =
                parseInteger(
                        value(record, "penalties_taken"),
                        "Rigori calciati",
                        false
                );

        ParsedInteger penaltiesMissed =
                parseInteger(
                        value(record, "penalties_missed"),
                        "Rigori sbagliati",
                        false
                );

        ParsedInteger yellowCards =
                parseInteger(
                        value(record, "yellow_cards"),
                        "Ammonizioni",
                        false
                );

        ParsedInteger redCards =
                parseInteger(
                        value(record, "red_cards"),
                        "Espulsioni",
                        false
                );

        ParsedInteger ownGoals =
                parseInteger(
                        value(record, "own_goals"),
                        "Autogol",
                        false
                );

        ParsedInteger goalsConceded =
                parseInteger(
                        value(record, "goals_conceded"),
                        "Gol subiti",
                        false
                );

        ParsedInteger cleanSheets =
                parseInteger(
                        value(record, "clean_sheets"),
                        "Clean sheet",
                        false
                );

        ParsedInteger penaltiesSaved =
                parseInteger(
                        value(record, "penalties_saved"),
                        "Rigori parati",
                        false
                );

        ParsedInteger saves =
                parseInteger(
                        value(record, "saves"),
                        "Parate",
                        false
                );

        ParsedDecimal expectedGoals =
                parseDecimal(
                        value(record, "expected_goals"),
                        "Expected goals",
                        BigDecimal.ZERO,
                        null
                );

        ParsedDecimal expectedAssists =
                parseDecimal(
                        value(record, "expected_assists"),
                        "Expected assists",
                        BigDecimal.ZERO,
                        null
                );

        addError(errors, appearances.error());
        addError(errors, starts.error());
        addError(errors, minutesPlayed.error());
        addError(errors, averageRating.error());
        addError(errors, fantasyAverage.error());
        addError(errors, goals.error());
        addError(errors, assists.error());
        addError(errors, penaltiesScored.error());
        addError(errors, penaltiesTaken.error());
        addError(errors, penaltiesMissed.error());
        addError(errors, yellowCards.error());
        addError(errors, redCards.error());
        addError(errors, ownGoals.error());
        addError(errors, goalsConceded.error());
        addError(errors, cleanSheets.error());
        addError(errors, penaltiesSaved.error());
        addError(errors, saves.error());
        addError(errors, expectedGoals.error());
        addError(errors, expectedAssists.error());

        Integer safeAppearances = appearances.value();
        Integer safeStarts = starts.value();

        if (safeStarts != null && safeAppearances != null
                && safeStarts > safeAppearances) {
            errors.add(
                    "Le titolarità non possono superare le presenze"
            );
        }

        String rowKey =
                source
                        + "|"
                        + normalizeKey(externalId);

        if (!isBlank(externalId)
                && !rowKeys.add(rowKey)) {

            errors.add(
                    "Giocatore duplicato nel file"
            );
        }

        Long playerId = null;
        Long clubId = null;

        String matchMethod = value(record, "match_method");
        String matchConfidence = value(record, "match_confidence");

        if (errors.stream().noneMatch(error -> error.contains("mancante"))) {
            if ("FBREF".equals(source)) {
                playerId = resolveFbrefPlayerId(
                        externalId,
                        firstNonBlank(value(record, "player_id"), value(record, "candidate_player_id")),
                        value(record, "match_status"),
                        matchMethod,
                        matchConfidence,
                        normalizedName,
                        errors
                );
                if (historicalClubs.size() == 1) {
                    clubId = resolveClubIdOptional(historicalClubs.getFirst());
                }
            }
            else {
                playerId = resolvePlayerId(source, externalId, normalizedName, errors);
                clubId = resolveClubId(clubName, normalizedClubName, errors);
            }
        }

        String validationError =
                errors.isEmpty()
                        ? null
                        : String.join(
                                "; ",
                                errors
                        );

        return new ImportedStatisticsRow(
                rowNumber,
                externalId,
                externalName,
                normalizedName,
                clubName,
                normalizedClubName,
                historicalClubs,
                matchMethod,
                matchConfidence,
                playerId,
                clubId,
                safeAppearances,
                safeStarts,
                minutesPlayed.value(),
                averageRating.value(),
                fantasyAverage.value(),
                goals.value(),
                assists.value(),
                penaltiesTaken.value(),
                penaltiesScored.value(),
                penaltiesMissed.value(),
                yellowCards.value(),
                redCards.value(),
                ownGoals.value(),
                goalsConceded.value(),
                cleanSheets.value(),
                saves.value(),
                penaltiesSaved.value(),
                expectedGoals.value(),
                expectedAssists.value(),
                errors.isEmpty(),
                validationError,
                toJson(
                        rawData(
                                parser,
                                record
                        )
                )
        );
    }

    private Long resolvePlayerId(
            String source,
            String externalId,
            String normalizedName,
            List<String> errors
    ) {
        Optional<Long> mappedPlayerId =
                jdbcClient.sql("""
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

        if (mappedPlayerId.isPresent()) {
            return mappedPlayerId.get();
        }

        List<Long> sameNamePlayers =
                jdbcClient.sql("""
                        SELECT id
                        FROM players
                        WHERE normalized_name = :normalizedName
                          AND active = TRUE
                        ORDER BY id
                        """)
                        .param(
                                "normalizedName",
                                normalizedName
                        )
                        .query(Long.class)
                        .list();

        if (sameNamePlayers.size() == 1) {
            return sameNamePlayers.getFirst();
        }

        if (sameNamePlayers.isEmpty()) {
            errors.add(
                    "Giocatore non trovato"
            );
        }
        else {
            errors.add(
                    "Giocatore non identificabile univocamente"
            );
        }

        return null;
    }

    private Long resolveFbrefPlayerId(
            String externalId,
            String suppliedPlayerId,
            String matchStatus,
            String matchMethod,
            String matchConfidence,
            String normalizedExternalName,
            List<String> errors
    ) {
        boolean safeMatch = "MATCHED".equalsIgnoreCase(matchStatus)
                && "HIGH".equalsIgnoreCase(matchConfidence)
                && ("EXACT_FULL_NAME".equals(matchMethod)
                    || "CROSS_SEASON_EXACT_ANCHOR".equals(matchMethod)
                    || "MANUAL_VERIFIED".equals(matchMethod));
        if (!safeMatch) {
            errors.add("Match FBref non certificato: richiesti MATCHED, HIGH e metodo sicuro");
            return null;
        }

        Optional<Long> linkedPlayer = jdbcClient.sql("""
                SELECT player_id
                FROM player_statistical_source_links
                WHERE source = 'FBREF' AND external_id = :externalId
                """)
                .param("externalId", externalId)
                .query(Long.class)
                .optional();

        if (linkedPlayer.isPresent()) {
            if (!isBlank(suppliedPlayerId)
                    && !suppliedPlayerId.equals(linkedPlayer.get().toString())) {
                errors.add("FBref external_id già collegato a un altro player_id");
            }
            return linkedPlayer.get();
        }

        if (isBlank(suppliedPlayerId)) {
            errors.add("player_id certificato mancante per FBref");
            return null;
        }

        Long playerId;
        try {
            playerId = Long.valueOf(suppliedPlayerId);
        }
        catch (NumberFormatException exception) {
            errors.add("player_id FBref non valido: " + suppliedPlayerId);
            return null;
        }

        boolean playerExists = jdbcClient.sql("SELECT EXISTS (SELECT 1 FROM players WHERE id = :playerId)")
                .param("playerId", playerId)
                .query(Boolean.class)
                .single();
        if (!playerExists) {
            errors.add("player_id FBref inesistente: " + playerId);
            return null;
        }

        if ("EXACT_FULL_NAME".equals(matchMethod)) {
            String catalogName = jdbcClient.sql("SELECT normalized_name FROM players WHERE id = :playerId")
                    .param("playerId", playerId)
                    .query(String.class)
                    .single();
            if (!textNormalizer.normalize(catalogName).equals(normalizedExternalName)
                    || normalizedExternalName.split(" ").length < 2) {
                errors.add("EXACT_FULL_NAME non coerente con il nome completo del catalogo");
                return null;
            }
        }

        Optional<String> otherExternalId = jdbcClient.sql("""
                SELECT external_id
                FROM player_statistical_source_links
                WHERE source = 'FBREF' AND player_id = :playerId
                  AND external_id <> :externalId
                """)
                .param("playerId", playerId)
                .param("externalId", externalId)
                .query(String.class)
                .optional();
        if (otherExternalId.isPresent()) {
            errors.add("player_id già collegato al differente FBref ID " + otherExternalId.get());
            return null;
        }

        return playerId;
    }

    private Long resolveClubIdOptional(String clubName) {
        String normalized = textNormalizer.normalize(clubName);
        List<Long> clubIds = jdbcClient.sql("""
                SELECT id FROM clubs
                WHERE LOWER(name) = LOWER(:clubName)
                   OR LOWER(slug) = LOWER(:clubName)
                   OR LOWER(REPLACE(name, ' ', '')) = LOWER(REPLACE(:normalizedClub, ' ', ''))
                ORDER BY id
                """)
                .param("clubName", clubName)
                .param("normalizedClub", normalized)
                .query(Long.class)
                .list();
        return clubIds.size() == 1 ? clubIds.getFirst() : null;
    }

    private Long resolveClubId(
            String clubName,
            String normalizedClubName,
            List<String> errors
    ) {
        List<Long> clubIds =
                jdbcClient.sql("""
                        SELECT id
                        FROM clubs
                        WHERE LOWER(name) = LOWER(:clubName)
                           OR LOWER(slug) = LOWER(:clubName)
                           OR LOWER(REPLACE(name, ' ', '')) =
                              LOWER(REPLACE(:normalizedClub, ' ', ''))
                        ORDER BY id
                        """)
                        .param("clubName", clubName)
                        .param(
                                "normalizedClub",
                                normalizedClubName
                        )
                        .query(Long.class)
                        .list();

        if (clubIds.size() == 1) {
            return clubIds.getFirst();
        }

        if (clubIds.isEmpty()) {
            errors.add(
                    "Squadra non trovata: " + clubName
            );
        }
        else {
            errors.add(
                    "Squadra non identificabile univocamente: "
                            + clubName
            );
        }

        return null;
    }

    private void upsertStatistics(
            ImportedStatisticsRow row,
            Long seasonId,
            String source,
            Long batchId
    ) {
        if ("FBREF".equals(source)) {
            upsertStatisticalLink(row);
        }

        Long statisticsId = jdbcClient.sql("""
                INSERT INTO player_season_statistics (
                    player_id,
                    club_id,
                    season_id,
                    source,
                    source_external_id,
                    appearances,
                    starts,
                    minutes_played,
                    average_rating,
                    fantasy_average,
                    goals,
                    assists,
                    penalties_scored,
                    penalties_missed,
                    penalties_taken,
                    yellow_cards,
                    red_cards,
                    own_goals,
                    goals_conceded,
                    clean_sheets,
                    saves,
                    penalties_saved,
                    expected_goals,
                    expected_assists,
                    import_batch_id
                )
                VALUES (
                    :playerId,
                    :clubId,
                    :seasonId,
                    :source,
                    :sourceExternalId,
                    :appearances,
                    :starts,
                    :minutesPlayed,
                    :averageRating,
                    :fantasyAverage,
                    :goals,
                    :assists,
                    :penaltiesScored,
                    :penaltiesMissed,
                    :penaltiesTaken,
                    :yellowCards,
                    :redCards,
                    :ownGoals,
                    :goalsConceded,
                    :cleanSheets,
                    :saves,
                    :penaltiesSaved,
                    :expectedGoals,
                    :expectedAssists,
                    :batchId
                )
                ON CONFLICT (
                    player_id,
                    season_id,
                    source
                )
                DO UPDATE SET
                    club_id = EXCLUDED.club_id,
                    source_external_id = EXCLUDED.source_external_id,
                    appearances = EXCLUDED.appearances,
                    starts = EXCLUDED.starts,
                    minutes_played = EXCLUDED.minutes_played,
                    average_rating = EXCLUDED.average_rating,
                    fantasy_average = EXCLUDED.fantasy_average,
                    goals = EXCLUDED.goals,
                    assists = EXCLUDED.assists,
                    penalties_scored =
                        EXCLUDED.penalties_scored,
                    penalties_missed =
                        EXCLUDED.penalties_missed,
                    penalties_taken = EXCLUDED.penalties_taken,
                    yellow_cards = EXCLUDED.yellow_cards,
                    red_cards = EXCLUDED.red_cards,
                    own_goals = EXCLUDED.own_goals,
                    goals_conceded = EXCLUDED.goals_conceded,
                    clean_sheets = EXCLUDED.clean_sheets,
                    saves = EXCLUDED.saves,
                    penalties_saved = EXCLUDED.penalties_saved,
                    expected_goals = EXCLUDED.expected_goals,
                    expected_assists = EXCLUDED.expected_assists,
                    import_batch_id = EXCLUDED.import_batch_id,
                    updated_at = CURRENT_TIMESTAMP
                RETURNING id
                """)
                .param("playerId", row.playerId())
                .param("clubId", row.clubId())
                .param("seasonId", seasonId)
                .param("source", source)
                .param("sourceExternalId", row.externalId())
                .param("batchId", batchId)
                .param("appearances", row.appearances(), Types.INTEGER)
                .param("starts", row.starts(), Types.INTEGER)
                .param("minutesPlayed", row.minutesPlayed(), Types.INTEGER)
                .param(
                        "averageRating",
                        row.averageRating(),
                        Types.NUMERIC
                )
                .param(
                        "fantasyAverage",
                        row.fantasyAverage(),
                        Types.NUMERIC
                )
                .param("goals", row.goals(), Types.INTEGER)
                .param("assists", row.assists(), Types.INTEGER)
                .param(
                        "penaltiesTaken",
                        row.penaltiesTaken(),
                        Types.INTEGER
                )
                .param(
                        "penaltiesScored",
                        row.penaltiesScored(),
                        Types.INTEGER
                )
                .param(
                        "penaltiesMissed",
                        row.penaltiesMissed(),
                        Types.INTEGER
                )
                .param(
                        "yellowCards",
                        row.yellowCards(),
                        Types.INTEGER
                )
                .param(
                        "redCards",
                        row.redCards(),
                        Types.INTEGER
                )
                .param("ownGoals", row.ownGoals(), Types.INTEGER)
                .param(
                        "goalsConceded",
                        row.goalsConceded(),
                        Types.INTEGER
                )
                .param(
                        "cleanSheets",
                        row.cleanSheets(),
                        Types.INTEGER
                )
                .param(
                        "saves",
                        row.saves(),
                        Types.INTEGER
                )
                .param(
                        "penaltiesSaved",
                        row.penaltiesSaved(),
                        Types.INTEGER
                )
                .param(
                        "expectedGoals",
                        row.expectedGoals(),
                        Types.NUMERIC
                )
                .param(
                        "expectedAssists",
                        row.expectedAssists(),
                        Types.NUMERIC
                )
                .query(Long.class)
                .single();

        if ("FBREF".equals(source)) {
            replaceHistoricalClubs(statisticsId, row.historicalClubs());
        }
    }

    private void upsertStatisticalLink(ImportedStatisticsRow row) {
        jdbcClient.sql("""
                INSERT INTO player_statistical_source_links (
                    player_id, source, external_id, external_name,
                    match_method, confidence, evidence
                ) VALUES (
                    :playerId, 'FBREF', :externalId, :externalName,
                    :matchMethod, 'HIGH', CAST(:evidence AS JSONB)
                )
                ON CONFLICT (source, external_id) DO UPDATE SET
                    external_name = EXCLUDED.external_name,
                    updated_at = CURRENT_TIMESTAMP
                """)
                .param("playerId", row.playerId())
                .param("externalId", row.externalId())
                .param("externalName", row.externalName())
                .param("matchMethod", row.matchMethod())
                .param("evidence", row.rawData())
                .update();
    }

    private void replaceHistoricalClubs(Long statisticsId, List<String> clubs) {
        jdbcClient.sql("DELETE FROM player_season_statistics_clubs WHERE statistics_id = :statisticsId")
                .param("statisticsId", statisticsId)
                .update();
        for (int index = 0; index < clubs.size(); index++) {
            String club = clubs.get(index);
            jdbcClient.sql("""
                    INSERT INTO player_season_statistics_clubs (
                        statistics_id, club_name, normalized_club_name, source_order
                    ) VALUES (:statisticsId, :clubName, :normalizedClubName, :sourceOrder)
                    """)
                    .param("statisticsId", statisticsId)
                    .param("clubName", club)
                    .param("normalizedClubName", textNormalizer.normalize(club))
                    .param("sourceOrder", index + 1)
                    .update();
        }
    }

    private void saveImportedRow(
            Long batchId,
            ImportedStatisticsRow row
    ) {
        jdbcClient.sql("""
                INSERT INTO player_statistics_import_rows (
                    batch_id,
                    row_number,
                    external_id,
                    external_name,
                    normalized_name,
                    club_name,
                    normalized_club_name,
                    resolved_player_id,
                    resolved_club_id,
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
                    :playerId,
                    :clubId,
                    :valid,
                    :validationError,
                    CAST(:rawData AS JSONB)
                )
                """)
                .param("batchId", batchId)
                .param("rowNumber", row.rowNumber())
                .param(
                        "externalId",
                        row.externalId(),
                        Types.VARCHAR
                )
                .param(
                        "externalName",
                        row.externalName(),
                        Types.VARCHAR
                )
                .param(
                        "normalizedName",
                        row.normalizedName(),
                        Types.VARCHAR
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
                        "playerId",
                        row.playerId(),
                        Types.BIGINT
                )
                .param(
                        "clubId",
                        row.clubId(),
                        Types.BIGINT
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

    private Long createBatch(
            String source,
            Long seasonId,
            String filename
    ) {
        return Objects.requireNonNull(
                requiresNewTransaction.execute(
                        transactionStatus ->
                                jdbcClient.sql("""
                                        INSERT INTO player_statistics_import_batches (
                                            source,
                                            season_id,
                                            original_filename,
                                            status
                                        )
                                        VALUES (
                                            :source,
                                            :seasonId,
                                            :filename,
                                            'PROCESSING'
                                        )
                                        RETURNING id
                                        """)
                                        .param("source", source)
                                        .param("seasonId", seasonId)
                                        .param("filename", filename)
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
            int importedStatistics
    ) {
        jdbcClient.sql("""
                UPDATE player_statistics_import_batches
                SET status = 'COMPLETED',
                    total_rows = :totalRows,
                    valid_rows = :validRows,
                    invalid_rows = :invalidRows,
                    imported_statistics = :importedStatistics,
                    completed_at = CURRENT_TIMESTAMP,
                    error_message = NULL
                WHERE id = :batchId
                """)
                .param("totalRows", totalRows)
                .param("validRows", validRows)
                .param("invalidRows", invalidRows)
                .param(
                        "importedStatistics",
                        importedStatistics
                )
                .param("batchId", batchId)
                .update();
    }

    private void markBatchFailed(
            Long batchId,
            String errorMessage
    ) {
        requiresNewTransaction.executeWithoutResult(
                transactionStatus ->
                        jdbcClient.sql("""
                                UPDATE player_statistics_import_batches
                                SET status = 'FAILED',
                                    error_message = :errorMessage,
                                    completed_at = CURRENT_TIMESTAMP
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

    private Long findSeasonId(String seasonCode) {
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

    private void validateHeaders(List<String> headers, String source) {
        Set<String> normalizedHeaders =
                new HashSet<>();

        for (String header : headers) {
            if (header != null) {
                normalizedHeaders.add(
                        header.trim()
                                .toLowerCase(Locale.ROOT)
                );
            }
        }

        List<String> missingColumns =
                java.util.stream.Stream.concat(
                        REQUIRED_COLUMNS.stream(),
                        ("FBREF".equals(source)
                                ? Set.of("clubs_played", "candidate_player_id", "match_status",
                                        "match_method", "match_confidence")
                                : Set.of("club")).stream())
                        .filter(column ->
                                !normalizedHeaders.contains(column)
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

    private String normalizeSource(String source) {
        if (source == null || source.isBlank()) {
            throw new BadRequestException(
                    "Fonte obbligatoria"
            );
        }

        String normalized =
                source.trim()
                        .toUpperCase(Locale.ROOT);

        if (!ALLOWED_SOURCES.contains(normalized)) {
            throw new BadRequestException(
                    "Fonte non valida: " + source
            );
        }

        return normalized;
    }

    private String validateSeasonCode(String seasonCode) {
        if (seasonCode == null || seasonCode.isBlank()) {
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

    private String resolveFilename(CsvImportPayload file) {
        String filename =
                file.filename();

        if (filename == null || filename.isBlank()) {
            return "player-statistics.csv";
        }

        String normalized =
                filename.replace("\\", "/");

        int separator =
                normalized.lastIndexOf('/');

        if (separator >= 0) {
            normalized =
                    normalized.substring(separator + 1);
        }

        return truncate(normalized, 255);
    }

    private String value(
            CSVRecord record,
            String column
    ) {
        if (!record.isMapped(column)) {
            return null;
        }

        String rawValue =
                record.get(column);

        return rawValue == null || rawValue.isBlank()
                ? null
                : rawValue.trim();
    }

    private List<String> parseHistoricalClubs(String clubsPlayed, String clubName) {
        String raw = firstNonBlank(clubsPlayed, clubName);
        if (isBlank(raw)) {
            return List.of();
        }
        LinkedHashSet<String> clubs = new LinkedHashSet<>();
        Arrays.stream(raw.split("\\|"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .forEach(clubs::add);
        return List.copyOf(clubs);
    }

    private String firstNonBlank(String first, String second) {
        return isBlank(first) ? second : first;
    }

    private ParsedInteger parseInteger(
            String rawValue,
            String fieldName,
            boolean required
    ) {
        if (rawValue == null) {
            if (required) {
                return new ParsedInteger(
                        null,
                        fieldName + " obbligatorie"
                );
            }

            return new ParsedInteger(null, null);
        }

        try {
            int parsed =
                    Integer.parseInt(rawValue);

            if (parsed < 0) {
                return new ParsedInteger(
                        null,
                        fieldName
                                + " non può essere negativo"
                );
            }

            return new ParsedInteger(parsed, null);
        }
        catch (NumberFormatException exception) {
            return new ParsedInteger(
                    null,
                    fieldName
                            + " non valido: "
                            + rawValue
            );
        }
    }

    private ParsedDecimal parseDecimal(
            String rawValue,
            String fieldName,
            BigDecimal minimum,
            BigDecimal maximum
    ) {
        if (rawValue == null) {
            return new ParsedDecimal(null, null);
        }

        try {
            BigDecimal parsed =
                    new BigDecimal(
                            rawValue.replace(",", ".")
                    );

            if (minimum != null
                    && parsed.compareTo(minimum) < 0) {

                return new ParsedDecimal(
                        null,
                        fieldName
                                + " inferiore al minimo consentito"
                );
            }

            if (maximum != null
                    && parsed.compareTo(maximum) > 0) {

                return new ParsedDecimal(
                        null,
                        fieldName
                                + " superiore al massimo consentito"
                );
            }

            return new ParsedDecimal(parsed, null);
        }
        catch (NumberFormatException exception) {
            return new ParsedDecimal(
                    null,
                    fieldName
                            + " non valido: "
                            + rawValue
            );
        }
    }

    private Map<String, String> rawData(
            CSVParser parser,
            CSVRecord record
    ) {
        Map<String, String> data =
                new LinkedHashMap<>();

        for (String header : parser.getHeaderNames()) {
            data.put(
                    header,
                    record.isMapped(header)
                            ? record.get(header)
                            : null
            );
        }

        return data;
    }

    private String toJson(
            Map<String, String> data
    ) {
        try {
            return jsonMapper.writeValueAsString(data);
        }
        catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Impossibile convertire la riga in JSON",
                    exception
            );
        }
    }

    private void addError(
            List<String> errors,
            String error
    ) {
        if (error != null) {
            errors.add(error);
        }
    }

    private String normalizeKey(String value) {
        return value == null
                ? ""
                : value.trim()
                        .toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String readableErrorMessage(
            Throwable exception
    ) {
        return SecretSanitizer.sanitize(exception);
    }

    private String truncate(
            String value,
            int maximumLength
    ) {
        if (value == null
                || value.length() <= maximumLength) {
            return value;
        }

        return value.substring(
                0,
                maximumLength
        );
    }

    private record ParsedInteger(
            Integer value,
            String error
    ) {
    }

    private record ParsedDecimal(
            BigDecimal value,
            String error
    ) {
    }

    private record ImportedStatisticsRow(
            int rowNumber,
            String externalId,
            String externalName,
            String normalizedName,
            String clubName,
            String normalizedClubName,
            List<String> historicalClubs,
            String matchMethod,
            String matchConfidence,
            Long playerId,
            Long clubId,
            Integer appearances,
            Integer starts,
            Integer minutesPlayed,
            BigDecimal averageRating,
            BigDecimal fantasyAverage,
            Integer goals,
            Integer assists,
            Integer penaltiesTaken,
            Integer penaltiesScored,
            Integer penaltiesMissed,
            Integer yellowCards,
            Integer redCards,
            Integer ownGoals,
            Integer goalsConceded,
            Integer cleanSheets,
            Integer saves,
            Integer penaltiesSaved,
            BigDecimal expectedGoals,
            BigDecimal expectedAssists,
            boolean valid,
            String validationError,
            String rawData
    ) {
    }
}
