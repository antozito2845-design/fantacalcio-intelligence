package it.fantacalciointelligence.backend.ingestion;

import java.net.URI;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import it.fantacalciointelligence.backend.common.BadRequestException;
import it.fantacalciointelligence.backend.common.SecretSanitizer;
import it.fantacalciointelligence.backend.common.ConflictException;
import it.fantacalciointelligence.backend.common.ResourceNotFoundException;
import it.fantacalciointelligence.backend.fixture.importer.FixtureImportService;
import it.fantacalciointelligence.backend.importer.CsvImportPayload;
import it.fantacalciointelligence.backend.importer.PlayerImportService;
import it.fantacalciointelligence.backend.statistics.importer.PlayerStatisticsImportService;

@Service
public class SourceIngestionService {

    private static final Logger LOG = LoggerFactory.getLogger(SourceIngestionService.class);
    private static final Pattern AUTHORIZATION = Pattern.compile(
            "(?i)\\bauthorization\\s*[:=]\\s*[^\\r\\n]*"
    );
    private static final Pattern POSTGRESQL_JDBC_URL = Pattern.compile(
            "(?i)\\bjdbc:postgresql://[^\\s,;]+"
    );
    private static final Pattern HTTP_URL = Pattern.compile(
            "(?i)\\bhttps?://[^\\s,;]+"
    );
    private static final Pattern SENSITIVE_VALUE = Pattern.compile(
            "(?i)\\b(password|passwd|pwd|token|api[_-]?key|apikey|secret)"
                    + "(\\s*[:=]\\s*)(?:\"[^\"]*\"|'[^']*'|[^\\s,;]+)"
    );

    private final SourceIngestionProperties config;
    private final RemoteCsvClient remote;
    private final JdbcClient jdbc;
    private final PlayerImportService players;
    private final PlayerStatisticsImportService statistics;
    private final FixtureImportService fixtures;

    public SourceIngestionService(
            SourceIngestionProperties config,
            RemoteCsvClient remote,
            JdbcClient jdbc,
            PlayerImportService players,
            PlayerStatisticsImportService statistics,
            FixtureImportService fixtures
    ) {
        this.config = config;
        this.remote = remote;
        this.jdbc = jdbc;
        this.players = players;
        this.statistics = statistics;
        this.fixtures = fixtures;
    }

    public List<SourceIngestionResponse> ingestAll(String season) {
        requireEnabled();
        List<SourceIngestionProperties.Feed> feeds = config.getFeeds().stream()
                .filter(SourceIngestionProperties.Feed::isEnabled)
                .filter(feed -> season.equals(feed.getSeason()))
                .toList();
        if (feeds.isEmpty()) {
            throw new BadRequestException(
                    "Nessun feed abilitato configurato per la stagione richiesta"
            );
        }
        return feeds.stream().map(this::ingest).toList();
    }

    public SourceIngestionResponse ingestOne(String id) {
        requireEnabled();
        return ingest(findFeed(id));
    }

    public List<SourceIngestionResponse> latest(String season) {
        return jdbc.sql(baseSelect() + """
                JOIN seasons season ON season.id = run.season_id
                WHERE season.code = :season
                  AND run.id IN (
                      SELECT MAX(r.id)
                      FROM source_ingestion_runs r
                      WHERE r.season_id = season.id
                      GROUP BY r.feed_id
                  )
                ORDER BY run.feed_id
                """)
                .param("season", season)
                .query((resultSet, rowNumber) -> map(
                        resultSet,
                        isRequired(resultSet.getString("feed_id"))
                ))
                .list();
    }

