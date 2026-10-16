package it.fantacalciointelligence.backend.calendarintelligence;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FixtureWindowService {

    private static final double VENUE_ADVANTAGE =
            4.0;

    private static final List<String> B69_ROLES =
            List.of(
                    "P",
                    "D",
                    "C",
                    "A"
            );

    private final JdbcClient jdbcClient;

    private final B69RoleMatchupScorer roleMatchupScorer;

    public FixtureWindowService(
            JdbcClient jdbcClient,
            B69RoleMatchupScorer roleMatchupScorer
    ) {
        this.jdbcClient = jdbcClient;
        this.roleMatchupScorer = roleMatchupScorer;
    }

    /**
     * Compatibility path used by the existing calendar-combination
     * and live-auction layers.
     *
     * B69 deliberately keeps this API stable while the role-aware
     * calendar is introduced alongside it.
     */
    @Transactional(readOnly = true)
    public Map<Long, ClubFixtureProfile> loadClubProfiles(
            String season,
            List<Long> clubIds,
            int from,
            int horizon
    ) {

        validateWindow(
                season,
                clubIds,
                from,
                horizon
        );

        if (clubIds.isEmpty()) {
            return Map.of();
        }

        int to =
                Math.min(
                        38,
                        from + horizon - 1
                );

        List<Map<String, Object>> rows =
                loadRows(
                        season,
                        clubIds,
                        from,
                        to
                );

        Map<Long, String> names =
                new LinkedHashMap<>();

        Map<Long, Map<Integer, ClubFixtureDay>> days =
                new LinkedHashMap<>();

        for (
                Long id :
                clubIds.stream()
                        .distinct()
                        .sorted()
                        .toList()
        ) {
            days.put(
                    id,
                    new LinkedHashMap<>()
            );
        }

        for (Map<String, Object> row : rows) {

            Long homeId =
                    requiredNumber(
                            row,
                            "home_id"
                    ).longValue();

            Long awayId =
                    requiredNumber(
                            row,
                            "away_id"
                    ).longValue();

            if (days.containsKey(homeId)) {

                addLegacyDay(
                        row,
                        homeId,
                        string(
                                row,
                                "home_name"
                        ),
                        true,
                        days,
                        names
                );
            }

            if (days.containsKey(awayId)) {

                addLegacyDay(
                        row,
                        awayId,
                        string(
                                row,
                                "away_name"
                        ),
                        false,
                        days,
                        names
                );
            }
        }

        Map<Long, ClubFixtureProfile> result =
                new LinkedHashMap<>();

        days.forEach(
                (id, map) ->
                        result.put(
                                id,
                                new ClubFixtureProfile(
                                        id,
                                        names.get(id),
                                        Map.copyOf(map)
                                )
                        )
        );

        return Map.copyOf(result);
    }

    /**
     * B69 role-aware calendar window.
     *
     * One DB query loads the requested fixture window and all club
     * strength inputs. The same rows are then converted into four
     * fantasy-role matchup scores: P, D, C and A.
     */
    @Transactional(readOnly = true)
    public Map<Long, B69RoleFixtureProfile> loadRoleAwareClubProfiles(
            String season,
            List<Long> clubIds,
            int from,
            int horizon
    ) {

        validateWindow(
                season,
                clubIds,
                from,
                horizon
        );

        if (clubIds.isEmpty()) {
            return Map.of();
        }

        int to =
                Math.min(
                        38,
                        from + horizon - 1
                );

        List<Map<String, Object>> rows =
                loadRows(
                        season,
                        clubIds,
                        from,
                        to
                );

        Map<Long, String> names =
                new LinkedHashMap<>();

        Map<Long, Map<Integer, B69RoleFixtureDay>> days =
                new LinkedHashMap<>();

        for (
                Long id :
                clubIds.stream()
                        .distinct()
                        .sorted()
                        .toList()
        ) {
            days.put(
                    id,
                    new LinkedHashMap<>()
            );
        }

        for (Map<String, Object> row : rows) {

            Long homeId =
                    requiredNumber(
                            row,
                            "home_id"
                    ).longValue();

            Long awayId =
                    requiredNumber(
                            row,
                            "away_id"
                    ).longValue();

            if (days.containsKey(homeId)) {

                addRoleAwareDay(
                        row,
                        homeId,
                        string(
                                row,
                                "home_name"
                        ),
                        awayId,
                        string(
                                row,
                                "away_name"
                        ),
                        true,
                        days,
                        names
                );
            }

            if (days.containsKey(awayId)) {

                addRoleAwareDay(
                        row,
                        awayId,
                        string(
                                row,
                                "away_name"
                        ),
                        homeId,
                        string(
                                row,
                                "home_name"
                        ),
                        false,
                        days,
                        names
                );
            }
        }

        Map<Long, B69RoleFixtureProfile> result =
                new LinkedHashMap<>();

        days.forEach(
                (id, map) ->
                        result.put(
                                id,
                                new B69RoleFixtureProfile(
                                        id,
                                        names.get(id),
                                        Map.copyOf(map)
                                )
                        )
        );

        return Map.copyOf(result);
    }

    private List<Map<String, Object>> loadRows(
            String season,
            List<Long> clubIds,
            int from,
            int to
    ) {

        return jdbcClient.sql("""
                SELECT
                    fixture.id AS fixture_id,
                    fixture.matchday,
                    fixture.kickoff_at,
                    fixture.status,

                    home.id AS home_id,
                    home.name AS home_name,

                    away.id AS away_id,
                    away.name AS away_name,

                    home_strength.overall_rating
                        AS home_overall,

                    home_strength.attacking_rating
                        AS home_attack,

                    home_strength.defensive_rating
                        AS home_defense,

                    home_strength.home_rating
                        AS home_home_rating,

                    home_strength.away_rating
                        AS home_away_rating,

                    away_strength.overall_rating
                        AS away_overall,

                    away_strength.attacking_rating
                        AS away_attack,

                    away_strength.defensive_rating
                        AS away_defense,

                    away_strength.home_rating
                        AS away_home_rating,

                    away_strength.away_rating
                        AS away_away_rating

                FROM fixtures fixture

                JOIN seasons season
                    ON season.id =
                        fixture.season_id

                JOIN clubs home
                    ON home.id =
                        fixture.home_club_id

                JOIN clubs away
                    ON away.id =
                        fixture.away_club_id

                LEFT JOIN club_strength_ratings
                    home_strength

                    ON home_strength.club_id =
                        home.id

                    AND home_strength.season_id =
                        season.id

                LEFT JOIN club_strength_ratings
                    away_strength

                    ON away_strength.club_id =
                        away.id

                    AND away_strength.season_id =
                        season.id

                WHERE season.code =
                    :season

                  AND fixture.matchday
                    BETWEEN :from AND :to

                  AND (
                      fixture.home_club_id
                        IN (:clubIds)

                      OR fixture.away_club_id
                        IN (:clubIds)
                  )

                ORDER BY
                    fixture.matchday,
                    fixture.id
                """)
                .param(
                        "season",
                        season
                )
                .param(
                        "from",
                        from
                )
                .param(
                        "to",
                        to
                )
                .param(
                        "clubIds",
                        clubIds
                )
                .query()
                .listOfRows();
    }

    private void addLegacyDay(
            Map<String, Object> row,
            Long clubId,
            String clubName,
            boolean home,
            Map<Long, Map<Integer, ClubFixtureDay>> days,
            Map<Long, String> names
    ) {

        names.put(
                clubId,
                clubName
        );

        BigDecimal score =
                legacyDifficulty(
                        row,
                        home
                );

        int matchday =
                requiredNumber(
                        row,
                        "matchday"
                ).intValue();

        days.get(clubId)
                .put(
                        matchday,
                        new ClubFixtureDay(
                                clubId,
                                matchday,
                                requiredNumber(
                                        row,
                                        "fixture_id"
                                ).longValue(),
                                home,
                                string(
                                        row,
                                        "status"
                                ),
                                offset(
                                        row.get(
                                                "kickoff_at"
                                        )
                                ),
                                score,
                                CalendarDifficulty
                                        .fromScore(score)
                        )
                );
    }

    private void addRoleAwareDay(
            Map<String, Object> row,
            Long clubId,
            String clubName,
            Long opponentClubId,
            String opponentClubName,
            boolean home,
            Map<Long, Map<Integer, B69RoleFixtureDay>> days,
            Map<Long, String> names
    ) {

        names.put(
                clubId,
                clubName
        );

        BigDecimal ownAttack =
                decimal(
                        row,
                        home
                                ? "home_attack"
                                : "away_attack"
                );

        BigDecimal ownDefense =
                decimal(
                        row,
                        home
                                ? "home_defense"
                                : "away_defense"
                );

        BigDecimal opponentOverall =
                decimal(
                        row,
                        home
                                ? "away_overall"
                                : "home_overall"
                );

        BigDecimal opponentAttack =
                decimal(
                        row,
                        home
                                ? "away_attack"
                                : "home_attack"
                );

        BigDecimal opponentDefense =
                decimal(
                        row,
                        home
                                ? "away_defense"
                                : "home_defense"
                );

        BigDecimal opponentHome =
                decimal(
                        row,
                        home
                                ? "away_home_rating"
                                : "home_home_rating"
                );

        BigDecimal opponentAway =
                decimal(
                        row,
                        home
                                ? "away_away_rating"
                                : "home_away_rating"
                );

        Map<String, B69RoleMatchupScorer.MatchupResult>
                roleMatchups =
                new LinkedHashMap<>();

        for (String role : B69_ROLES) {

            roleMatchups.put(
                    role,
                    roleMatchupScorer.score(
                            new B69RoleMatchupScorer.Input(
                                    role,
                                    home,
                                    ownAttack,
                                    ownDefense,
                                    opponentOverall,
                                    opponentAttack,
                                    opponentDefense,
                                    opponentHome,
                                    opponentAway
                            )
                    )
            );
        }

        int matchday =
                requiredNumber(
                        row,
                        "matchday"
                ).intValue();

        days.get(clubId)
                .put(
                        matchday,
                        new B69RoleFixtureDay(
                                clubId,
                                clubName,
                                opponentClubId,
                                opponentClubName,
                                matchday,
                                requiredNumber(
                                        row,
                                        "fixture_id"
                                ).longValue(),
                                home,
                                string(
                                        row,
                                        "status"
                                ),
                                offset(
                                        row.get(
                                                "kickoff_at"
                                        )
                                ),
                                Map.copyOf(
                                        roleMatchups
                                )
                        )
                );
    }

    private BigDecimal legacyDifficulty(
            Map<String, Object> row,
            boolean home
    ) {

        String[] keys =
                home
                        ? new String[]{
                                "away_away_rating",
                                "away_overall",
                                "away_attack",
                                "home_defense"
                        }
                        : new String[]{
                                "home_home_rating",
                                "home_overall",
                                "home_attack",
                                "away_defense"
                        };

        for (String key : keys) {

            if (
                    !(row.get(key)
                            instanceof Number)
            ) {
                return null;
            }
        }

        double value =
                numberValue(
                        row,
                        keys[0]
                ) * 0.35

                + numberValue(
                        row,
                        keys[1]
                ) * 0.20

                + numberValue(
                        row,
                        keys[2]
                ) * 0.30

                + (
                        100
                        - numberValue(
                                row,
                                keys[3]
                        )
                ) * 0.15

                + (
                        home
                                ? -VENUE_ADVANTAGE
                                : VENUE_ADVANTAGE
                );

        return BigDecimal
                .valueOf(
                        Math.max(
                                0,
                                Math.min(
                                        100,
                                        value
                                )
                        )
                )
                .setScale(
                        2,
                        RoundingMode.HALF_UP
                );
    }

    private void validateWindow(
            String season,
            List<Long> clubIds,
            int from,
            int horizon
    ) {

        if (
                season == null
                || season.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "Season is required"
            );
        }

        if (clubIds == null) {
            throw new IllegalArgumentException(
                    "Club ids are required"
            );
        }

        if (
                from < 1
                || from > 38
                || horizon < 1
                || horizon > 10
        ) {
            throw new IllegalArgumentException(
                    "Invalid fixture window"
            );
        }
    }

    private BigDecimal decimal(
            Map<String, Object> row,
            String key
    ) {

        Object value =
                row.get(key);

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

        throw new IllegalArgumentException(
                "Unsupported numeric value for "
                        + key
                        + ": "
                        + value.getClass()
                                .getName()
        );
    }

    private Number requiredNumber(
            Map<String, Object> row,
            String key
    ) {

        Object value =
                row.get(key);

        if (value instanceof Number number) {
            return number;
        }

        throw new IllegalArgumentException(
                "Required numeric value missing: "
                        + key
        );
    }

    private double numberValue(
            Map<String, Object> row,
            String key
    ) {

        return requiredNumber(
                row,
                key
        ).doubleValue();
    }

    private String string(
            Map<String, Object> row,
            String key
    ) {

        Object value =
                row.get(key);

        return value == null
                ? null
                : value.toString();
    }

    private OffsetDateTime offset(
            Object value
    ) {

        if (value == null) {
            return null;
        }

        if (
                value
                instanceof OffsetDateTime result
        ) {
            return result;
        }

        if (
                value
                instanceof java.sql.Timestamp timestamp
        ) {
            return timestamp
                    .toInstant()
                    .atOffset(
                            java.time.ZoneOffset.UTC
                    );
        }

        if (
                value
                instanceof java.time.LocalDateTime localDateTime
        ) {
            return localDateTime
                    .atZone(
                            java.time.ZoneId.of(
                                    "Europe/Rome"
                            )
                    )
                    .toOffsetDateTime();
        }

        throw new IllegalArgumentException(
                "Unsupported kickoff type: "
                        + value.getClass()
                                .getName()
        );
    }
}