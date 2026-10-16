package it.fantacalciointelligence.backend.development;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.common.ConflictException;
import it.fantacalciointelligence.backend.common.ResourceNotFoundException;

@Service
public class PlayerDevelopmentAssessmentService {

    private static final BigDecimal ZERO =
            BigDecimal.ZERO.setScale(
                    2,
                    RoundingMode.HALF_UP
            );

    private final JdbcClient jdbcClient;

    public PlayerDevelopmentAssessmentService(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional
    public PlayerDevelopmentGenerationResponse generate(
            String targetSeasonCode
    ) {
        SeasonReference targetSeason =
                findSeason(targetSeasonCode);

        List<PlayerReference> players =
                findEligiblePlayers(targetSeason.id());

        if (players.isEmpty()) {
            throw new ConflictException(
                    "Nessun giocatore ufficiale disponibile "
                            + "per la stagione "
                            + targetSeasonCode
            );
        }

        int generated = 0;
        int growing = 0;
        int stable = 0;
        int declining = 0;
        int insufficient = 0;

        for (PlayerReference player : players) {
            DevelopmentCalculation calculation =
                    calculateDevelopment(
                            player,
                            targetSeason
                    );

            saveAssessment(
                    player,
                    targetSeason,
                    calculation
            );

            generated++;

            switch (calculation.trend()) {
                case "GROWING" -> growing++;
                case "STABLE" -> stable++;
                case "DECLINING" -> declining++;
                default -> insufficient++;
            }
        }

        deleteStaleAssessments(
                targetSeason.id()
        );

        return new PlayerDevelopmentGenerationResponse(
                targetSeason.code(),
                players.size(),
                generated,
                growing,
                stable,
                declining,
                insufficient,
                "COMPLETED"
        );
    }

    @Transactional(readOnly = true)
    public PlayerDevelopmentAssessmentResponse findByPlayer(
            Long playerId,
            String targetSeasonCode
    ) {
        return jdbcClient.sql("""
                SELECT
                    assessment.player_id,
                    player.display_name,
                    target.code AS target_season,
                    older.code AS older_season,
                    latest.code AS latest_season,
                    assessment.trend,
                    assessment.growth_score,
                    assessment.decline_risk_score,
                    assessment.confidence_score,
                    assessment.appearances_change,
                    assessment.starts_change,
                    assessment.minutes_change,
                    assessment.fantasy_average_change,
                    assessment.goals_change,
                    assessment.assists_change,
                    assessment.expected_goals_change,
                    assessment.expected_assists_change,
                    assessment.analysis_summary
                FROM player_development_assessments assessment

                JOIN players player
                    ON player.id = assessment.player_id

                JOIN seasons target
                    ON target.id = assessment.target_season_id

                LEFT JOIN seasons older
                    ON older.id = assessment.older_season_id

                LEFT JOIN seasons latest
                    ON latest.id = assessment.latest_season_id

                WHERE assessment.player_id = :playerId
                  AND target.code = :targetSeasonCode
                """)
                .param("playerId", playerId)
                .param(
                        "targetSeasonCode",
                        targetSeasonCode
                )
                .query((resultSet, rowNumber) ->
                        new PlayerDevelopmentAssessmentResponse(
                                resultSet.getLong("player_id"),
                                resultSet.getString("display_name"),
                                resultSet.getString("target_season"),
                                resultSet.getString("older_season"),
                                resultSet.getString("latest_season"),
                                resultSet.getString("trend"),
                                resultSet.getBigDecimal(
                                        "growth_score"
                                ),
                                resultSet.getBigDecimal(
                                        "decline_risk_score"
                                ),
                                resultSet.getBigDecimal(
                                        "confidence_score"
                                ),
                                resultSet.getBigDecimal(
                                        "appearances_change"
                                ),
                                resultSet.getBigDecimal(
                                        "starts_change"
                                ),
                                resultSet.getBigDecimal(
                                        "minutes_change"
                                ),
                                resultSet.getBigDecimal(
                                        "fantasy_average_change"
                                ),
                                resultSet.getBigDecimal(
                                        "goals_change"
                                ),
                                resultSet.getBigDecimal(
                                        "assists_change"
                                ),
                                resultSet.getBigDecimal(
                                        "expected_goals_change"
                                ),
                                resultSet.getBigDecimal(
                                        "expected_assists_change"
                                ),
                                resultSet.getString(
                                        "analysis_summary"
                                )
                        )
                )
                .optional()
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Analisi evolutiva non trovata "
                                        + "per il giocatore"
                        )
                );
    }

    private DevelopmentCalculation calculateDevelopment(
            PlayerReference player,
            SeasonReference targetSeason
    ) {
        List<SeasonStatistics> history =
                findPreviousSeasonStatistics(
                        player.id(),
                        targetSeason.startYear()
                );

        if (history.size() < 2) {
            String summary =
                    "Dati storici insufficienti: servono due "
                            + "stagioni complete per stimare "
                            + "crescita o rischio di calo.";

            return DevelopmentCalculation.insufficient(
                    history,
                    summary
            );
        }

        SeasonStatistics latest = history.get(0);
        SeasonStatistics older = history.get(1);

        BigDecimal appearancesChange =
                difference(
                        latest.appearances(),
                        older.appearances()
                );

        BigDecimal startsChange =
                difference(
                        latest.starts(),
                        older.starts()
                );

        BigDecimal minutesChange =
                difference(
                        latest.minutesPlayed(),
                        older.minutesPlayed()
                );

        BigDecimal fantasyAverageChange =
                difference(
                        latest.fantasyAverage(),
                        older.fantasyAverage()
                );

        BigDecimal goalsChange =
                difference(
                        latest.goals(),
                        older.goals()
                );

        BigDecimal assistsChange =
                difference(
                        latest.assists(),
                        older.assists()
                );

        BigDecimal expectedGoalsChange =
                difference(
                        latest.expectedGoals(),
                        older.expectedGoals()
                );

        BigDecimal expectedAssistsChange =
                difference(
                        latest.expectedAssists(),
                        older.expectedAssists()
                );

        BigDecimal opportunityScore =
                clamp(
                        BigDecimal.valueOf(50)
                                .add(
                                        appearancesChange.multiply(
                                                new BigDecimal("1.20")
                                        )
                                )
                                .add(
                                        startsChange.multiply(
                                                new BigDecimal("1.50")
                                        )
                                )
                                .add(
                                        minutesChange.divide(
                                                new BigDecimal("90"),
                                                4,
                                                RoundingMode.HALF_UP
                                        )
                                )
                );

        BigDecimal performanceScore =
                clamp(
                        BigDecimal.valueOf(50)
                                .add(
                                        fantasyAverageChange.multiply(
                                                new BigDecimal("18")
                                        )
                                )
                                .add(
                                        goalsChange.multiply(
                                                new BigDecimal("2.50")
                                        )
                                )
                                .add(
                                        assistsChange.multiply(
                                                new BigDecimal("2.00")
                                        )
                                )
                );

        BigDecimal underlyingScore =
                clamp(
                        BigDecimal.valueOf(50)
                                .add(
                                        expectedGoalsChange.multiply(
                                                new BigDecimal("4.00")
                                        )
                                )
                                .add(
                                        expectedAssistsChange.multiply(
                                                new BigDecimal("4.00")
                                        )
                                )
                );

        BigDecimal growthScore =
                clamp(
                        opportunityScore.multiply(
                                new BigDecimal("0.40")
                        )
                                .add(
                                        performanceScore.multiply(
                                                new BigDecimal("0.35")
                                        )
                                )
                                .add(
                                        underlyingScore.multiply(
                                                new BigDecimal("0.25")
                                        )
                                )
                );

        BigDecimal declineRisk =
                clamp(
                        BigDecimal.valueOf(100)
                                .subtract(growthScore)
                                .add(
                                        regressionPenalty(
                                                latest
                                        )
                                )
                );

        BigDecimal confidence =
                calculateConfidence(
                        older,
                        latest
                );

        String trend =
                determineTrend(
                        growthScore,
                        declineRisk
                );

        String summary =
                buildSummary(
                        player.name(),
                        trend,
                        appearancesChange,
                        startsChange,
                        minutesChange,
                        fantasyAverageChange,
                        goalsChange,
                        assistsChange,
                        expectedGoalsChange,
                        expectedAssistsChange,
                        confidence
                );

        return new DevelopmentCalculation(
                older.seasonId(),
                older.seasonCode(),
                latest.seasonId(),
                latest.seasonCode(),
                trend,
                growthScore,
                declineRisk,
                confidence,
                appearancesChange,
                startsChange,
                minutesChange,
                fantasyAverageChange,
                goalsChange,
                assistsChange,
                expectedGoalsChange,
                expectedAssistsChange,
                summary
        );
    }

    private List<SeasonStatistics> findPreviousSeasonStatistics(
            Long playerId,
            int targetStartYear
    ) {
        return jdbcClient.sql("""
                SELECT
                    season.id AS season_id,
                    season.code AS season_code,

                    AVG(statistics.appearances)
                        AS appearances,

                    AVG(statistics.starts)
                        AS starts,

                    AVG(statistics.minutes_played)
                        AS minutes_played,

                    AVG(statistics.fantasy_average)
                        FILTER (
                            WHERE statistics.fantasy_average
                                IS NOT NULL
                        )
                        AS fantasy_average,

                    AVG(statistics.goals)
                        AS goals,

                    AVG(statistics.assists)
                        AS assists,

                    AVG(statistics.expected_goals)
                        FILTER (
                            WHERE statistics.expected_goals
                                IS NOT NULL
                        )
                        AS expected_goals,

                    AVG(statistics.expected_assists)
                        FILTER (
                            WHERE statistics.expected_assists
                                IS NOT NULL
                        )
                        AS expected_assists,

                    COUNT(DISTINCT statistics.source)
                        AS sources_count

                FROM player_season_statistics statistics

                JOIN seasons season
                    ON season.id = statistics.season_id

                WHERE statistics.player_id = :playerId
                  AND season.start_year < :targetStartYear

                GROUP BY
                    season.id,
                    season.code,
                    season.start_year

                ORDER BY season.start_year DESC

                LIMIT 2
                """)
                .param("playerId", playerId)
                .param(
                        "targetStartYear",
                        targetStartYear
                )
                .query((resultSet, rowNumber) ->
                        new SeasonStatistics(
                                resultSet.getLong("season_id"),
                                resultSet.getString("season_code"),
                                safeDecimal(
                                        resultSet.getBigDecimal(
                                                "appearances"
                                        )
                                ),
                                safeDecimal(
                                        resultSet.getBigDecimal(
                                                "starts"
                                        )
                                ),
                                safeDecimal(
                                        resultSet.getBigDecimal(
                                                "minutes_played"
                                        )
                                ),
                                safeDecimal(
                                        resultSet.getBigDecimal(
                                                "fantasy_average"
                                        )
                                ),
                                safeDecimal(
                                        resultSet.getBigDecimal(
                                                "goals"
                                        )
                                ),
                                safeDecimal(
                                        resultSet.getBigDecimal(
                                                "assists"
                                        )
                                ),
                                safeDecimal(
                                        resultSet.getBigDecimal(
                                                "expected_goals"
                                        )
                                ),
                                safeDecimal(
                                        resultSet.getBigDecimal(
                                                "expected_assists"
                                        )
                                ),
                                resultSet.getInt("sources_count")
                        )
                )
                .list();
    }

    private BigDecimal regressionPenalty(
            SeasonStatistics latest
    ) {
        BigDecimal actualBonus =
                latest.goals()
                        .add(latest.assists());

        BigDecimal expectedBonus =
                latest.expectedGoals()
                        .add(latest.expectedAssists());

        BigDecimal overPerformance =
                actualBonus.subtract(expectedBonus);

        if (overPerformance.compareTo(
                new BigDecimal("3")
        ) <= 0) {
            return ZERO;
        }

        return overPerformance
                .subtract(new BigDecimal("3"))
                .multiply(new BigDecimal("2.50"));
    }

    private BigDecimal calculateConfidence(
            SeasonStatistics older,
            SeasonStatistics latest
    ) {
        BigDecimal sourceComponent =
                BigDecimal.valueOf(
                        Math.min(
                                3,
                                older.sourcesCount()
                        )
                                + Math.min(
                                        3,
                                        latest.sourcesCount()
                                )
                )
                        .divide(
                                new BigDecimal("6"),
                                4,
                                RoundingMode.HALF_UP
                        )
                        .multiply(
                                new BigDecimal("45")
                        );

        BigDecimal appearancesComponent =
                older.appearances()
                        .add(latest.appearances())
                        .divide(
                                new BigDecimal("60"),
                                4,
                                RoundingMode.HALF_UP
                        )
                        .multiply(
                                new BigDecimal("55")
                        );

        return clamp(
                sourceComponent.add(
                        appearancesComponent
                )
        );
    }

    private String determineTrend(
            BigDecimal growthScore,
            BigDecimal declineRisk
    ) {
        if (growthScore.compareTo(
                new BigDecimal("62")
        ) >= 0
                && declineRisk.compareTo(
                new BigDecimal("48")
        ) <= 0) {
            return "GROWING";
        }

        if (growthScore.compareTo(
                new BigDecimal("40")
        ) < 0
                || declineRisk.compareTo(
                new BigDecimal("62")
        ) >= 0) {
            return "DECLINING";
        }

        return "STABLE";
    }

    private String buildSummary(
            String playerName,
            String trend,
            BigDecimal appearancesChange,
            BigDecimal startsChange,
            BigDecimal minutesChange,
            BigDecimal fantasyAverageChange,
            BigDecimal goalsChange,
            BigDecimal assistsChange,
            BigDecimal expectedGoalsChange,
            BigDecimal expectedAssistsChange,
            BigDecimal confidence
    ) {
        List<String> evidence =
                new ArrayList<>();

        if (minutesChange.compareTo(
                new BigDecimal("300")
        ) >= 0) {
            evidence.add(
                    "minutaggio in forte aumento"
            );
        }
        else if (minutesChange.compareTo(
                new BigDecimal("-300")
        ) <= 0) {
            evidence.add(
                    "minutaggio in diminuzione"
            );
        }

        if (startsChange.compareTo(
                new BigDecimal("4")
        ) >= 0) {
            evidence.add(
                    "maggiore continuità da titolare"
            );
        }
        else if (startsChange.compareTo(
                new BigDecimal("-4")
        ) <= 0) {
            evidence.add(
                    "meno presenze da titolare"
            );
        }

        if (fantasyAverageChange.compareTo(
                new BigDecimal("0.30")
        ) >= 0) {
            evidence.add(
                    "fantamedia in crescita"
            );
        }
        else if (fantasyAverageChange.compareTo(
                new BigDecimal("-0.30")
        ) <= 0) {
            evidence.add(
                    "fantamedia in calo"
            );
        }

        if (expectedGoalsChange
                .add(expectedAssistsChange)
                .compareTo(
                        new BigDecimal("1.50")
                ) >= 0) {
            evidence.add(
                    "occasioni offensive attese in aumento"
            );
        }
        else if (expectedGoalsChange
                .add(expectedAssistsChange)
                .compareTo(
                        new BigDecimal("-1.50")
                ) <= 0) {
            evidence.add(
                    "coinvolgimento offensivo atteso in calo"
            );
        }

        if (goalsChange
                .add(assistsChange)
                .compareTo(
                        new BigDecimal("3")
                ) >= 0) {
            evidence.add(
                    "bonus reali in aumento"
            );
        }
        else if (goalsChange
                .add(assistsChange)
                .compareTo(
                        new BigDecimal("-3")
                ) <= 0) {
            evidence.add(
                    "bonus reali in diminuzione"
            );
        }

        String evidenceText =
                evidence.isEmpty()
                        ? "andamento complessivamente regolare"
                        : String.join(
                                ", ",
                                evidence
                        );

        String conclusion =
                switch (trend) {
                    case "GROWING" ->
                            "potrebbe fare il salto di qualità";
                    case "DECLINING" ->
                            "potrebbe rendere meno rispetto "
                                    + "all'ultima stagione";
                    default ->
                            "mostra un profilo tendenzialmente stabile";
                };

        return playerName
                + " "
                + conclusion
                + ": "
                + evidenceText
                + ". Affidabilità dell'analisi "
                + confidence.setScale(
                        0,
                        RoundingMode.HALF_UP
                )
                + "/100.";
    }

    private void saveAssessment(
            PlayerReference player,
            SeasonReference targetSeason,
            DevelopmentCalculation calculation
    ) {
        jdbcClient.sql("""
                INSERT INTO player_development_assessments (
                    player_id,
                    target_season_id,
                    older_season_id,
                    latest_season_id,
                    trend,
                    growth_score,
                    decline_risk_score,
                    confidence_score,
                    appearances_change,
                    starts_change,
                    minutes_change,
                    fantasy_average_change,
                    goals_change,
                    assists_change,
                    expected_goals_change,
                    expected_assists_change,
                    analysis_summary
                )
                VALUES (
                    :playerId,
                    :targetSeasonId,
                    :olderSeasonId,
                    :latestSeasonId,
                    :trend,
                    :growthScore,
                    :declineRiskScore,
                    :confidenceScore,
                    :appearancesChange,
                    :startsChange,
                    :minutesChange,
                    :fantasyAverageChange,
                    :goalsChange,
                    :assistsChange,
                    :expectedGoalsChange,
                    :expectedAssistsChange,
                    :analysisSummary
                )
                ON CONFLICT (
                    player_id,
                    target_season_id
                )
                DO UPDATE SET
                    older_season_id =
                        EXCLUDED.older_season_id,

                    latest_season_id =
                        EXCLUDED.latest_season_id,

                    trend =
                        EXCLUDED.trend,

                    growth_score =
                        EXCLUDED.growth_score,

                    decline_risk_score =
                        EXCLUDED.decline_risk_score,

                    confidence_score =
                        EXCLUDED.confidence_score,

                    appearances_change =
                        EXCLUDED.appearances_change,

                    starts_change =
                        EXCLUDED.starts_change,

                    minutes_change =
                        EXCLUDED.minutes_change,

                    fantasy_average_change =
                        EXCLUDED.fantasy_average_change,

                    goals_change =
                        EXCLUDED.goals_change,

                    assists_change =
                        EXCLUDED.assists_change,

                    expected_goals_change =
                        EXCLUDED.expected_goals_change,

                    expected_assists_change =
                        EXCLUDED.expected_assists_change,

                    analysis_summary =
                        EXCLUDED.analysis_summary,

                    generated_at =
                        CURRENT_TIMESTAMP,

                    updated_at =
                        CURRENT_TIMESTAMP
                """)
                .param("playerId", player.id())
                .param(
                        "targetSeasonId",
                        targetSeason.id()
                )
                .param(
                        "olderSeasonId",
                        calculation.olderSeasonId()
                )
                .param(
                        "latestSeasonId",
                        calculation.latestSeasonId()
                )
                .param("trend", calculation.trend())
                .param(
                        "growthScore",
                        calculation.growthScore()
                )
                .param(
                        "declineRiskScore",
                        calculation.declineRiskScore()
                )
                .param(
                        "confidenceScore",
                        calculation.confidenceScore()
                )
                .param(
                        "appearancesChange",
                        calculation.appearancesChange()
                )
                .param(
                        "startsChange",
                        calculation.startsChange()
                )
                .param(
                        "minutesChange",
                        calculation.minutesChange()
                )
                .param(
                        "fantasyAverageChange",
                        calculation.fantasyAverageChange()
                )
                .param(
                        "goalsChange",
                        calculation.goalsChange()
                )
                .param(
                        "assistsChange",
                        calculation.assistsChange()
                )
                .param(
                        "expectedGoalsChange",
                        calculation.expectedGoalsChange()
                )
                .param(
                        "expectedAssistsChange",
                        calculation.expectedAssistsChange()
                )
                .param(
                        "analysisSummary",
                        calculation.analysisSummary()
                )
                .update();
    }

    private List<PlayerReference> findEligiblePlayers(
            Long targetSeasonId
    ) {
        return jdbcClient.sql("""
                SELECT DISTINCT
                    player.id,
                    player.display_name
                FROM players player

                JOIN player_registrations registration
                    ON registration.player_id = player.id
                    AND registration.season_id =
                        :targetSeasonId
                    AND registration.registration_status =
                        'ACTIVE'

                JOIN player_reconciliation_results
                    reconciliation
                    ON reconciliation.player_id = player.id
                    AND reconciliation.season_id =
                        :targetSeasonId

                WHERE player.active = TRUE

                ORDER BY player.id
                """)
                .param(
                        "targetSeasonId",
                        targetSeasonId
                )
                .query((resultSet, rowNumber) ->
                        new PlayerReference(
                                resultSet.getLong("id"),
                                resultSet.getString(
                                        "display_name"
                                )
                        )
                )
                .list();
    }

    private void deleteStaleAssessments(
            Long targetSeasonId
    ) {
        jdbcClient.sql("""
                DELETE FROM player_development_assessments
                WHERE target_season_id = :targetSeasonId
                  AND NOT EXISTS (
                      SELECT 1
                      FROM player_reconciliation_results
                          reconciliation

                      JOIN player_registrations registration
                          ON registration.player_id =
                              reconciliation.player_id
                          AND registration.season_id =
                              reconciliation.season_id
                          AND registration.registration_status =
                              'ACTIVE'

                      WHERE reconciliation.player_id =
                              player_development_assessments.player_id
                        AND reconciliation.season_id =
                              :targetSeasonId
                  )
                """)
                .param(
                        "targetSeasonId",
                        targetSeasonId
                )
                .update();
    }

    private SeasonReference findSeason(
            String seasonCode
    ) {
        return jdbcClient.sql("""
                SELECT
                    id,
                    code,
                    start_year
                FROM seasons
                WHERE code = :seasonCode
                """)
                .param("seasonCode", seasonCode)
                .query((resultSet, rowNumber) ->
                        new SeasonReference(
                                resultSet.getLong("id"),
                                resultSet.getString("code"),
                                resultSet.getInt("start_year")
                        )
                )
                .optional()
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Stagione non trovata: "
                                        + seasonCode
                        )
                );
    }

    private BigDecimal difference(
            BigDecimal latest,
            BigDecimal older
    ) {
        return safeDecimal(latest)
                .subtract(safeDecimal(older))
                .setScale(
                        2,
                        RoundingMode.HALF_UP
                );
    }

    private BigDecimal safeDecimal(
            BigDecimal value
    ) {
        return value == null
                ? ZERO
                : value;
    }

    private BigDecimal clamp(
            BigDecimal value
    ) {
        Objects.requireNonNull(value);

        if (value.compareTo(BigDecimal.ZERO) < 0) {
            return ZERO;
        }

        if (value.compareTo(
                BigDecimal.valueOf(100)
        ) > 0) {
            return BigDecimal.valueOf(100)
                    .setScale(
                            2,
                            RoundingMode.HALF_UP
                    );
        }

        return value.setScale(
                2,
                RoundingMode.HALF_UP
        );
    }

    private record PlayerReference(
            Long id,
            String name
    ) {
    }

    private record SeasonReference(
            Long id,
            String code,
            int startYear
    ) {
    }

    private record SeasonStatistics(
            Long seasonId,
            String seasonCode,
            BigDecimal appearances,
            BigDecimal starts,
            BigDecimal minutesPlayed,
            BigDecimal fantasyAverage,
            BigDecimal goals,
            BigDecimal assists,
            BigDecimal expectedGoals,
            BigDecimal expectedAssists,
            int sourcesCount
    ) {
    }

    private record DevelopmentCalculation(
            Long olderSeasonId,
            String olderSeasonCode,
            Long latestSeasonId,
            String latestSeasonCode,
            String trend,
            BigDecimal growthScore,
            BigDecimal declineRiskScore,
            BigDecimal confidenceScore,
            BigDecimal appearancesChange,
            BigDecimal startsChange,
            BigDecimal minutesChange,
            BigDecimal fantasyAverageChange,
            BigDecimal goalsChange,
            BigDecimal assistsChange,
            BigDecimal expectedGoalsChange,
            BigDecimal expectedAssistsChange,
            String analysisSummary
    ) {
        private static DevelopmentCalculation insufficient(
                List<SeasonStatistics> history,
                String summary
        ) {
            SeasonStatistics latest =
                    history.isEmpty()
                            ? null
                            : history.get(0);

            return new DevelopmentCalculation(
                    null,
                    null,
                    latest == null
                            ? null
                            : latest.seasonId(),
                    latest == null
                            ? null
                            : latest.seasonCode(),
                    "INSUFFICIENT_DATA",
                    ZERO,
                    ZERO,
                    ZERO,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    summary
            );
        }
    }
}