    private SourceIngestionResponse ingest(SourceIngestionProperties.Feed feed) {
        validateFeed(feed);
        long seasonId = seasonId(feed.getSeason());
        long runId = start(feed, seasonId);
        try {
            RemoteCsvClient.FetchResult fetched = remote.fetch(feed.getUrl());
            String checksum = sha256(fetched.bytes());
            Optional<String> effectiveChecksum = latestEffectiveChecksum(
                    feed.getId(), seasonId, runId
            );
            if (effectiveChecksum.filter(checksum::equals).isPresent()) {
                finish(
                        runId,
                        "SKIPPED_UNCHANGED",
                        fetched.httpStatus(),
                        (long) fetched.bytes().length,
                        checksum,
                        null,
                        null
                );
                return find(runId, feed.isRequired());
            }

            String filename = feed.getFilename() == null || feed.getFilename().isBlank()
                    ? fetched.filename()
                    : feed.getFilename();
            CsvImportPayload payload = new CsvImportPayload(filename, fetched.bytes());
            SourceFeedType feedType = type(feed);
            long batchId = importPayload(feedType, feed, payload);
            observe(feedType, batchId);
            finish(
                    runId,
                    "COMPLETED",
                    fetched.httpStatus(),
                    (long) fetched.bytes().length,
                    checksum,
                    batchId,
                    null
            );
            LOG.info(
                    "Source ingestion feed={} source={} season={} status=COMPLETED "
                            + "bytes={} checksum={} batch={}",
                    feed.getId(),
                    feed.getSource(),
                    feed.getSeason(),
                    fetched.bytes().length,
                    checksum.substring(0, 12),
                    batchId
            );
            return find(runId, feed.isRequired());
        } catch (RuntimeException exception) {
            finish(runId, "FAILED", null, null, null, null, safe(exception));
            LOG.warn(
                    "Source ingestion feed={} source={} season={} status=FAILED",
                    feed.getId(),
                    feed.getSource(),
                    feed.getSeason()
            );
            return find(runId, feed.isRequired());
        }
    }

    private Optional<String> latestEffectiveChecksum(String feedId, long seasonId, long runId) {
        return jdbc.sql("""
                SELECT payload_sha256
                FROM source_ingestion_runs
                WHERE feed_id = :feed
                  AND season_id = :season
                  AND status IN ('COMPLETED', 'SKIPPED_UNCHANGED')
                  AND id < :run
                ORDER BY id DESC
                LIMIT 1
                """)
                .param("feed", feedId)
                .param("season", seasonId)
                .param("run", runId)
                .query(String.class)
                .optional();
    }

    private long importPayload(
            SourceFeedType feedType,
            SourceIngestionProperties.Feed feed,
            CsvImportPayload payload
    ) {
        return switch (feedType) {
            case PLAYERS -> players.importPayload(
                    feed.getSource(), feed.getSeason(), payload
            ).batchId();
            case STATISTICS -> statistics.importPayload(
                    feed.getSource(), feed.getSeason(), payload
            ).batchId();
            case FIXTURES -> fixtures.importPayload(
                    feed.getSource(), feed.getSeason(), payload
            ).batchId();
        };
    }

    long start(SourceIngestionProperties.Feed feed, long seasonId) {
        jdbc.sql("""
                UPDATE source_ingestion_runs
                SET status = 'FAILED', completed_at = CURRENT_TIMESTAMP,
                    error_message = 'Ingestion interrotta recuperata automaticamente'
                WHERE feed_id = :feed AND season_id = :season
                  AND status = 'STARTED'
                  AND started_at < CURRENT_TIMESTAMP - (:staleSeconds * INTERVAL '1 second')
                """)
                .param("feed", feed.getId())
                .param("season", seasonId)
                .param("staleSeconds", config.getStaleThreshold().toSeconds())
                .update();
        try {
            return jdbc.sql("""
                    INSERT INTO source_ingestion_runs (
                        feed_id, feed_type, source, season_id, sanitized_url, status
                    )
                    VALUES (:id, :type, :source, :season, :url, 'STARTED')
                    RETURNING id
                    """)
                    .param("id", feed.getId())
                    .param("type", type(feed).name())
                    .param("source", feed.getSource().trim().toUpperCase(Locale.ROOT))
                    .param("season", seasonId)
                    .param("url", RemoteCsvClient.sanitized(remote.validate(feed.getUrl())))
                    .query(Long.class)
                    .single();
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Ingestion feed già in esecuzione");
        }
    }

