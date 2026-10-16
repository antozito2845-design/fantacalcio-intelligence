package it.fantacalciointelligence.backend.fixture.strength;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.common.ConflictException;
import it.fantacalciointelligence.backend.common.ResourceNotFoundException;

@Service
public class ClubStrengthGenerationService {

    private final JdbcClient jdbcClient;

    public ClubStrengthGenerationService(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional
    public ClubStrengthGenerationResponse generate(
            String seasonCode
    ) {
        Long seasonId = findSeasonId(seasonCode);

        int eligiblePlayers =
                countEligiblePlayers(seasonId);

        if (eligiblePlayers == 0) {
            throw new ConflictException(
                    "Nessuna valutazione giocatore disponibile "
                            + "per generare i rating della stagione "
                            + seasonCode
            );
        }

        int eligibleClubs =
                countEligibleClubs(seasonId);

        int generatedRatings =
                generateRatings(seasonId);

        return new ClubStrengthGenerationResponse(
                seasonCode,
                eligiblePlayers,
                eligibleClubs,
                generatedRatings,
                "COMPLETED"
        );
    }

    private Long findSeasonId(
            String seasonCode
    ) {
        return jdbcClient.sql("""
                SELECT id
                FROM seasons
                WHERE code = :seasonCode
                """)
                .param("seasonCode", seasonCode)
                .query(Long.class)
                .optional()
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Stagione non trovata: "
                                        + seasonCode
                        )
                );
    }

    private int countEligiblePlayers(
            Long seasonId
    ) {
        return jdbcClient.sql("""
                SELECT COUNT(DISTINCT evaluation.player_id)

                FROM player_evaluations evaluation

                JOIN player_registrations registration
                    ON registration.player_id =
                        evaluation.player_id
                    AND registration.season_id =
                        evaluation.season_id

                JOIN season_clubs season_club
                    ON season_club.season_id = evaluation.season_id
                    AND season_club.club_id = registration.club_id

                JOIN player_roles role
                    ON role.player_id =
                        evaluation.player_id
                    AND role.season_id =
                        evaluation.season_id
                    AND role.source = 'FANTACALCIO'

                JOIN player_reconciliation_results reconciliation
                    ON reconciliation.player_id =
                        evaluation.player_id
                    AND reconciliation.season_id =
                        evaluation.season_id

                WHERE evaluation.season_id = :seasonId
                  AND role.fantasy_role IS NOT NULL
                """)
                .param("seasonId", seasonId)
                .query(Integer.class)
                .single();
    }

    private int countEligibleClubs(
            Long seasonId
    ) {
        return jdbcClient.sql("""
                SELECT COUNT(DISTINCT registration.club_id)

                FROM player_evaluations evaluation

                JOIN player_registrations registration
                    ON registration.player_id =
                        evaluation.player_id
                    AND registration.season_id =
                        evaluation.season_id

                JOIN season_clubs season_club
                    ON season_club.season_id = evaluation.season_id
                    AND season_club.club_id = registration.club_id

                JOIN player_roles role
                    ON role.player_id =
                        evaluation.player_id
                    AND role.season_id =
                        evaluation.season_id
                    AND role.source = 'FANTACALCIO'

                JOIN player_reconciliation_results reconciliation
                    ON reconciliation.player_id =
                        evaluation.player_id
                    AND reconciliation.season_id =
                        evaluation.season_id

                WHERE evaluation.season_id = :seasonId
                  AND role.fantasy_role IS NOT NULL
                """)
                .param("seasonId", seasonId)
                .query(Integer.class)
                .single();
    }

    private int generateRatings(
            Long seasonId
    ) {
        return jdbcClient.sql("""
                WITH eligible_players AS (
                    SELECT DISTINCT
                        registration.club_id,
                        evaluation.player_id,
                        role.fantasy_role,

                        COALESCE(
                            evaluation.reliability_score,
                            50
                        ) AS reliability_score,

                        COALESCE(
                            evaluation.potential_score,
                            50
                        ) AS potential_score,

                        COALESCE(
                            evaluation.risk_score,
                            50
                        ) AS risk_score,

                        COALESCE(
                            evaluation.value_score,
                            50
                        ) AS value_score,

                        COALESCE(
                            evaluation.projected_goals,
                            0
                        ) AS projected_goals,

                        COALESCE(
                            evaluation.projected_assists,
                            0
                        ) AS projected_assists,

                        COALESCE(
                            evaluation.projected_starts,
                            0
                        ) AS projected_starts

                    FROM player_evaluations evaluation

                    JOIN player_registrations registration
                        ON registration.player_id =
                            evaluation.player_id
                        AND registration.season_id =
                            evaluation.season_id

                    JOIN season_clubs season_club
                        ON season_club.season_id = evaluation.season_id
                        AND season_club.club_id = registration.club_id

                    JOIN player_roles role
                        ON role.player_id =
                            evaluation.player_id
                        AND role.season_id =
                            evaluation.season_id
                        AND role.source = 'FANTACALCIO'

                    JOIN player_reconciliation_results reconciliation
                        ON reconciliation.player_id =
                            evaluation.player_id
                        AND reconciliation.season_id =
                            evaluation.season_id

                    WHERE evaluation.season_id = :seasonId
                      AND role.fantasy_role IS NOT NULL
                ),

                player_scores AS (
                    SELECT
                        eligible_players.*,

                        LEAST(
                            100,
                            GREATEST(
                                0,
                                reliability_score * 0.35
                                + potential_score * 0.25
                                + value_score * 0.20
                                + (
                                    100 - risk_score
                                ) * 0.20
                            )
                        ) AS player_quality,

                        CASE fantasy_role
                            WHEN 'A' THEN 1.00
                            WHEN 'C' THEN 0.65
                            WHEN 'D' THEN 0.20
                            WHEN 'P' THEN 0.05
                            ELSE 0.10
                        END AS attacking_weight,

                        CASE fantasy_role
                            WHEN 'P' THEN 1.00
                            WHEN 'D' THEN 0.85
                            WHEN 'C' THEN 0.35
                            WHEN 'A' THEN 0.10
                            ELSE 0.10
                        END AS defensive_weight

                    FROM eligible_players
                ),

                ranked_players AS (
                    SELECT
                        player_scores.*,

                        ROW_NUMBER() OVER (
                            PARTITION BY club_id
                            ORDER BY
                                player_quality DESC,
                                projected_starts DESC,
                                player_id
                        ) AS club_position

                    FROM player_scores
                ),

                club_scores AS (
                    SELECT
                        club_id,

                        COUNT(*) AS players_count,

                        AVG(player_quality)
                            FILTER (
                                WHERE club_position <= 18
                            ) AS overall_score,

                        SUM(
                            (
                                player_quality
                                + LEAST(
                                    projected_goals * 1.50
                                    + projected_assists * 0.80,
                                    18
                                )
                            )
                            * attacking_weight
                        )
                        FILTER (
                            WHERE club_position <= 18
                        )
                        /
                        NULLIF(
                            SUM(attacking_weight)
                            FILTER (
                                WHERE club_position <= 18
                            ),
                            0
                        ) AS attacking_score,

                        SUM(
                            (
                                player_quality
                                + CASE fantasy_role
                                    WHEN 'P' THEN 5
                                    WHEN 'D' THEN 3
                                    ELSE 0
                                  END
                            )
                            * defensive_weight
                        )
                        FILTER (
                            WHERE club_position <= 18
                        )
                        /
                        NULLIF(
                            SUM(defensive_weight)
                            FILTER (
                                WHERE club_position <= 18
                            ),
                            0
                        ) AS defensive_score

                    FROM ranked_players

                    GROUP BY club_id
                ),

                normalized_scores AS (
                    SELECT
                        club.id AS club_id,

                        COALESCE(
                            club_scores.overall_score,
                            50
                        ) AS overall_score,

                        COALESCE(
                            club_scores.attacking_score,
                            club_scores.overall_score,
                            50
                        ) AS attacking_score,

                        COALESCE(
                            club_scores.defensive_score,
                            club_scores.overall_score,
                            50
                        ) AS defensive_score,

                        COALESCE(
                            club_scores.players_count,
                            0
                        ) AS players_count

                    FROM season_clubs season_club

                    JOIN clubs club
                        ON club.id = season_club.club_id

                    LEFT JOIN club_scores
                        ON club_scores.club_id = club.id

                    WHERE season_club.season_id = :seasonId
                ),

                final_scores AS (
                    SELECT
                        club_id,

                        LEAST(
                            100,
                            GREATEST(
                                35,
                                ROUND(
                                    overall_score
                                    + LEAST(
                                        players_count,
                                        20
                                    ) * 0.35
                                )
                            )
                        )::INTEGER AS overall_rating,

                        LEAST(
                            100,
                            GREATEST(
                                35,
                                ROUND(attacking_score)
                            )
                        )::INTEGER AS attacking_rating,

                        LEAST(
                            100,
                            GREATEST(
                                35,
                                ROUND(defensive_score)
                            )
                        )::INTEGER AS defensive_rating

                    FROM normalized_scores
                )

                INSERT INTO club_strength_ratings (
                    club_id,
                    season_id,
                    overall_rating,
                    attacking_rating,
                    defensive_rating,
                    home_rating,
                    away_rating
                )

                SELECT
                    club_id,
                    :seasonId,
                    overall_rating,
                    attacking_rating,
                    defensive_rating,

                    LEAST(
                        100,
                        GREATEST(
                            35,
                            ROUND(
                                overall_rating * 0.55
                                + attacking_rating * 0.20
                                + defensive_rating * 0.25
                                + 4
                            )
                        )
                    )::INTEGER AS home_rating,

                    LEAST(
                        100,
                        GREATEST(
                            35,
                            ROUND(
                                overall_rating * 0.55
                                + attacking_rating * 0.20
                                + defensive_rating * 0.25
                                - 4
                            )
                        )
                    )::INTEGER AS away_rating

                FROM final_scores

                ON CONFLICT (
                    club_id,
                    season_id
                )
                DO UPDATE SET
                    overall_rating =
                        EXCLUDED.overall_rating,

                    attacking_rating =
                        EXCLUDED.attacking_rating,

                    defensive_rating =
                        EXCLUDED.defensive_rating,

                    home_rating =
                        EXCLUDED.home_rating,

                    away_rating =
                        EXCLUDED.away_rating,

                    updated_at =
                        CURRENT_TIMESTAMP
                """)
                .param("seasonId", seasonId)
                .update();
    }
}
