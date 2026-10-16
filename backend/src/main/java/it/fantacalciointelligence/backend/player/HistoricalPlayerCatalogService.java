package it.fantacalciointelligence.backend.player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.common.PageResponse;

@Service
public class HistoricalPlayerCatalogService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public HistoricalPlayerCatalogService(
            NamedParameterJdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public PageResponse<HistoricalPlayerCatalogResponse> findPlayers(
            String season,
            String search,
            String role,
            int page,
            int size
    ) {
        if (season == null || season.isBlank()) {
            throw new IllegalArgumentException(
                    "La stagione storica ? obbligatoria"
            );
        }

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = safePage * safeSize;

        Map<String, Object> params = new HashMap<>();
        params.put("season", season.trim());
        params.put("limit", safeSize);
        params.put("offset", offset);

        StringBuilder where = new StringBuilder("""
                WHERE s.code = :season
                  AND s.current_season = FALSE
                  AND role_fantacalcio.fantasy_role IS NOT NULL
                """);

        if (search != null && !search.isBlank()) {
            where.append("""
                    AND (
                        LOWER(p.display_name) LIKE :search
                        OR p.normalized_name LIKE :search
                    )
                    """);

            params.put(
                    "search",
                    "%" + search.trim().toLowerCase() + "%"
            );
        }

        if (role != null && !role.isBlank()) {
            where.append("""
                    AND role_fantacalcio.fantasy_role = :role
                    """);

            params.put(
                    "role",
                    role.trim().toUpperCase()
            );
        }

        String joins = """
                FROM players p

                INNER JOIN player_roles role_fantacalcio
                    ON role_fantacalcio.player_id = p.id
                    AND role_fantacalcio.source = 'FANTACALCIO'

                INNER JOIN seasons s
                    ON s.id = role_fantacalcio.season_id

                INNER JOIN player_season_statistics stats
                    ON stats.player_id = p.id
                    AND stats.season_id = s.id
                    AND stats.source = 'FANTACALCIO'

                LEFT JOIN clubs club
                    ON club.id = stats.club_id
                """;

        String dataSql = """
                SELECT
                    p.id,
                    p.display_name,
                    p.active,
                    s.code AS season_code,
                    club.name AS club_name,
                    club.slug AS club_slug,
                    role_fantacalcio.fantasy_role
                        AS fantacalcio_role,
                    stats.appearances,
                    stats.fantasy_average,
                    stats.goals,
                    stats.assists
                """
                + joins
                + where
                + """
                ORDER BY p.display_name ASC, p.id ASC
                LIMIT :limit
                OFFSET :offset
                """;

        List<HistoricalPlayerCatalogResponse> players =
                jdbcTemplate.query(
                        dataSql,
                        params,
                        (resultSet, rowNumber) ->
                                new HistoricalPlayerCatalogResponse(
                                        resultSet.getLong("id"),
                                        resultSet.getString(
                                                "display_name"
                                        ),
                                        resultSet.getBoolean("active"),
                                        resultSet.getString(
                                                "season_code"
                                        ),
                                        resultSet.getString(
                                                "club_name"
                                        ),
                                        resultSet.getString(
                                                "club_slug"
                                        ),
                                        resultSet.getString(
                                                "fantacalcio_role"
                                        ),
                                        resultSet.getObject(
                                                "appearances",
                                                Integer.class
                                        ),
                                        resultSet.getBigDecimal(
                                                "fantasy_average"
                                        ),
                                        resultSet.getObject(
                                                "goals",
                                                Integer.class
                                        ),
                                        resultSet.getObject(
                                                "assists",
                                                Integer.class
                                        )
                                )
                );

        String countSql = """
                SELECT COUNT(DISTINCT p.id)
                """
                + joins
                + where;

        Long total = jdbcTemplate.queryForObject(
                countSql,
                params,
                Long.class
        );

        long totalElements = total == null ? 0 : total;

        int totalPages = (int) Math.ceil(
                (double) totalElements / safeSize
        );

        return new PageResponse<>(
                players,
                safePage,
                safeSize,
                totalElements,
                totalPages
        );
    }
}
