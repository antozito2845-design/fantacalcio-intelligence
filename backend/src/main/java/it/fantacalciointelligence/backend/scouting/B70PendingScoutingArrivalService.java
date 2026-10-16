package it.fantacalciointelligence.backend.scouting;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import it.fantacalciointelligence.backend.common.BadRequestException;

@Service
public class B70PendingScoutingArrivalService {

    private static final Set<String> ROLES =
            Set.of("P", "D", "C", "A");

    private final JdbcClient jdbcClient;

    public B70PendingScoutingArrivalService(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    public List<B70PendingScoutingArrivalResponse> findPending(
            String season,
            String role,
            String club
    ) {

        String normalizedSeason =
                requireText(
                        season,
                        "Stagione obbligatoria"
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

                            event.effective_at,
                            event.transfer_claim_id,
                            event.source AS transfer_source,
                            event.source_reference,
                            event.created_at AS detected_at

                        FROM player_registration_events event

                        JOIN seasons season
                            ON season.id = event.season_id

                        JOIN players player
                            ON player.id = event.player_id
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

                          AND event.event_type =
                              'TRANSFER_IN_SERIE_A'

                          AND NOT EXISTS (
                              SELECT 1
                              FROM foreign_player_scouting_reports report
                              WHERE report.player_id = player.id
                                AND report.season_id = season.id
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
                            event.effective_at DESC,
                            event.created_at DESC,
                            player.id ASC
                        """)
                .param(
                        "season",
                        normalizedSeason
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
                                new B70PendingScoutingArrivalResponse(
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
                                        resultSet.getObject(
                                                "effective_at",
                                                java.time.LocalDate.class
                                        ),
                                        resultSet.getLong(
                                                "transfer_claim_id"
                                        ),
                                        resultSet.getString(
                                                "transfer_source"
                                        ),
                                        resultSet.getString(
                                                "source_reference"
                                        ),
                                        "SCOUTING_REQUIRED",
                                        resultSet.getObject(
                                                "detected_at",
                                                java.time.OffsetDateTime.class
                                        )
                                )
                )
                .list();
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
                normalized.toUpperCase(
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