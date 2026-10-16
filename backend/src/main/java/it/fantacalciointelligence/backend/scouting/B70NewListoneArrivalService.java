package it.fantacalciointelligence.backend.scouting;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import it.fantacalciointelligence.backend.common.BadRequestException;

@Service
public class B70NewListoneArrivalService {

    private static final Set<String> ROLES =
            Set.of("P", "D", "C", "A");

    private final JdbcClient jdbcClient;

    public B70NewListoneArrivalService(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    public List<B70NewListoneArrivalResponse> findNewEntries(
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

        List<BatchSnapshot> batches =
                jdbcClient.sql("""
                        SELECT
                            batch.id,
                            COALESCE(
                                batch.source_observed_at,
                                batch.completed_at,
                                batch.started_at
                            ) AS observed_at
                        FROM import_batches batch
                        JOIN seasons season
                            ON season.id = batch.season_id
                        WHERE season.code = :season
                          AND batch.source = 'FANTACALCIO'
                          AND batch.status = 'COMPLETED'
                        ORDER BY
                            COALESCE(
                                batch.source_observed_at,
                                batch.completed_at,
                                batch.started_at
                            ) DESC,
                            batch.id DESC
                        LIMIT 2
                        """)
                .param(
                        "season",
                        normalizedSeason
                )
                .query(
                        (resultSet, rowNumber) ->
                                new BatchSnapshot(
                                        resultSet.getLong("id"),
                                        resultSet.getObject(
                                                "observed_at",
                                                java.time.OffsetDateTime.class
                                        )
                                )
                )
                .list();

        /*
         * With no previous snapshot there is no evidence that an
         * entry is new. Never classify the whole current listone
         * as newly arrived.
         */
        if (batches.size() < 2) {
            return List.of();
        }

        BatchSnapshot current =
                batches.get(0);

        BatchSnapshot previous =
                batches.get(1);

        return jdbcClient.sql("""
                        SELECT DISTINCT
                            player.id AS player_id,
                            player.display_name AS player_name,
                            club.name AS club_name,
                            fantasy_role.fantasy_role,
                            season.code AS season_code,
                            current_row.external_id,

                            EXISTS (
                                SELECT 1
                                FROM player_registration_events event
                                WHERE event.player_id = player.id
                                  AND event.season_id = season.id
                                  AND event.event_type =
                                      'TRANSFER_IN_SERIE_A'
                            ) AS official_transfer_event_present

                        FROM player_import_rows current_row

                        JOIN import_batches current_batch
                            ON current_batch.id =
                               current_row.batch_id

                        JOIN seasons season
                            ON season.id =
                               current_batch.season_id

                        JOIN player_source_mappings mapping
                            ON mapping.source =
                               'FANTACALCIO'
                           AND mapping.external_id =
                               current_row.external_id
                           AND mapping.verified = TRUE

                        JOIN players player
                            ON player.id = mapping.player_id
                           AND player.active = TRUE

                        JOIN player_registrations registration
                            ON registration.player_id =
                               player.id
                           AND registration.season_id =
                               season.id
                           AND registration.registration_status =
                               'ACTIVE'

                        JOIN season_clubs membership
                            ON membership.season_id =
                               season.id
                           AND membership.club_id =
                               registration.club_id

                        JOIN clubs club
                            ON club.id =
                               registration.club_id

                        JOIN player_roles fantasy_role
                            ON fantasy_role.player_id =
                               player.id
                           AND fantasy_role.season_id =
                               season.id
                           AND fantasy_role.source =
                               'FANTACALCIO'

                        WHERE current_row.batch_id =
                              :currentBatchId

                          AND current_row.valid = TRUE

                          AND current_row.external_id
                              IS NOT NULL

                          AND BTRIM(
                              current_row.external_id
                          ) <> ''

                          AND LOWER(
                              COALESCE(
                                  current_row.raw_data
                                      ->> 'out_of_game',
                                  'false'
                              )
                          ) <> 'true'

                          AND NOT EXISTS (
                              SELECT 1
                              FROM player_import_rows previous_row
                              WHERE previous_row.batch_id =
                                    :previousBatchId
                                AND previous_row.valid = TRUE
                                AND previous_row.external_id =
                                    current_row.external_id
                          )

                          AND NOT EXISTS (
                              SELECT 1
                              FROM foreign_player_scouting_reports report
                              WHERE report.player_id =
                                    player.id
                                AND report.season_id =
                                    season.id
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
                                          :club AS VARCHAR
                                      )
                                  )
                              OR LOWER(club.short_name) =
                                  LOWER(
                                      CAST(
                                          :club AS VARCHAR
                                      )
                                  )
                              OR LOWER(club.slug) =
                                  LOWER(
                                      CAST(
                                          :club AS VARCHAR
                                      )
                                  )
                          )

                        ORDER BY
                            player.id ASC
                        """)
                .param(
                        "currentBatchId",
                        current.id()
                )
                .param(
                        "previousBatchId",
                        previous.id()
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
                                new B70NewListoneArrivalResponse(
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
                                                "external_id"
                                        ),
                                        current.id(),
                                        previous.id(),
                                        current.observedAt(),
                                        "NEW_FANTACALCIO_LISTONE_ENTRY",
                                        resultSet.getBoolean(
                                                "official_transfer_event_present"
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

    private record BatchSnapshot(
            Long id,
            java.time.OffsetDateTime observedAt
    ) {
    }
}