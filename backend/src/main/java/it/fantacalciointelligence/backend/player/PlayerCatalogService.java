package it.fantacalciointelligence.backend.player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.common.PageResponse;

@Service
public class PlayerCatalogService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PlayerCatalogService(
            NamedParameterJdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public PageResponse<PlayerCatalogResponse> findPlayers(
            String season,
            String search,
            String club,
            String role,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = safePage * safeSize;

        StringBuilder where = new StringBuilder("""
                WHERE s.code = :season
                  AND p.active = TRUE
                  AND registration.registration_status = 'ACTIVE'
                  AND role_fantacalcio.fantasy_role IS NOT NULL
                """);

        Map<String, Object> params = new HashMap<>();
        params.put("season", season);
        params.put("limit", safeSize);
        params.put("offset", offset);

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

        if (club != null && !club.isBlank()) {
            where.append("""
                    AND (
                        LOWER(c.name) = :club
                        OR LOWER(c.slug) = :club
                    )
                    """);

            params.put(
                    "club",
                    club.trim().toLowerCase()
            );
        }

        if (role != null && !role.isBlank()) {
            where.append("""
                    AND EXISTS (
                        SELECT 1
                        FROM player_roles filter_role
                        WHERE filter_role.player_id = p.id
                          AND filter_role.season_id = s.id
                          AND filter_role.source = 'FANTACALCIO'
                          AND filter_role.fantasy_role = :role
                    )
                    """);

            params.put(
                    "role",
                    role.trim().toUpperCase()
            );
        }

        String joins = """
                FROM players p

                INNER JOIN player_reconciliation_results r
                    ON r.player_id = p.id

                INNER JOIN seasons s
                    ON s.id = r.season_id

                INNER JOIN player_registrations registration
                    ON registration.player_id = p.id
                    AND registration.season_id = s.id

                INNER JOIN clubs c
                    ON c.id = registration.club_id

                INNER JOIN season_clubs season_club
                    ON season_club.season_id = s.id
                    AND season_club.club_id = c.id

                LEFT JOIN player_roles role_fantacalcio
                    ON role_fantacalcio.player_id = p.id
                    AND role_fantacalcio.season_id = s.id
                    AND role_fantacalcio.source = 'FANTACALCIO'

                LEFT JOIN player_roles role_gazzetta
                    ON role_gazzetta.player_id = p.id
                    AND role_gazzetta.season_id = s.id
                    AND role_gazzetta.source = 'GAZZETTA'

                LEFT JOIN player_roles role_fantamarkt
                    ON role_fantamarkt.player_id = p.id
                    AND role_fantamarkt.season_id = s.id
                    AND role_fantamarkt.source = 'FANTAMARKT'
                """;

        String dataSql = """
                SELECT
                    p.id,
                    p.display_name,
                    c.name AS club_name,
                    c.slug AS club_slug,
                    role_fantacalcio.fantasy_role
                        AS fantacalcio_role,
                    role_gazzetta.fantasy_role
                        AS gazzetta_role,
                    role_fantamarkt.fantasy_role
                        AS fantamarkt_role,
                    r.sources_count
                """
                + joins
                + where
                + """
                ORDER BY p.display_name ASC
                LIMIT :limit
                OFFSET :offset
                """;

        List<PlayerCatalogResponse> players =
                jdbcTemplate.query(
                        dataSql,
                        params,
                        (resultSet, rowNumber) ->
                                new PlayerCatalogResponse(
                                        resultSet.getLong("id"),
                                        resultSet.getString(
                                                "display_name"
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
                                        resultSet.getString(
                                                "gazzetta_role"
                                        ),
                                        resultSet.getString(
                                                "fantamarkt_role"
                                        ),
                                        resultSet.getInt(
                                                "sources_count"
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
