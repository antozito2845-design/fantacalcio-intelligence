package it.fantacalciointelligence.backend.scouting;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import it.fantacalciointelligence.backend.common.BadRequestException;

@Service
public class B70ArrivalScoutingService {

    private static final Set<String> ROLES =
            Set.of("P", "D", "C", "A");

    private static final Set<String> RECOMMENDATIONS =
            Set.of(
                    "HIGH_PRIORITY",
                    "RECOMMENDED",
                    "MONITOR",
                    "HIGH_RISK"
            );

    private final JdbcClient jdbcClient;

    public B70ArrivalScoutingService(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    public List<B70ArrivalScoutingResponse> findCurrentArrivals(
            String season,
            String recommendation,
            String role,
            String club
    ) {

        String normalizedSeason =
                requireText(
                        season,
                        "Stagione obbligatoria"
                );

        String normalizedRecommendation =
                normalizeRecommendation(
                        recommendation
                );

        String normalizedRole =
                normalizeRole(role);

        String normalizedClub =
                normalizeOptionalText(club);

        return jdbcClient.sql("""
                        SELECT
                            player.id AS player_id,
                            player.display_name AS player_name,
                            club.name AS club_name,
                            fantasy_role.fantasy_role,
                            season.code AS season_code,

                            report.previous_club,
                            report.previous_league,
                            report.previous_country_code,
                            report.transfer_type,

                            report.adaptation_score,
                            report.potential_score,
                            report.risk_score,
                            report.confidence_score,

                            report.recommendation,
                            report.scouting_summary,
                            report.updated_at

                        FROM foreign_player_scouting_reports report

                        JOIN seasons season
                            ON season.id = report.season_id

                        JOIN players player
                            ON player.id = report.player_id
                           AND player.active = TRUE

                        JOIN player_registrations registration
                            ON registration.player_id = player.id
                           AND registration.season_id = season.id
                           AND registration.registration_status =
                               'ACTIVE'

                        JOIN season_clubs membership
                            ON membership.season_id = season.id
                           AND membership.club_id =
                               registration.club_id

                        JOIN clubs club
                            ON club.id = registration.club_id

                        JOIN player_roles fantasy_role
                            ON fantasy_role.player_id = player.id
                           AND fantasy_role.season_id = season.id
                           AND fantasy_role.source =
                               'FANTACALCIO'

                        WHERE season.code = :season

                          AND (
                              CAST(:recommendation AS VARCHAR)
                                  IS NULL
                              OR report.recommendation =
                                  CAST(
                                      :recommendation
                                      AS VARCHAR
                                  )
                          )

                          AND (
                              CAST(:role AS VARCHAR) IS NULL
                              OR fantasy_role.fantasy_role =
                                  CAST(:role AS VARCHAR)
                          )

                          AND (
                              CAST(:club AS VARCHAR) IS NULL
                              OR LOWER(club.name) =
                                  LOWER(
                                      CAST(
                                          :club
                                          AS VARCHAR
                                      )
                                  )
                              OR LOWER(club.short_name) =
                                  LOWER(
                                      CAST(
                                          :club
                                          AS VARCHAR
                                      )
                                  )
                              OR LOWER(club.slug) =
                                  LOWER(
                                      CAST(
                                          :club
                                          AS VARCHAR
                                      )
                                  )
                          )

                        ORDER BY
                            CASE report.recommendation
                                WHEN 'HIGH_PRIORITY' THEN 1
                                WHEN 'RECOMMENDED' THEN 2
                                WHEN 'MONITOR' THEN 3
                                WHEN 'HIGH_RISK' THEN 4
                                ELSE 5
                            END,

                            report.potential_score DESC,
                            report.confidence_score DESC,
                            report.risk_score ASC,
                            player.id ASC
                        """)
                .param(
                        "season",
                        normalizedSeason
                )
                .param(
                        "recommendation",
                        normalizedRecommendation
                )
                .param(
                        "role",
                        normalizedRole
                )
                .param(
                        "club",
                        normalizedClub
                )
                .query(
                        (resultSet, rowNumber) ->
                                new B70ArrivalScoutingResponse(
                                        resultSet.getLong(
                                                "player_id"
                                        ),
                                        resultSet.getString(
                                                "player_name"
                                        ),
                                        resultSet.getString(
                                                "club_name"
                                        ),
                                        resultSet.getString(
                                                "fantasy_role"
                                        ),
                                        resultSet.getString(
                                                "season_code"
                                        ),
                                        resultSet.getString(
                                                "previous_club"
                                        ),
                                        resultSet.getString(
                                                "previous_league"
                                        ),
                                        resultSet.getString(
                                                "previous_country_code"
                                        ),
                                        resultSet.getString(
                                                "transfer_type"
                                        ),
                                        resultSet.getBigDecimal(
                                                "adaptation_score"
                                        ),
                                        resultSet.getBigDecimal(
                                                "potential_score"
                                        ),
                                        resultSet.getBigDecimal(
                                                "risk_score"
                                        ),
                                        resultSet.getBigDecimal(
                                                "confidence_score"
                                        ),
                                        resultSet.getString(
                                                "recommendation"
                                        ),
                                        resultSet.getString(
                                                "scouting_summary"
                                        ),
                                        "CONFIRMED_SERIE_A",
                                        resultSet.getObject(
                                                "updated_at",
                                                java.time.OffsetDateTime.class
                                        )
                                )
                )
                .list();
    }

    private String normalizeRecommendation(
            String recommendation
    ) {

        String normalized =
                normalizeOptionalText(
                        recommendation
                );

        if (normalized == null) {
            return null;
        }

        normalized =
                normalized
                        .toUpperCase(
                                Locale.ROOT
                        );

        if (!RECOMMENDATIONS.contains(normalized)) {

            throw new BadRequestException(
                    "Raccomandazione scouting non valida: "
                            + recommendation
            );
        }

        return normalized;
    }

    private String normalizeRole(
            String role
    ) {

        String normalized =
                normalizeOptionalText(role);

        if (normalized == null) {
            return null;
        }

        normalized =
                normalized
                        .toUpperCase(
                                Locale.ROOT
                        );

        if (!ROLES.contains(normalized)) {

            throw new BadRequestException(
                    "Ruolo non valido: "
                            + role
            );
        }

        return normalized;
    }

    private String requireText(
            String value,
            String message
    ) {

        if (
            value == null
            || value.isBlank()
        ) {

            throw new BadRequestException(
                    message
            );
        }

        return value.trim();
    }

    private String normalizeOptionalText(
            String value
    ) {

        if (
            value == null
            || value.isBlank()
        ) {
            return null;
        }

        return value.trim();
    }
}