package it.fantacalciointelligence.backend.ingestion;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.time.OffsetDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import it.fantacalciointelligence.backend.common.ConflictException;
import it.fantacalciointelligence.backend.common.ResourceNotFoundException;
import it.fantacalciointelligence.backend.common.SecretSanitizer;

@Service
public class AutomationRunService {

    private final JdbcClient jdbc;
    private final Duration staleThreshold;

    public AutomationRunService(
            JdbcClient jdbc,
            @Value("${app.production-automation.stale-threshold:2h}")
            Duration staleThreshold
    ) {
        this.jdbc = jdbc;

        if (
                staleThreshold == null
                        || staleThreshold.isZero()
                        || staleThreshold.isNegative()
        ) {
            throw new IllegalStateException(
                    "app.production-automation.stale-threshold deve essere maggiore di zero"
            );
        }

        this.staleThreshold = staleThreshold;
    }

    public long begin(String season) {

        long seasonId = seasonId(season);

        recoverStale(seasonId);

        try {
            return jdbc.sql("""
                    INSERT INTO automation_runs (
                        season_id,
                        trigger_type,
                        status,
                        stage
                    )
                    VALUES (
                        :seasonId,
                        'SCHEDULED',
                        'RUNNING',
                        'STARTING'
                    )
                    RETURNING id
                    """)
                    .param("seasonId", seasonId)
                    .query(Long.class)
                    .single();

        } catch (DataIntegrityViolationException exception) {

            throw new ConflictException(
                    "Aggiornamento automatico gia' in esecuzione per la stagione "
                            + season
            );
        }
    }

    public void stage(
            long runId,
            String stage
    ) {

        int updated = jdbc.sql("""
                UPDATE automation_runs
                SET
                    stage = :stage,
                    last_heartbeat_at = CURRENT_TIMESTAMP
                WHERE id = :runId
                  AND status = 'RUNNING'
                """)
                .param("stage", stage)
                .param("runId", runId)
                .update();

        if (updated != 1) {
            throw new ConflictException(
                    "Esecuzione automatica non piu' attiva: " + runId
            );
        }
    }

    public void complete(
            long runId,
            Long listoneRunId,
            int ingestionRunCount,
            Long refreshRunId
    ) {

        int updated = jdbc.sql("""
                UPDATE automation_runs
                SET
                    status = 'COMPLETED',
                    stage = 'COMPLETED',
                    listone_run_id = :listoneRunId,
                    ingestion_run_count = :ingestionRunCount,
                    refresh_run_id = :refreshRunId,
                    last_heartbeat_at = CURRENT_TIMESTAMP,
                    completed_at = CURRENT_TIMESTAMP,
                    error_message = NULL
                WHERE id = :runId
                  AND status = 'RUNNING'
                """)
                .param(
                        "listoneRunId",
                        listoneRunId,
                        Types.BIGINT
                )
                .param(
                        "ingestionRunCount",
                        ingestionRunCount
                )
                .param(
                        "refreshRunId",
                        refreshRunId,
                        Types.BIGINT
                )
                .param("runId", runId)
                .update();

        if (updated != 1) {
            throw new ConflictException(
                    "Impossibile completare esecuzione automatica " + runId
            );
        }
    }

    public void fail(
            long runId,
            Throwable exception
    ) {

        String safeError =
                truncate(
                        SecretSanitizer.sanitize(exception),
                        2000
                );

        jdbc.sql("""
                UPDATE automation_runs
                SET
                    status = 'FAILED',
                    last_heartbeat_at = CURRENT_TIMESTAMP,
                    completed_at = CURRENT_TIMESTAMP,
                    error_message = :error
                WHERE id = :runId
                  AND status = 'RUNNING'
                """)
                .param(
                        "error",
                        safeError,
                        Types.VARCHAR
                )
                .param("runId", runId)
                .update();
    }

    public AutomationRunResponse latest(
            String season
    ) {

        return jdbc.sql("""
                SELECT
                    run.id,
                    season.code AS season,
                    run.trigger_type,
                    run.status,
                    run.stage,
                    run.listone_run_id,
                    run.ingestion_run_count,
                    run.refresh_run_id,
                    run.started_at,
                    run.last_heartbeat_at,
                    run.completed_at,
                    run.error_message
                FROM automation_runs run
                JOIN seasons season
                  ON season.id = run.season_id
                WHERE season.code = :season
                ORDER BY
                    run.started_at DESC,
                    run.id DESC
                LIMIT 1
                """)
                .param("season", season)
                .query((resultSet, rowNumber) ->
                        map(resultSet)
                )
                .optional()
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Nessuna esecuzione automatica trovata per la stagione "
                                        + season
                        )
                );
    }

    private void recoverStale(
            long seasonId
    ) {

        jdbc.sql("""
                UPDATE automation_runs
                SET
                    status = 'FAILED',
                    completed_at = CURRENT_TIMESTAMP,
                    error_message =
                        'Esecuzione automatica interrotta recuperata automaticamente'
                WHERE season_id = :seasonId
                  AND status = 'RUNNING'
                  AND last_heartbeat_at <
                      CURRENT_TIMESTAMP
                      - (:staleSeconds * INTERVAL '1 second')
                """)
                .param("seasonId", seasonId)
                .param(
                        "staleSeconds",
                        staleThreshold.toSeconds()
                )
                .update();
    }

    private long seasonId(
            String season
    ) {

        return jdbc.sql("""
                SELECT id
                FROM seasons
                WHERE code = :season
                """)
                .param("season", season)
                .query(Long.class)
                .optional()
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Stagione non trovata: " + season
                        )
                );
    }

    private AutomationRunResponse map(
            ResultSet resultSet
    ) throws SQLException {

        return new AutomationRunResponse(
                resultSet.getLong("id"),
                resultSet.getString("season"),
                resultSet.getString("trigger_type"),
                resultSet.getString("status"),
                resultSet.getString("stage"),
                (Long) resultSet.getObject(
                        "listone_run_id"
                ),
                resultSet.getInt(
                        "ingestion_run_count"
                ),
                (Long) resultSet.getObject(
                        "refresh_run_id"
                ),
                resultSet.getObject(
                        "started_at",
                        OffsetDateTime.class
                ),
                resultSet.getObject(
                        "last_heartbeat_at",
                        OffsetDateTime.class
                ),
                resultSet.getObject(
                        "completed_at",
                        OffsetDateTime.class
                ),
                resultSet.getString(
                        "error_message"
                )
        );
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
                : value.substring(0, maximum);
    }
}