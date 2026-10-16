package it.fantacalciointelligence.backend.player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.common.ResourceNotFoundException;

@Service
public class HistoricalPlayerDetailService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public HistoricalPlayerDetailService(
            NamedParameterJdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public HistoricalPlayerDetailResponse findById(
            Long playerId,
            String season
    ) {
        if (season == null || season.isBlank()) {
            throw new IllegalArgumentException(
                    "La stagione storica ? obbligatoria"
            );
        }

        Map<String, Object> params = Map.of(
                "playerId", playerId,
                "season", season.trim()
        );

        List<Identity> identities = jdbcTemplate.query(
                """
                SELECT
                    p.id,
                    p.display_name,
                    p.active,
                    s.id AS season_id,
                    s.code AS season_code,
                    club.name AS club_name,
                    club.slug AS club_slug
                FROM players p
                INNER JOIN player_roles role_fantacalcio
                    ON role_fantacalcio.player_id = p.id
                    AND role_fantacalcio.source = 'FANTACALCIO'
                INNER JOIN seasons s
                    ON s.id = role_fantacalcio.season_id
                INNER JOIN player_season_statistics stats_fantacalcio
                    ON stats_fantacalcio.player_id = p.id
                    AND stats_fantacalcio.season_id = s.id
                    AND stats_fantacalcio.source = 'FANTACALCIO'
                LEFT JOIN clubs club
                    ON club.id = stats_fantacalcio.club_id
                WHERE p.id = :playerId
                  AND s.code = :season
                  AND s.current_season = FALSE
                  AND role_fantacalcio.fantasy_role IS NOT NULL
                ORDER BY stats_fantacalcio.id
                LIMIT 1
                """,
                params,
                (rs, rowNum) ->
                        new Identity(
                                rs.getLong("id"),
                                rs.getString("display_name"),
                                rs.getBoolean("active"),
                                rs.getLong("season_id"),
                                rs.getString("season_code"),
                                rs.getString("club_name"),
                                rs.getString("club_slug")
                        )
        );

        Identity identity = identities.stream()
                .findFirst()
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Giocatore storico non trovato"
                        )
                );

        Map<String, Object> seasonParams = Map.of(
                "playerId", playerId,
                "seasonId", identity.seasonId()
        );

        Map<String, String> roles = new LinkedHashMap<>();

        jdbcTemplate.queryForList(
                """
                SELECT source, fantasy_role
                FROM player_roles
                WHERE player_id = :playerId
                  AND season_id = :seasonId
                  AND fantasy_role IS NOT NULL
                ORDER BY source
                """,
                seasonParams
        ).forEach(row -> {
            Object source = row.get("source");
            Object role = row.get("fantasy_role");

            if (source != null && role != null) {
                roles.put(
                        source.toString(),
                        role.toString()
                );
            }
        });

        List<PlayerSeasonStatisticsResponse> statistics =
                jdbcTemplate.query(
                        """
                        SELECT
                            s.code,
                            stats.source,
                            stats.source_external_id,
                            club.name AS club_name,
                            (
                                SELECT STRING_AGG(
                                    history.club_name,
                                    '|'
                                    ORDER BY history.source_order
                                )
                                FROM player_season_statistics_clubs history
                                WHERE history.statistics_id = stats.id
                            ) AS historical_clubs,
                            stats.appearances,
                            stats.starts,
                            stats.minutes_played,
                            stats.average_rating,
                            stats.fantasy_average,
                            stats.goals,
                            stats.assists,
                            stats.penalties_taken,
                            stats.penalties_scored,
                            stats.penalties_missed,
                            stats.yellow_cards,
                            stats.red_cards,
                            stats.clean_sheets,
                            stats.goals_conceded,
                            stats.saves,
                            stats.penalties_saved,
                            stats.expected_goals,
                            stats.expected_assists,
                            CASE
                                WHEN stats.minutes_played > 0
                                     AND stats.goals IS NOT NULL
                                THEN ROUND(
                                    stats.goals * 90.0
                                    / stats.minutes_played,
                                    2
                                )
                            END AS goals_per_90,
                            CASE
                                WHEN stats.minutes_played > 0
                                     AND stats.assists IS NOT NULL
                                THEN ROUND(
                                    stats.assists * 90.0
                                    / stats.minutes_played,
                                    2
                                )
                            END AS assists_per_90,
                            CASE
                                WHEN stats.appearances > 0
                                     AND stats.minutes_played IS NOT NULL
                                THEN ROUND(
                                    stats.minutes_played * 1.0
                                    / stats.appearances,
                                    2
                                )
                            END AS minutes_per_appearance,
                            CASE
                                WHEN stats.appearances > 0
                                     AND stats.starts IS NOT NULL
                                THEN ROUND(
                                    stats.starts * 100.0
                                    / stats.appearances,
                                    2
                                )
                            END AS start_percentage,
                            stats.imported_at,
                            stats.source_updated_at,
                            stats.import_batch_id
                        FROM player_season_statistics stats
                        JOIN seasons s
                            ON s.id = stats.season_id
                        LEFT JOIN clubs club
                            ON club.id = stats.club_id
                        WHERE stats.player_id = :playerId
                          AND stats.season_id = :seasonId
                        ORDER BY
                            CASE
                                WHEN stats.source = 'FANTACALCIO'
                                THEN 0
                                ELSE 1
                            END,
                            stats.source
                        """,
                        seasonParams,
                        (rs, rowNum) ->
                                new PlayerSeasonStatisticsResponse(
                                        rs.getString("code"),
                                        rs.getString("source"),
                                        rs.getString("source_external_id"),
                                        rs.getString("club_name"),
                                        splitHistoricalClubs(
                                                rs.getString("historical_clubs")
                                        ),
                                        rs.getObject("appearances", Integer.class),
                                        rs.getObject("starts", Integer.class),
                                        rs.getObject("minutes_played", Integer.class),
                                        rs.getBigDecimal("average_rating"),
                                        rs.getBigDecimal("fantasy_average"),
                                        rs.getObject("goals", Integer.class),
                                        rs.getObject("assists", Integer.class),
                                        rs.getObject("penalties_taken", Integer.class),
                                        rs.getObject("penalties_scored", Integer.class),
                                        rs.getObject("penalties_missed", Integer.class),
                                        rs.getObject("yellow_cards", Integer.class),
                                        rs.getObject("red_cards", Integer.class),
                                        rs.getObject("clean_sheets", Integer.class),
                                        rs.getObject("goals_conceded", Integer.class),
                                        rs.getObject("saves", Integer.class),
                                        rs.getObject("penalties_saved", Integer.class),
                                        rs.getBigDecimal("expected_goals"),
                                        rs.getBigDecimal("expected_assists"),
                                        rs.getBigDecimal("goals_per_90"),
                                        rs.getBigDecimal("assists_per_90"),
                                        rs.getBigDecimal("minutes_per_appearance"),
                                        rs.getBigDecimal("start_percentage"),
                                        rs.getObject(
                                                "imported_at",
                                                java.time.OffsetDateTime.class
                                        ),
                                        rs.getObject(
                                                "source_updated_at",
                                                java.time.OffsetDateTime.class
                                        ),
                                        rs.getObject(
                                                "import_batch_id",
                                                Long.class
                                        )
                                )
                );

        return new HistoricalPlayerDetailResponse(
                identity.id(),
                identity.name(),
                identity.active(),
                identity.season(),
                identity.club(),
                identity.clubSlug(),
                roles,
                statistics
        );
    }

    private List<String> splitHistoricalClubs(String value) {
        return value == null || value.isBlank()
                ? List.of()
                : List.of(value.split("\\|"));
    }

    private record Identity(
            Long id,
            String name,
            boolean active,
            Long seasonId,
            String season,
            String club,
            String clubSlug
    ) {
    }
}
