package it.fantacalciointelligence.backend.lineup;

import it.fantacalciointelligence.backend.common.ResourceNotFoundException;
import it.fantacalciointelligence.backend.fixture.B69FixtureMatchupResponse;
import it.fantacalciointelligence.backend.fixture.B69FixtureMatchupService;
import it.fantacalciointelligence.backend.fixture.B69RoleDifficultyResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecommendedLineupService {

    private static final String PERFORMANCE_VERSION =
            "B66_FEATURES_V3";

    private static final List<Formation> FORMATIONS =
            List.of(
                    new Formation("3-4-3", 3, 4, 3),
                    new Formation("3-5-2", 3, 5, 2),
                    new Formation("4-3-3", 4, 3, 3),
                    new Formation("4-4-2", 4, 4, 2),
                    new Formation("4-5-1", 4, 5, 1),
                    new Formation("5-3-2", 5, 3, 2),
                    new Formation("5-4-1", 5, 4, 1)
            );

    private final JdbcClient jdbcClient;
    private final B69FixtureMatchupService matchupService;
    private final B68LineupScorer lineupScorer;

    public RecommendedLineupService(
            JdbcClient jdbcClient,
            B69FixtureMatchupService matchupService,
            B68LineupScorer lineupScorer
    ) {
        this.jdbcClient = jdbcClient;
        this.matchupService = matchupService;
        this.lineupScorer = lineupScorer;
    }

    @Transactional(readOnly = true)
    public RecommendedLineupResponse recommend(
            String season,
            int matchday,
            String unavailablePlayerIds
    ) {
        return recommend(
                season,
                matchday,
                null,
                unavailablePlayerIds
        );
    }

    /**
     * Ownership-aware HTTP path: the squad is always resolved for the
     * authenticated principal (or an administrator) and never for another
     * owner. This is the only path used by RecommendedLineupController.
     */
    @Transactional(readOnly = true)
    public RecommendedLineupResponse recommend(
            String season,
            int matchday,
            Long requestedSquadId,
            String unavailablePlayerIds,
            String username,
            boolean administrator
    ) {

        Long squadId =
                resolveSquadId(
                        season,
                        requestedSquadId,
                        username,
                        administrator
                );

        return buildRecommendation(
                season,
                matchday,
                squadId,
                unavailablePlayerIds
        );
    }

    /**
     * Compatibility path for internal callers and backend tests.
     * It deliberately does NOT enforce ownership: it is not reachable
     * through the HTTP controller anymore.
     */
    @Transactional(readOnly = true)
    public RecommendedLineupResponse recommend(
            String season,
            int matchday,
            Long requestedSquadId,
            String unavailablePlayerIds
    ) {

        Long squadId =
                resolveSquadId(
                        season,
                        requestedSquadId
                );

        return buildRecommendation(
                season,
                matchday,
                squadId,
                unavailablePlayerIds
        );
    }

    private RecommendedLineupResponse buildRecommendation(
            String season,
            int matchday,
            Long squadId,
            String unavailablePlayerIds
    ) {

        if (matchday < 1 || matchday > 38) {
            throw new IllegalArgumentException(
                    "La giornata deve essere compresa tra 1 e 38"
            );
        }

        List<PlayerCandidate> candidates =
                loadCandidates(
                        season,
                        matchday,
                        squadId
                );

        FormationResult bestResult =
                FORMATIONS.stream()
                        .map(formation ->
                                evaluateFormation(
                                        formation,
                                        candidates
                                )
                        )
                        .max(
                                Comparator
                                        .comparingInt(
                                                FormationResult::
                                                        selectedPlayers
                                        )
                                        .thenComparing(
                                                FormationResult::
                                                        totalScore
                                        )
                        )
                        .orElseThrow();

        List<LineupPlayerResponse> starters =
                bestResult.selected()
                        .stream()
                        .sorted(
                                candidateRoleComparator()
                        )
                        .map(this::toResponse)
                        .toList();

        List<Long> starterIds =
                bestResult.selected()
                        .stream()
                        .map(
                                PlayerCandidate::playerId
                        )
                        .toList();

        List<LineupPlayerResponse> bench =
                createSmartBench(
                        candidates,
                        starterIds
                );

        List<Long> simulatedUnavailableIds =
                parseUnavailablePlayerIds(
                        unavailablePlayerIds
                );

        int maxSubstitutions =
                loadMaxSubstitutions(
                        squadId
                );

        List<LineupSubstitutionResponse> substitutions =
                createAutomaticSubstitutions(
                        starters,
                        bench,
                        simulatedUnavailableIds,
                        maxSubstitutions
                );

        Map<String, LineupRoleSummaryResponse> roles =
                createRoleSummary(
                        bestResult.formation(),
                        bestResult.selected()
                );

        int missingPlayers =
                11 - bestResult.selectedPlayers();

        List<String> warnings =
                new ArrayList<>();

        long unavailableStarters =
                starters.stream()
                        .filter(starter ->
                                simulatedUnavailableIds.contains(
                                        starter.playerId()
                                )
                        )
                        .count();

        if (
                unavailableStarters
                        > substitutions.size()
        ) {

            if (
                    substitutions.size()
                            >= maxSubstitutions
            ) {
                warnings.add(
                        "Limite massimo di "
                                + maxSubstitutions
                                + " sostituzioni raggiunto."
                );
            } else {
                warnings.add(
                        "Una o pi\u00F9 assenze non possono essere coperte: " + "nessuna riserva valida disponibile."
                );
            }
        }

        if (missingPlayers > 0) {
            warnings.add(
                    "Rosa incompleta: mancano "
                            + missingPlayers
                            + " giocatori per completare gli 11."
            );
        }

        long noEvidencePlayers =
                candidates.stream()
                        .filter(candidate ->
                                !candidate.actionable()
                        )
                        .filter(candidate ->
                                !isHardUnavailable(
                                        candidate.availabilityStatus()
                                )
                        )
                        .count();

        if (noEvidencePlayers > 0) {
            warnings.add(
                    noEvidencePlayers
                            + " giocatori non sono classificabili "
                            + "per insufficienza di evidenza reale."
            );
        }

        appendRoleWarning(
                warnings,
                roles,
                "P",
                "portieri"
        );

        appendRoleWarning(
                warnings,
                roles,
                "D",
                "difensori"
        );

        appendRoleWarning(
                warnings,
                roles,
                "C",
                "centrocampisti"
        );

        appendRoleWarning(
                warnings,
                roles,
                "A",
                "attaccanti"
        );

        BigDecimal formationScore =
                bestResult.selected().isEmpty()
                        ? BigDecimal.ZERO
                                .setScale(2)
                        : bestResult.totalScore()
                                .divide(
                                        BigDecimal.valueOf(
                                                bestResult
                                                        .selectedPlayers()
                                        ),
                                        2,
                                        RoundingMode.HALF_UP
                                );

        return new RecommendedLineupResponse(
                season,
                matchday,
                squadId,
                bestResult.formation().name(),
                missingPlayers == 0,
                bestResult.selectedPlayers(),
                missingPlayers,
                formationScore,
                roles,
                starters,
                bench,
                maxSubstitutions,
                substitutions.size(),
                Math.max(
                        0,
                        maxSubstitutions
                                - substitutions.size()
                ),
                substitutions,
                List.copyOf(warnings)
        );
    }

    private Long resolveSquadId(
            String season,
            Long requestedSquadId
    ) {

        if (requestedSquadId != null) {

            return jdbcClient.sql("""
                    SELECT squad.id

                    FROM fantasy_squads squad

                    WHERE squad.id = :squadId
                      AND squad.active = TRUE

                      AND EXISTS (
                          SELECT 1

                          FROM fantasy_squad_players squad_player

                          JOIN seasons season
                              ON season.id =
                                  squad_player.season_id

                          WHERE squad_player.squad_id =
                                  squad.id

                            AND season.code = :season
                      )
                    """)
                    .param(
                            "squadId",
                            requestedSquadId
                    )
                    .param(
                            "season",
                            season
                    )
                    .query(Long.class)
                    .optional()
                    .orElseThrow(() ->
                            new ResourceNotFoundException(
                                    "Rosa attiva non trovata: "
                                            + requestedSquadId
                                            + " per la stagione "
                                            + season
                            )
                    );
        }

        return jdbcClient.sql("""
                SELECT squad.id

                FROM fantasy_squads squad

                WHERE squad.active = TRUE

                  AND EXISTS (
                      SELECT 1

                      FROM fantasy_squad_players squad_player

                      JOIN seasons season
                          ON season.id =
                              squad_player.season_id

                      WHERE squad_player.squad_id =
                              squad.id

                        AND season.code = :season
                  )

                ORDER BY squad.id
                LIMIT 1
                """)
                .param(
                        "season",
                        season
                )
                .query(Long.class)
                .optional()
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Nessuna rosa attiva trovata "
                                        + "per la stagione "
                                        + season
                        )
                );
    }

    /**
     * Ownership-aware squad resolution used by the HTTP path:
     * a requested squadId must belong to the authenticated principal,
     * and without squadId only the principal's own active squad is used.
     * Administrators may resolve any squad, mirroring SquadService.
     */
    private Long resolveSquadId(
            String season,
            Long requestedSquadId,
            String username,
            boolean administrator
    ) {

        if (requestedSquadId != null) {

            return jdbcClient.sql("""
                    SELECT squad.id

                    FROM fantasy_squads squad

                    WHERE squad.id = :squadId
                      AND squad.active = TRUE

                      AND (
                          :administrator = TRUE
                          OR LOWER(squad.owner_username)
                             = LOWER(:username)
                      )

                      AND EXISTS (
                          SELECT 1

                          FROM fantasy_squad_players squad_player

                          JOIN seasons season
                              ON season.id =
                                  squad_player.season_id

                          WHERE squad_player.squad_id =
                                  squad.id

                            AND season.code = :season
                      )
                    """)
                    .param(
                            "squadId",
                            requestedSquadId
                    )
                    .param(
                            "username",
                            username
                    )
                    .param(
                            "administrator",
                            administrator
                    )
                    .param(
                            "season",
                            season
                    )
                    .query(Long.class)
                    .optional()
                    .orElseThrow(() ->
                            new ResourceNotFoundException(
                                    "Rosa attiva non trovata: "
                                            + requestedSquadId
                                            + " per la stagione "
                                            + season
                            )
                    );
        }

        return jdbcClient.sql("""
                SELECT squad.id

                FROM fantasy_squads squad

                WHERE squad.active = TRUE

                  AND (
                      :administrator = TRUE
                      OR LOWER(squad.owner_username)
                         = LOWER(:username)
                  )

                  AND EXISTS (
                      SELECT 1

                      FROM fantasy_squad_players squad_player

                      JOIN seasons season
                          ON season.id =
                              squad_player.season_id

                      WHERE squad_player.squad_id =
                              squad.id

                        AND season.code = :season
                  )

                ORDER BY squad.id
                LIMIT 1
                """)
                .param(
                        "username",
                        username
                )
                .param(
                        "administrator",
                        administrator
                )
                .param(
                        "season",
                        season
                )
                .query(Long.class)
                .optional()
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Nessuna rosa attiva trovata "
                                        + "per la stagione "
                                        + season
                        )
                );
    }

    private List<PlayerCandidate> loadCandidates(
            String season,
            int matchday,
            Long squadId
    ) {

        List<Map<String, Object>> rows =
                jdbcClient.sql("""
                        SELECT
                            player.id AS player_id,
                            player.display_name,
                            club.name AS club_name,
                            club.slug AS club_slug,
                            squad_player.fantasy_role,

                            performance.history_seasons_available,
                            performance.data_confidence_score,

                            (
                                performance.feature_values
                                    ->> 'fantasy_average_role_percentile'
                            )::double precision
                                AS fantasy_average_role_percentile,

                            (
                                performance.feature_values
                                    ->> 'usage_role_percentile'
                            )::double precision
                                AS usage_role_percentile,

                            (
                                performance.feature_values
                                    ->> 'goal_rate_role_percentile'
                            )::double precision
                                AS goal_rate_role_percentile,

                            (
                                performance.feature_values
                                    ->> 'assist_rate_role_percentile'
                            )::double precision
                                AS assist_rate_role_percentile,

                            availability.status
                                AS availability_status,

                            availability.starting_probability
                                AS starting_probability,

                            availability.availability_probability
                                AS availability_probability

                        FROM fantasy_squad_players squad_player

                        JOIN fantasy_squads squad
                            ON squad.id =
                                squad_player.squad_id

                        JOIN players player
                            ON player.id =
                                squad_player.player_id

                        JOIN seasons season
                            ON season.id =
                                squad_player.season_id

                        JOIN player_registrations registration
                            ON registration.player_id =
                                player.id

                            AND registration.season_id =
                                season.id

                            AND registration.registration_status =
                                'ACTIVE'

                        JOIN clubs club
                            ON club.id =
                                registration.club_id

                        LEFT JOIN player_performance_feature_profiles
                            performance

                            ON performance.player_id =
                                player.id

                            AND performance.reference_season_id =
                                season.id

                            AND performance.algorithm_version =
                                :performanceVersion

                        LEFT JOIN player_availabilities availability
                            ON availability.player_id =
                                player.id

                            AND availability.season_id =
                                season.id

                            AND availability.matchday =
                                :matchday

                        WHERE squad.active = TRUE

                          AND player.active = TRUE

                          AND season.code = :season

                          AND squad.id = :squadId

                        ORDER BY player.display_name
                        """)
                        .param(
                                "performanceVersion",
                                PERFORMANCE_VERSION
                        )
                        .param(
                                "season",
                                season
                        )
                        .param(
                                "matchday",
                                matchday
                        )
                        .param(
                                "squadId",
                                squadId
                        )
                        .query()
                        .listOfRows();

        List<PlayerCandidate> candidates =
                new ArrayList<>();

        for (Map<String, Object> row : rows) {

            String clubSlug =
                    stringValue(
                            row.get("club_slug")
                    );

            String fantasyRole =
                    stringValue(
                            row.get("fantasy_role")
                    );

            B69FixtureMatchupResponse fixture =
                    matchupService
                            .findClubMatchups(
                                    season,
                                    clubSlug,
                                    matchday,
                                    matchday
                            )
                            .stream()
                            .findFirst()
                            .orElse(null);

            B69RoleDifficultyResponse roleMatchup =
                    fixture == null
                            ? null
                            : fixture.roles()
                                    .stream()
                                    .filter(role ->
                                            fantasyRole.equals(
                                                    role.role()
                                            )
                                    )
                                    .findFirst()
                                    .orElse(null);

            BigDecimal fixtureDifficulty =
                    roleMatchup == null
                            ? null
                            : roleMatchup
                                    .difficultyScore();

            BigDecimal startingProbability =
                    decimalOrNull(
                            row.get(
                                    "starting_probability"
                            )
                    );

            BigDecimal availabilityProbability =
                    decimalOrNull(
                            row.get(
                                    "availability_probability"
                            )
                    );

            String availabilityStatus =
                    stringValue(
                            row.get(
                                    "availability_status"
                            )
                    );

            if (
                    availabilityStatus == null
                    || availabilityStatus.isBlank()
            ) {
                availabilityStatus = "UNKNOWN";
            }

            BigDecimal confidence =
                    decimalOrNull(
                            row.get(
                                    "data_confidence_score"
                            )
                    );

            Integer historySeasons =
                    integerOrNull(
                            row.get(
                                    "history_seasons_available"
                            )
                    );

            B68LineupScorer.ScoreResult score =
                    lineupScorer.score(
                            new B68LineupScorer.Input(
                                    fantasyRole,
                                    doubleOrNull(
                                            row.get(
                                                    "fantasy_average_role_percentile"
                                            )
                                    ),
                                    doubleOrNull(
                                            row.get(
                                                    "usage_role_percentile"
                                            )
                                    ),
                                    doubleOrNull(
                                            row.get(
                                                    "goal_rate_role_percentile"
                                            )
                                    ),
                                    doubleOrNull(
                                            row.get(
                                                    "assist_rate_role_percentile"
                                            )
                                    ),
                                    startingProbability,
                                    availabilityProbability,
                                    availabilityStatus,
                                    fixtureDifficulty,
                                    confidence,
                                    historySeasons == null
                                            ? 0
                                            : historySeasons
                            )
                    );

            String reason =
                    createReason(
                            availabilityStatus,
                            score,
                            fixture,
                            roleMatchup,
                            historySeasons
                    );

            candidates.add(
                    new PlayerCandidate(
                            ((Number) row.get(
                                    "player_id"
                            )).longValue(),

                            stringValue(
                                    row.get(
                                            "display_name"
                                    )
                            ),

                            stringValue(
                                    row.get(
                                            "club_name"
                                    )
                            ),

                            stringValue(
                                    row.get(
                                            "fantasy_role"
                                    )
                            ),

                            fixture == null
                                    ? "Partita non disponibile"
                                    : fixture.opponent(),

                            fixture != null
                                    && fixture.home(),

                            fixtureDifficulty,

                            score.selectionScore(),

                            reason,

                            availabilityStatus,

                            score.actionable(),

                            score.evidenceTier(),

                            score.confidence()
                    )
            );
        }

        return candidates;
    }

    private FormationResult evaluateFormation(
            Formation formation,
            List<PlayerCandidate> candidates
    ) {

        List<PlayerCandidate> selected =
                new ArrayList<>();

        selected.addAll(
                selectByRole(
                        candidates,
                        "P",
                        1
                )
        );

        selected.addAll(
                selectByRole(
                        candidates,
                        "D",
                        formation.defenders()
                )
        );

        selected.addAll(
                selectByRole(
                        candidates,
                        "C",
                        formation.midfielders()
                )
        );

        selected.addAll(
                selectByRole(
                        candidates,
                        "A",
                        formation.forwards()
                )
        );

        BigDecimal totalScore =
                selected.stream()
                        .map(
                                PlayerCandidate::
                                        selectionScore
                        )
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );

        return new FormationResult(
                formation,
                selected,
                selected.size(),
                totalScore
        );
    }

    private List<PlayerCandidate> selectByRole(
            List<PlayerCandidate> candidates,
            String role,
            int maximum
    ) {

        return candidates.stream()
                .filter(candidate ->
                        role.equals(
                                candidate.role()
                        )
                )
                .filter(
                        PlayerCandidate::actionable
                )
                .filter(candidate ->
                        !isHardUnavailable(
                                candidate
                                        .availabilityStatus()
                        )
                )
                .filter(candidate ->
                        candidate.selectionScore()
                                != null
                )
                .sorted(
                        Comparator
                                .comparing(
                                        PlayerCandidate::
                                                selectionScore
                                )
                                .reversed()
                                .thenComparing(
                                        PlayerCandidate::
                                                playerId
                                )
                )
                .limit(maximum)
                .toList();
    }

    private int loadMaxSubstitutions(
            Long squadId
    ) {

        return jdbcClient.sql("""
                SELECT league.max_substitutions

                FROM fantasy_squads squad

                JOIN league_configurations league
                    ON league.id =
                        squad.league_configuration_id

                WHERE squad.id = :squadId
                """)
                .param(
                        "squadId",
                        squadId
                )
                .query(Integer.class)
                .optional()
                .orElse(3);
    }

    private List<Long> parseUnavailablePlayerIds(
            String unavailablePlayerIds
    ) {

        if (
                unavailablePlayerIds == null
                || unavailablePlayerIds.isBlank()
        ) {
            return List.of();
        }

        try {
            return List.of(
                    unavailablePlayerIds.split(",")
            )
                    .stream()
                    .map(String::trim)
                    .filter(value ->
                            !value.isBlank()
                    )
                    .map(Long::valueOf)
                    .distinct()
                    .toList();

        } catch (NumberFormatException exception) {

            throw new IllegalArgumentException(
                    "unavailablePlayerIds deve contenere "
                            + "solo ID numerici separati da virgole"
            );
        }
    }

    private List<LineupSubstitutionResponse>
            createAutomaticSubstitutions(
                    List<LineupPlayerResponse> starters,
                    List<LineupPlayerResponse> bench,
                    List<Long> simulatedUnavailableIds,
                    int maxSubstitutions
            ) {

        List<LineupSubstitutionResponse> substitutions =
                new ArrayList<>();

        List<Long> usedBenchPlayerIds =
                new ArrayList<>();

        for (LineupPlayerResponse starter : starters) {

            if (
                    substitutions.size()
                            >= maxSubstitutions
            ) {
                break;
            }

            if (
                    !requiresSubstitution(
                            starter,
                            simulatedUnavailableIds
                    )
            ) {
                continue;
            }

            LineupPlayerResponse replacement =
                    bench.stream()
                            .filter(candidate ->
                                    candidate.role()
                                            .equals(
                                                    starter.role()
                                            )
                            )
                            .filter(
                                    this::
                                            isUsableSubstitute
                            )
                            .filter(candidate ->
                                    !usedBenchPlayerIds
                                            .contains(
                                                    candidate
                                                            .playerId()
                                            )
                            )
                            .findFirst()
                            .orElse(null);

            if (replacement == null) {
                continue;
            }

            usedBenchPlayerIds.add(
                    replacement.playerId()
            );

            substitutions.add(
                    new LineupSubstitutionResponse(
                            starter,
                            replacement,
                            simulatedUnavailableIds
                                    .contains(
                                            starter.playerId()
                                    )
                                    ? "Sostituzione automatica: "
                                            + "assenza simulata"
                                    : "Sostituzione automatica "
                                            + "per stato "
                                            + starter
                                                    .availabilityStatus()
                    )
            );
        }

        return substitutions;
    }

    private boolean requiresSubstitution(
            LineupPlayerResponse player,
            List<Long> simulatedUnavailableIds
    ) {

        return simulatedUnavailableIds
                .contains(
                        player.playerId()
                )
                || isHardUnavailable(
                        player.availabilityStatus()
                );
    }

    private boolean isUsableSubstitute(
            LineupPlayerResponse player
    ) {

        return !isHardUnavailable(
                player.availabilityStatus()
        )
                && player.selectionScore() != null;
    }

    private boolean isHardUnavailable(
            String status
    ) {

        return "INJURED".equals(status)
                || "SUSPENDED".equals(status)
                || "UNAVAILABLE".equals(status);
    }

    private List<LineupPlayerResponse> createSmartBench(
            List<PlayerCandidate> candidates,
            List<Long> starterIds
    ) {

        List<PlayerCandidate> reserves =
                candidates.stream()
                        .filter(candidate ->
                                !starterIds.contains(
                                        candidate.playerId()
                                )
                        )
                        .toList();

        List<PlayerCandidate> orderedBench =
                new ArrayList<>();

        addBenchPlayersByRole(
                orderedBench,
                reserves,
                "P"
        );

        addBenchPlayersByRole(
                orderedBench,
                reserves,
                "D"
        );

        addBenchPlayersByRole(
                orderedBench,
                reserves,
                "C"
        );

        addBenchPlayersByRole(
                orderedBench,
                reserves,
                "A"
        );

        return orderedBench.stream()
                .map(this::toResponse)
                .toList();
    }

    private void addBenchPlayersByRole(
            List<PlayerCandidate> bench,
            List<PlayerCandidate> reserves,
            String role
    ) {

        reserves.stream()
                .filter(candidate ->
                        role.equals(
                                candidate.role()
                        )
                )
                .sorted(
                        Comparator
                                .comparingInt(
                                        this::
                                                availabilityPriority
                                )
                                .thenComparing(
                                        candidate ->
                                                candidate
                                                        .selectionScore()
                                                        == null
                                                        ? BigDecimal
                                                                .valueOf(-1)
                                                        : candidate
                                                                .selectionScore(),
                                        Comparator
                                                .reverseOrder()
                                )
                                .thenComparing(
                                        PlayerCandidate::
                                                playerId
                                )
                )
                .forEach(
                        bench::add
                );
    }

    private int availabilityPriority(
            PlayerCandidate candidate
    ) {

        return switch (
                candidate.availabilityStatus()
        ) {
            case "AVAILABLE", "PROBABLE" -> 1;
            case "DOUBTFUL" -> 2;
            case "UNKNOWN" -> 3;
            case "INJURED" -> 4;
            case "SUSPENDED" -> 5;
            case "UNAVAILABLE" -> 6;
            default -> 7;
        };
    }

    private Map<String, LineupRoleSummaryResponse>
            createRoleSummary(
                    Formation formation,
                    List<PlayerCandidate> selected
            ) {

        Map<String, LineupRoleSummaryResponse> roles =
                new LinkedHashMap<>();

        roles.put(
                "P",
                summary(
                        selected,
                        "P",
                        1
                )
        );

        roles.put(
                "D",
                summary(
                        selected,
                        "D",
                        formation.defenders()
                )
        );

        roles.put(
                "C",
                summary(
                        selected,
                        "C",
                        formation.midfielders()
                )
        );

        roles.put(
                "A",
                summary(
                        selected,
                        "A",
                        formation.forwards()
                )
        );

        return roles;
    }

    private LineupRoleSummaryResponse summary(
            List<PlayerCandidate> selected,
            String role,
            int required
    ) {

        int current =
                (int) selected.stream()
                        .filter(candidate ->
                                role.equals(
                                        candidate.role()
                                )
                        )
                        .count();

        return new LineupRoleSummaryResponse(
                required,
                current,
                Math.max(
                        0,
                        required - current
                )
        );
    }

    private void appendRoleWarning(
            List<String> warnings,
            Map<String, LineupRoleSummaryResponse> roles,
            String role,
            String roleName
    ) {

        LineupRoleSummaryResponse summary =
                roles.get(role);

        if (summary.missing() > 0) {
            warnings.add(
                    "Mancano "
                            + summary.missing()
                            + " "
                            + roleName
                            + "."
            );
        }
    }

    private Comparator<PlayerCandidate>
            candidateRoleComparator() {

        return Comparator
                .comparingInt(
                        (PlayerCandidate player) ->
                                roleOrder(
                                        player.role()
                                )
                )
                .thenComparing(
                        PlayerCandidate::
                                selectionScore,
                        Comparator.reverseOrder()
                )
                .thenComparing(
                        PlayerCandidate::
                                playerId
                );
    }

    private int roleOrder(
            String role
    ) {

        return switch (role) {
            case "P" -> 1;
            case "D" -> 2;
            case "C" -> 3;
            case "A" -> 4;
            default -> 5;
        };
    }

    private LineupPlayerResponse toResponse(
            PlayerCandidate candidate
    ) {

        return new LineupPlayerResponse(
                candidate.playerId(),
                candidate.playerName(),
                candidate.club(),
                candidate.role(),
                candidate.opponent(),
                candidate.home(),
                candidate.fixtureDifficulty(),
                candidate.selectionScore(),
                candidate.availabilityStatus(),
                candidate.reason()
        );
    }

    private String createReason(
            String availabilityStatus,
            B68LineupScorer.ScoreResult score,
            B69FixtureMatchupResponse fixture,
            B69RoleDifficultyResponse roleMatchup,
            Integer historySeasons
    ) {

        List<String> evidence =
                new ArrayList<>();

        evidence.add(
                "stato "
                        + availabilityStatus
        );

        if (
                score.performanceScore()
                        != null
        ) {
            evidence.add(
                    "performance B66 "
                            + score
                                    .performanceScore()
                            + "/100"
            );
        }

        if (
                score.startingProbability()
                        != null
        ) {
            evidence.add(
                    "titolarit\u00E0 "
                            + score
                                    .startingProbability()
                            + "%"
            );
        }

        if (
                score.availabilityProbability()
                        != null
        ) {
            evidence.add(
                    "disponibilit\u00E0 "
                            + score
                                    .availabilityProbability()
                            + "%"
            );
        }

        if (fixture != null) {

            evidence.add(
                    "avversario "
                            + fixture.opponent()
            );

            evidence.add(
                    "difficolt\u00E0 "
                            + (
                                    roleMatchup == null
                                            || roleMatchup.difficultyScore()
                                                    == null
                                            ? "non disponibile"
                                            : roleMatchup.difficultyScore()
                                                    + "/100"
                            )
            );

            evidence.add(
                    fixture.home()
                            ? "casa"
                            : "trasferta"
            );
        }

        evidence.add(
                "evidenza "
                        + score.evidenceTier()
        );

        evidence.add(
                "confidence "
                        + score.confidence()
                        + "/100"
        );

        if (historySeasons != null) {
            evidence.add(
                    "storico "
                            + historySeasons
                            + " stagioni"
            );
        }

        if (
                score.exclusionReason()
                        != null
        ) {
            evidence.add(
                    score.exclusionReason()
            );
        }

        return String.join(
                ", ",
                evidence
        ) + ".";
    }

    private BigDecimal decimalOrNull(
            Object value
    ) {

        if (value == null) {
            return null;
        }

        if (value instanceof BigDecimal decimal) {
            return decimal;
        }

        if (value instanceof Number number) {
            return BigDecimal.valueOf(
                    number.doubleValue()
            );
        }

        return new BigDecimal(
                value.toString()
        );
    }

    private Double doubleOrNull(
            Object value
    ) {

        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.doubleValue();
        }

        return Double.valueOf(
                value.toString()
        );
    }

    private Integer integerOrNull(
            Object value
    ) {

        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.intValue();
        }

        return Integer.valueOf(
                value.toString()
        );
    }

    private String stringValue(
            Object value
    ) {

        return value == null
                ? null
                : value.toString();
    }

    private record Formation(
            String name,
            int defenders,
            int midfielders,
            int forwards
    ) {
    }

    private record FormationResult(
            Formation formation,
            List<PlayerCandidate> selected,
            int selectedPlayers,
            BigDecimal totalScore
    ) {
    }

    private record PlayerCandidate(
            Long playerId,
            String playerName,
            String club,
            String role,
            String opponent,
            boolean home,
            BigDecimal fixtureDifficulty,
            BigDecimal selectionScore,
            String reason,
            String availabilityStatus,
            boolean actionable,
            String evidenceTier,
            BigDecimal confidence
    ) {
    }
}