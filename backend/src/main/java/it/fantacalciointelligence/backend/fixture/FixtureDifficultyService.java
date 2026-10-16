package it.fantacalciointelligence.backend.fixture;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FixtureDifficultyService {

    private static final double VENUE_ADVANTAGE = 4.0;

    private final JdbcClient jdbcClient;

    public FixtureDifficultyService(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional(readOnly = true)
    public List<FixtureDifficultyResponse> findClubFixtures(
            String season,
            String club,
            int fromMatchday,
            int toMatchday
    ) {
        List<Map<String, Object>> rows =
                jdbcClient.sql("""
                        SELECT
                            fixture.id,
                            fixture.matchday,
                            fixture.kickoff_at,

                            home_club.name AS home_name,
                            home_club.slug AS home_slug,

                            away_club.name AS away_name,
                            away_club.slug AS away_slug,

                            home_strength.overall_rating
                                AS home_overall,

                            home_strength.attacking_rating
                                AS home_attack,

                            home_strength.defensive_rating
                                AS home_defense,

                            home_strength.home_rating
                                AS home_venue_rating,

                            away_strength.overall_rating
                                AS away_overall,

                            away_strength.attacking_rating
                                AS away_attack,

                            away_strength.defensive_rating
                                AS away_defense,

                            away_strength.away_rating
                                AS away_venue_rating

                        FROM fixtures fixture

                        JOIN seasons season
                            ON season.id = fixture.season_id

                        JOIN clubs home_club
                            ON home_club.id =
                                fixture.home_club_id

                        JOIN clubs away_club
                            ON away_club.id =
                                fixture.away_club_id

                        JOIN club_strength_ratings home_strength
                            ON home_strength.club_id =
                                home_club.id
                            AND home_strength.season_id =
                                season.id

                        JOIN club_strength_ratings away_strength
                            ON away_strength.club_id =
                                away_club.id
                            AND away_strength.season_id =
                                season.id

                        WHERE season.code = :season
                          AND fixture.matchday BETWEEN
                              :fromMatchday
                              AND :toMatchday

                          AND (
                              LOWER(home_club.slug) =
                                  LOWER(:club)

                              OR LOWER(away_club.slug) =
                                  LOWER(:club)
                          )

                        ORDER BY
                            fixture.matchday,
                            fixture.id
                        """)
                        .param("season", season)
                        .param("club", club)
                        .param(
                                "fromMatchday",
                                fromMatchday
                        )
                        .param(
                                "toMatchday",
                                toMatchday
                        )
                        .query()
                        .listOfRows();

        List<FixtureDifficultyResponse> response =
                new ArrayList<>();

        for (Map<String, Object> row : rows) {
            boolean home =
                    row.get("home_slug")
                            .toString()
                            .equalsIgnoreCase(club);

            double difficulty = home
                    ? calculateHomeDifficulty(row)
                    : calculateAwayDifficulty(row);

            response.add(
                    new FixtureDifficultyResponse(
                            number(row, "id").longValue(),

                            number(
                                    row,
                                    "matchday"
                            ).intValue(),

                            toOffsetDateTime(
                                    row.get("kickoff_at")
                            ),

                            home
                                    ? row.get(
                                            "home_name"
                                    ).toString()
                                    : row.get(
                                            "away_name"
                                    ).toString(),

                            home
                                    ? row.get(
                                            "away_name"
                                    ).toString()
                                    : row.get(
                                            "home_name"
                                    ).toString(),

                            home,

                            decimal(difficulty),

                            difficultyLevel(difficulty)
                    )
            );
        }

        return response;
    }

    @Transactional(readOnly = true)
    public List<MatchdayFixtureResponse> findMatchday(
            String season,
            int matchday
    ) {
        List<Map<String, Object>> rows =
                jdbcClient.sql("""
                        SELECT
                            fixture.id,
                            fixture.matchday,

                            home_club.name AS home_name,
                            away_club.name AS away_name,

                            home_strength.overall_rating
                                AS home_overall,

                            home_strength.attacking_rating
                                AS home_attack,

                            home_strength.defensive_rating
                                AS home_defense,

                            home_strength.home_rating
                                AS home_venue_rating,

                            away_strength.overall_rating
                                AS away_overall,

                            away_strength.attacking_rating
                                AS away_attack,

                            away_strength.defensive_rating
                                AS away_defense,

                            away_strength.away_rating
                                AS away_venue_rating

                        FROM fixtures fixture

                        JOIN seasons season
                            ON season.id = fixture.season_id

                        JOIN clubs home_club
                            ON home_club.id =
                                fixture.home_club_id

                        JOIN clubs away_club
                            ON away_club.id =
                                fixture.away_club_id

                        JOIN club_strength_ratings home_strength
                            ON home_strength.club_id =
                                home_club.id
                            AND home_strength.season_id =
                                season.id

                        JOIN club_strength_ratings away_strength
                            ON away_strength.club_id =
                                away_club.id
                            AND away_strength.season_id =
                                season.id

                        WHERE season.code = :season
                          AND fixture.matchday = :matchday

                        ORDER BY
                            fixture.kickoff_at NULLS LAST,
                            fixture.id
                        """)
                        .param("season", season)
                        .param("matchday", matchday)
                        .query()
                        .listOfRows();

        List<MatchdayFixtureResponse> response =
                new ArrayList<>();

        for (Map<String, Object> row : rows) {
            double homeDifficulty =
                    calculateHomeDifficulty(row);

            double awayDifficulty =
                    calculateAwayDifficulty(row);

            response.add(
                    new MatchdayFixtureResponse(
                            number(row, "id").longValue(),

                            number(
                                    row,
                                    "matchday"
                            ).intValue(),

                            row.get(
                                    "home_name"
                            ).toString(),

                            row.get(
                                    "away_name"
                            ).toString(),

                            difficultyLevel(
                                    homeDifficulty
                            ),

                            difficultyLevel(
                                    awayDifficulty
                            )
                    )
            );
        }

        return response;
    }

    private double calculateHomeDifficulty(
            Map<String, Object> row
    ) {
        double opponentVenue =
                value(row, "away_venue_rating");

        double opponentOverall =
                value(row, "away_overall");

        double opponentAttack =
                value(row, "away_attack");

        double ownDefense =
                value(row, "home_defense");

        return clamp(
                opponentVenue * 0.35
                + opponentOverall * 0.20
                + opponentAttack * 0.30
                + (100 - ownDefense) * 0.15
                - VENUE_ADVANTAGE
        );
    }

    private double calculateAwayDifficulty(
            Map<String, Object> row
    ) {
        double opponentVenue =
                value(row, "home_venue_rating");

        double opponentOverall =
                value(row, "home_overall");

        double opponentAttack =
                value(row, "home_attack");

        double ownDefense =
                value(row, "away_defense");

        return clamp(
                opponentVenue * 0.35
                + opponentOverall * 0.20
                + opponentAttack * 0.30
                + (100 - ownDefense) * 0.15
                + VENUE_ADVANTAGE
        );
    }

    private double clamp(double value) {
        return Math.max(
                0,
                Math.min(value, 100)
        );
    }

    private String difficultyLevel(double score) {
        if (score < 50) {
            return "FACILE";
        }

        if (score < 70) {
            return "MEDIA";
        }

        return "DIFFICILE";
    }

    private BigDecimal decimal(double value) {
        return BigDecimal
                .valueOf(value)
                .setScale(
                        2,
                        RoundingMode.HALF_UP
                );
    }

    private Number number(
            Map<String, Object> row,
            String key
    ) {
        Object value = row.get(key);

        if (value instanceof Number number) {
            return number;
        }

        return 0;
    }

    private double value(
            Map<String, Object> row,
            String key
    ) {
        Object value = row.get(key);

        if (value instanceof Number number) {
            return number.doubleValue();
        }

        return 50.0;
    }

    private OffsetDateTime toOffsetDateTime(
            Object value
    ) {
        if (value == null) {
            return null;
        }

        if (value instanceof OffsetDateTime dateTime) {
            return dateTime;
        }

        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp
                    .toInstant()
                    .atZone(
                            ZoneId.of("Europe/Rome")
                    )
                    .toOffsetDateTime();
        }

        if (value instanceof LocalDateTime dateTime) {
            return dateTime
                    .atZone(
                            ZoneId.of("Europe/Rome")
                    )
                    .toOffsetDateTime();
        }

        throw new IllegalArgumentException(
                "Formato data non supportato: "
                        + value.getClass().getName()
        );
    }
}