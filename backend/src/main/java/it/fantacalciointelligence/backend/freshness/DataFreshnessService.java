package it.fantacalciointelligence.backend.freshness;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.common.ResourceNotFoundException;

@Service
public class DataFreshnessService {

    private static final List<String> SOURCES =
            List.of(
                    "FANTACALCIO",
                    "GAZZETTA",
                    "FANTAMARKT"
            );

    private final JdbcClient jdbcClient;
    private final Clock clock;

    public DataFreshnessService(
            JdbcClient jdbcClient,
            Clock clock
    ) {
        this.jdbcClient = jdbcClient;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DataFreshnessResponse evaluate(
            String seasonCode
    ) {

        requireSeason(seasonCode);

        OffsetDateTime evaluatedAt =
                OffsetDateTime.now(clock);

        List<SourceFreshnessResponse> sources =
                new ArrayList<>();

        List<String> blockers =
                new ArrayList<>();

        List<String> warnings =
                new ArrayList<>();

        for (String source : SOURCES) {

            Policy policy =
                    loadPolicy(source);

            BatchSnapshot batch =
                    latestProductionBatch(
                            seasonCode,
                            source
                    );

            SourceFreshnessResponse response =
                    classify(
                            source,
                            policy,
                            batch,
                            evaluatedAt
                    );

            sources.add(response);

            addMessages(
                    response,
                    blockers,
                    warnings
            );
        }

        String overallStatus;

        if (!blockers.isEmpty()) {
            overallStatus = "BLOCKED";
        } else if (!warnings.isEmpty()) {
            overallStatus = "WARNING";
        } else {
            overallStatus = "READY";
        }

        return new DataFreshnessResponse(
                seasonCode,
                evaluatedAt,
                overallStatus,
                List.copyOf(sources),
                List.copyOf(blockers),
                List.copyOf(warnings)
        );
    }

    private SourceFreshnessResponse classify(
            String source,
            Policy policy,
            BatchSnapshot batch,
            OffsetDateTime evaluatedAt
    ) {

        if (batch == null) {
            return new SourceFreshnessResponse(
                    source,
                    null,
                    null,
                    null,
                    policy.freshMaxHours(),
                    policy.staleAfterHours(),
                    policy.requiredForRefresh(),
                    FreshnessStatus.MISSING.name()
            );
        }

        long ageHours =
                Math.max(
                        0,
                        Duration.between(
                                batch.observedAt(),
                                evaluatedAt
                        ).toHours()
                );

        FreshnessStatus status;

        if (ageHours
                <= policy.freshMaxHours()) {

            status = FreshnessStatus.FRESH;

        } else if (ageHours
                <= policy.staleAfterHours()) {

            status = FreshnessStatus.AGING;

        } else {
            status = FreshnessStatus.STALE;
        }

        return new SourceFreshnessResponse(
                source,
                batch.id(),
                batch.observedAt(),
                ageHours,
                policy.freshMaxHours(),
                policy.staleAfterHours(),
                policy.requiredForRefresh(),
                status.name()
        );
    }

    private void addMessages(
            SourceFreshnessResponse source,
            List<String> blockers,
            List<String> warnings
    ) {

        String status =
                source.status();

        if ("FRESH".equals(status)) {
            return;
        }

        if ("AGING".equals(status)) {
            warnings.add(
                    source.source()
                            + " è AGING: snapshot di "
                            + source.ageHours()
                            + " ore"
            );
            return;
        }

        String message;

        if ("MISSING".equals(status)) {
            message =
                    source.source()
                            + " è MISSING: nessun batch "
                            + "COMPLETED di produzione";
        } else {
            message =
                    source.source()
                            + " è STALE: snapshot di "
                            + source.ageHours()
                            + " ore";
        }

        if (source.requiredForRefresh()) {
            blockers.add(message);
        } else {
            warnings.add(message);
        }
    }

    private void requireSeason(
            String seasonCode
    ) {

        boolean exists =
                jdbcClient.sql("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM seasons
                            WHERE code = :season
                        )
                        """)
                        .param(
                                "season",
                                seasonCode
                        )
                        .query(Boolean.class)
                        .single();

        if (!exists) {
            throw new ResourceNotFoundException(
                    "Stagione non trovata: "
                            + seasonCode
            );
        }
    }

    private Policy loadPolicy(
            String source
    ) {

        return jdbcClient.sql("""
                SELECT
                    fresh_max_hours,
                    stale_after_hours,
                    required_for_refresh

                FROM data_source_freshness_policies

                WHERE source = :source
                """)
                .param("source", source)
                .query((resultSet, rowNumber) ->
                        new Policy(
                                resultSet.getInt(
                                        "fresh_max_hours"
                                ),
                                resultSet.getInt(
                                        "stale_after_hours"
                                ),
                                resultSet.getBoolean(
                                        "required_for_refresh"
                                )
                        )
                )
                .optional()
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Policy freshness mancante per "
                                        + source
                        )
                );
    }

    private BatchSnapshot latestProductionBatch(
            String seasonCode,
            String source
    ) {

        return jdbcClient.sql("""
                SELECT
                    batch.id,

                    COALESCE(
                        batch.source_observed_at,
                        batch.completed_at,
                        batch.started_at
                    ) AS observed_at

                FROM import_batches batch

                JOIN seasons season
                    ON season.id =
                        batch.season_id

                WHERE season.code = :season
                  AND batch.source = :source
                  AND batch.status = 'COMPLETED'
                  AND LOWER(batch.original_filename)
                        NOT LIKE '%test%'

                ORDER BY
                    COALESCE(
                        batch.source_observed_at,
                        batch.completed_at,
                        batch.started_at
                    ) DESC,
                    batch.id DESC

                LIMIT 1
                """)
                .param(
                        "season",
                        seasonCode
                )
                .param(
                        "source",
                        source
                )
                .query((resultSet, rowNumber) ->
                        new BatchSnapshot(
                                resultSet.getLong("id"),
                                resultSet.getObject(
                                        "observed_at",
                                        OffsetDateTime.class
                                )
                        )
                )
                .optional()
                .orElse(null);
    }

    private record Policy(
            int freshMaxHours,
            int staleAfterHours,
            boolean requiredForRefresh
    ) {
    }

    private record BatchSnapshot(
            Long id,
            OffsetDateTime observedAt
    ) {
    }
}