    private void finish(
            long id,
            String status,
            Integer httpStatus,
            Long payloadBytes,
            String checksum,
            Long batchId,
            String error
    ) {
        jdbc.sql("""
                UPDATE source_ingestion_runs
                SET status = :status,
                    http_status = :http,
                    payload_bytes = :bytes,
                    payload_sha256 = :sha,
                    import_batch_id = :batch,
                    error_message = :error,
                    completed_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """)
                .param("status", status)
                .param("http", httpStatus)
                .param("bytes", payloadBytes)
                .param("sha", checksum)
                .param("batch", batchId)
                .param("error", error)
                .param("id", id)
                .update();
    }

    private void observe(SourceFeedType feedType, long batchId) {
        String table = switch (feedType) {
            case PLAYERS -> "import_batches";
            case STATISTICS -> "player_statistics_import_batches";
            case FIXTURES -> "fixture_import_batches";
        };
        jdbc.sql("UPDATE " + table + " SET source_observed_at = CURRENT_TIMESTAMP WHERE id = :id")
                .param("id", batchId)
                .update();
    }

    private SourceIngestionResponse find(long id, boolean required) {
        return jdbc.sql(baseSelect() + """
                JOIN seasons season ON season.id = run.season_id
                WHERE run.id = :id
                """)
                .param("id", id)
                .query((resultSet, rowNumber) -> map(resultSet, required))
                .single();
    }

    private String baseSelect() {
        return """
                SELECT run.*, season.code AS season_code
                FROM source_ingestion_runs run
                """;
    }

    private SourceIngestionResponse map(ResultSet resultSet, boolean required) throws SQLException {
        return new SourceIngestionResponse(
                resultSet.getLong("id"),
                resultSet.getString("feed_id"),
                resultSet.getString("feed_type"),
                resultSet.getString("source"),
                resultSet.getString("season_code"),
                resultSet.getString("status"),
                (Integer) resultSet.getObject("http_status"),
                (Long) resultSet.getObject("payload_bytes"),
                resultSet.getString("payload_sha256"),
                (Long) resultSet.getObject("import_batch_id"),
                resultSet.getString("error_message"),
                resultSet.getObject("started_at", OffsetDateTime.class),
                resultSet.getObject("completed_at", OffsetDateTime.class),
                required
        );
    }

    private long seasonId(String code) {
        return jdbc.sql("SELECT id FROM seasons WHERE code = :code")
                .param("code", code)
                .query(Long.class)
                .optional()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Stagione non trovata: " + code
                ));
    }

    private SourceIngestionProperties.Feed findFeed(String id) {
        return config.getFeeds().stream()
                .filter(feed -> feed.isEnabled() && feed.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Feed configurato non trovato"
                ));
    }

    private boolean isRequired(String id) {
        return config.getFeeds().stream()
                .filter(feed -> id.equals(feed.getId()))
                .findFirst()
                .map(SourceIngestionProperties.Feed::isRequired)
                .orElse(false);
    }

    private void requireEnabled() {
        if (!config.isEnabled()) {
            throw new ConflictException("Source ingestion disabilitata");
        }
    }

    private void validateFeed(SourceIngestionProperties.Feed feed) {
        if (feed.getId() == null
                || feed.getId().isBlank()
                || feed.getSource() == null
                || feed.getSeason() == null
                || feed.getUrl() == null
                || feed.getType() == null) {
            throw new BadRequestException("Configurazione feed incompleta");
        }
        type(feed);
    }

    private SourceFeedType type(SourceIngestionProperties.Feed feed) {
        try {
            return SourceFeedType.valueOf(feed.getType().trim().toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            throw new BadRequestException("Tipo feed non valido");
        }
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes)
            );
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    static String safe(Throwable exception) {
        return SecretSanitizer.sanitize(exception);
    }

    private static String sanitizeHttpUrl(MatchResult match) {
        try {
            return RemoteCsvClient.sanitized(URI.create(match.group()));
        } catch (IllegalArgumentException ignored) {
            return "[REDACTED]";
        }
    }
}
