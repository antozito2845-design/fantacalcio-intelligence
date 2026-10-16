package it.fantacalciointelligence.backend.ingestion;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import it.fantacalciointelligence.backend.common.BadRequestException;
import it.fantacalciointelligence.backend.common.ConflictException;
import it.fantacalciointelligence.backend.common.ResourceNotFoundException;
import it.fantacalciointelligence.backend.common.SecretSanitizer;
import it.fantacalciointelligence.backend.importer.CsvImportPayload;
import it.fantacalciointelligence.backend.importer.PlayerImportResponse;
import it.fantacalciointelligence.backend.importer.PlayerImportService;

@Service
public class FantacalcioListoneSyncService {

    static final String FEED_ID = "fantacalcio-listone-html";
    static final String SOURCE = "FANTACALCIO";

    private final FantacalcioListoneProperties properties;
    private final FantacalcioListoneClient client;
    private final FantacalcioListoneParser parser;
    private final PlayerImportService playerImportService;
    private final JdbcClient jdbc;

    public FantacalcioListoneSyncService(
            FantacalcioListoneProperties properties,
            FantacalcioListoneClient client,
            FantacalcioListoneParser parser,
            PlayerImportService playerImportService,
            JdbcClient jdbc
    ) {
        this.properties = properties;
        this.client = client;
        this.parser = parser;
        this.playerImportService = playerImportService;
        this.jdbc = jdbc;
    }

    public FantacalcioListoneSyncResponse sync(String season) {
        if (!properties.isEnabled()) {
            throw new ConflictException("Fantacalcio listone sync disabilitato");
        }

        long seasonId = seasonId(season);
        long runId = start(seasonId);

        try {
            FantacalcioListoneClient.FetchResult fetched = client.fetch();
            List<FantacalcioListoneParser.ListonePlayer> parsed =
                    parser.parse(fetched.html());

            Map<String, String> clubNamesBySlug = currentSeasonClubs(seasonId);
            byte[] csv = toCsv(parsed, clubNamesBySlug);
            String checksum = sha256(csv);

            Optional<String> previous = latestEffectiveChecksum(
                    seasonId,
                    runId
            );

            if (previous.filter(checksum::equals).isPresent()) {
                finish(
                        runId,
                        "SKIPPED_UNCHANGED",
                        fetched.httpStatus(),
                        fetched.payloadBytes(),
                        checksum,
                        null,
                        null
                );

                return response(runId, season, parsed.size());
            }

            String filename = "players-fantacalcio-" + season.replace("/", "-") + ".csv";
            PlayerImportResponse imported = playerImportService.importPayload(
                    SOURCE,
                    season,
                    new CsvImportPayload(filename, csv)
            );

            jdbc.sql("""
                    UPDATE import_batches
                    SET source_observed_at = CURRENT_TIMESTAMP
                    WHERE id = :id
                    """)
                    .param("id", imported.batchId())
                    .update();

            finish(
                    runId,
                    "COMPLETED",
                    fetched.httpStatus(),
                    fetched.payloadBytes(),
                    checksum,
                    imported.batchId(),
                    null
            );

            return response(runId, season, parsed.size());
        } catch (RuntimeException exception) {
            finish(
                    runId,
                    "FAILED",
                    null,
                    null,
                    null,
                    null,
                    SecretSanitizer.sanitize(exception)
            );
            throw exception;
        }
    }

    private byte[] toCsv(
            List<FantacalcioListoneParser.ListonePlayer> players,
            Map<String, String> clubNamesBySlug
    ) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();

            try (
                    OutputStreamWriter writer = new OutputStreamWriter(
                            output,
                            StandardCharsets.UTF_8
                    );
                    CSVPrinter printer = CSVFormat.DEFAULT.builder()
                            .setHeader(
                                    "external_id",
                                    "name",
                                    "club",
                                    "role",
                                    "quotation",
                                    "fmv",
                                    "out_of_game"
                            )
                            .get()
                            .print(writer)
            ) {
                for (FantacalcioListoneParser.ListonePlayer player : players) {
                    String club = clubNamesBySlug.get(player.clubSlug());
                    if (club == null) {
                        throw new BadRequestException(
                                "Club Fantacalcio non riconosciuto per la stagione: "
                                        + player.clubSlug()
                        );
                    }

                    printer.printRecord(
                            player.externalId(),
                            player.name(),
                            club,
                            player.role(),
                            number(player.quotation()),
                            number(player.fmv()),
                            Boolean.toString(player.outOfGame())
                    );
                }
            }

            return output.toByteArray();
        } catch (BadRequestException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BadRequestException(
                    "Conversione listone Fantacalcio in CSV fallita",
                    exception
            );
        }
    }

    private Object number(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private Map<String, String> currentSeasonClubs(long seasonId) {
        List<String> clubs = jdbc.sql("""
                SELECT club.name
                FROM season_clubs membership
                JOIN clubs club ON club.id = membership.club_id
                WHERE membership.season_id = :seasonId
                """)
                .param("seasonId", seasonId)
                .query(String.class)
                .list();

        Map<String, String> result = new HashMap<>();
        for (String club : clubs) {
            result.put(slugify(club), club);
        }

        // Fantacalcio URL conventions / common aliases.
        alias(result, "inter", "Internazionale");
        alias(result, "milan", "Milan");
        alias(result, "roma", "Roma");
        alias(result, "lazio", "Lazio");

        return result;
    }

    private void alias(Map<String, String> map, String slug, String expectedName) {
        if (map.containsKey(slug)) {
            return;
        }
        map.values().stream()
                .filter(value -> value.equalsIgnoreCase(expectedName))
                .findFirst()
                .ifPresent(value -> map.put(slug, value));
    }

    static String slugify(String value) {
        String normalized = java.text.Normalizer
                .normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replace("&", " ")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return normalized;
    }

    private long start(long seasonId) {
        recoverStale(seasonId);
        try {
            return jdbc.sql("""
                    INSERT INTO source_ingestion_runs (
                        feed_id,
                        feed_type,
                        source,
                        season_id,
                        sanitized_url,
                        status
                    )
                    VALUES (
                        :feed,
                        'PLAYERS',
                        :source,
                        :season,
                        :url,
                        'STARTED'
                    )
                    RETURNING id
                    """)
                    .param("feed", FEED_ID)
                    .param("source", SOURCE)
                    .param("season", seasonId)
                    .param("url", properties.getUrl())
                    .query(Long.class)
                    .single();
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException(
                    "Sync listone Fantacalcio gia' in esecuzione"
            );
        }
    }

    private void recoverStale(long seasonId) {
        jdbc.sql("""
                UPDATE source_ingestion_runs
                SET status = 'FAILED',
                    completed_at = CURRENT_TIMESTAMP,
                    error_message = 'Sync listone interrotta recuperata automaticamente'
                WHERE feed_id = :feed
                  AND season_id = :season
                  AND status = 'STARTED'
                  AND started_at <
                      CURRENT_TIMESTAMP
                      - (:staleSeconds * INTERVAL '1 second')
                """)
                .param("feed", FEED_ID)
                .param("season", seasonId)
                .param(
                        "staleSeconds",
                        properties.getStaleThreshold().toSeconds()
                )
                .update();
    }

    private Optional<String> latestEffectiveChecksum(
            long seasonId,
            long runId
    ) {
        return jdbc.sql("""
                SELECT payload_sha256
                FROM source_ingestion_runs
                WHERE feed_id = :feed
                  AND season_id = :season
                  AND status IN ('COMPLETED', 'SKIPPED_UNCHANGED')
                  AND id < :runId
                ORDER BY id DESC
                LIMIT 1
                """)
                .param("feed", FEED_ID)
                .param("season", seasonId)
                .param("runId", runId)
                .query(String.class)
                .optional();
    }

    private void finish(
            long runId,
            String status,
            Integer httpStatus,
            Long payloadBytes,
            String checksum,
            Long importBatchId,
            String error
    ) {
        jdbc.sql("""
                UPDATE source_ingestion_runs
                SET status = :status,
                    http_status = :httpStatus,
                    payload_bytes = :payloadBytes,
                    payload_sha256 = :checksum,
                    import_batch_id = :importBatchId,
                    error_message = :error,
                    completed_at = CURRENT_TIMESTAMP
                WHERE id = :runId
                """)
                .param("status", status)
                .param("httpStatus", httpStatus, java.sql.Types.INTEGER)
                .param("payloadBytes", payloadBytes, java.sql.Types.BIGINT)
                .param("checksum", checksum, java.sql.Types.VARCHAR)
                .param("importBatchId", importBatchId, java.sql.Types.BIGINT)
                .param(
                        "error",
                        error == null ? null : truncate(error, 2000),
                        java.sql.Types.VARCHAR
                )
                .param("runId", runId)
                .update();
    }

    private FantacalcioListoneSyncResponse response(
            long runId,
            String season,
            int parsedPlayers
    ) {
        return jdbc.sql("""
                SELECT id,
                       status,
                       payload_sha256,
                       import_batch_id,
                       started_at,
                       completed_at
                FROM source_ingestion_runs
                WHERE id = :id
                """)
                .param("id", runId)
                .query((rs, rowNum) -> mapResponse(
                        rs,
                        season,
                        parsedPlayers
                ))
                .single();
    }

    private FantacalcioListoneSyncResponse mapResponse(
            ResultSet rs,
            String season,
            int parsedPlayers
    ) throws SQLException {
        return new FantacalcioListoneSyncResponse(
                rs.getLong("id"),
                season,
                rs.getString("status"),
                parsedPlayers,
                (Long) rs.getObject("import_batch_id"),
                rs.getString("payload_sha256"),
                rs.getObject("started_at", OffsetDateTime.class),
                rs.getObject("completed_at", OffsetDateTime.class)
        );
    }

    private long seasonId(String code) {
        return jdbc.sql("""
                SELECT id
                FROM seasons
                WHERE code = :code
                """)
                .param("code", code)
                .query(Long.class)
                .optional()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Stagione non trovata: " + code
                ));
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value)
            );
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}