package it.fantacalciointelligence.backend.evaluation;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.common.ConflictException;
import it.fantacalciointelligence.backend.common.ResourceNotFoundException;

@Service
public class PlayerEvaluationGenerationService {

    private final JdbcClient jdbcClient;

    public PlayerEvaluationGenerationService(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional
    public PlayerEvaluationGenerationResponse generate(
            String seasonCode
    ) {
        Long seasonId = findSeasonId(seasonCode);

        int eligiblePlayers =
                countEligiblePlayers(seasonId);

        if (eligiblePlayers == 0) {
            throw new ConflictException(
                    "Nessun giocatore ufficiale disponibile "
                            + "per la stagione "
                            + seasonCode
            );
        }

        deleteStaleEvaluations(seasonId);

        int generatedEvaluations =
                generateEvaluations(seasonId);

        return new PlayerEvaluationGenerationResponse(
                seasonCode,
                eligiblePlayers,
                generatedEvaluations,
                "COMPLETED"
        );
    }

    private Long findSeasonId(String seasonCode) {
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

    private int countEligiblePlayers(Long seasonId) {
        return jdbcClient.sql("""
                SELECT COUNT(DISTINCT role.player_id)
                FROM player_roles role

                JOIN player_reconciliation_results reconciliation
                    ON reconciliation.player_id = role.player_id
                    AND reconciliation.season_id = role.season_id

                JOIN players player
                    ON player.id = role.player_id
                    AND player.active = TRUE

                JOIN player_registrations registration
                    ON registration.player_id = role.player_id
                    AND registration.season_id = role.season_id
                    AND registration.registration_status = 'ACTIVE'

                JOIN season_clubs season_club
                    ON season_club.season_id = role.season_id
                    AND season_club.club_id = registration.club_id

                WHERE role.season_id = :seasonId
                  AND role.source = 'FANTACALCIO'
                  AND role.fantasy_role IS NOT NULL
                """)
                .param("seasonId", seasonId)
                .query(Integer.class)
                .single();
    }

    private void deleteStaleEvaluations(Long seasonId) {
        jdbcClient.sql("""
                DELETE FROM player_evaluations evaluation
                WHERE evaluation.season_id = :seasonId
                  AND NOT EXISTS (
                      SELECT 1
                      FROM player_reconciliation_results reconciliation

                      JOIN player_registrations registration
                          ON registration.player_id =
                              reconciliation.player_id
                          AND registration.season_id =
                              reconciliation.season_id
                          AND registration.registration_status =
                              'ACTIVE'

                      JOIN season_clubs season_club
                          ON season_club.season_id = reconciliation.season_id
                          AND season_club.club_id = registration.club_id

                      WHERE reconciliation.player_id =
                              evaluation.player_id
                        AND reconciliation.season_id =
                              evaluation.season_id
                  )
                """)
                .param("seasonId", seasonId)
                .update();
    }

    private int generateEvaluations(Long seasonId) {
        return jdbcClient.sql("""
                WITH eligible AS (
                    SELECT DISTINCT
                        player.id AS player_id,
                        role.fantasy_role,

                        COALESCE(
                            market.quotation,
                            0
                        ) AS quotation,

                        COALESCE(
                            market.fantasy_market_value,
                            0
                        ) AS fantasy_market_value,

                        COALESCE(
                            reconciliation.sources_count,
                            1
                        ) AS sources_count

                    FROM players player

                    JOIN player_roles role
                        ON role.player_id = player.id
                        AND role.season_id = :seasonId
                        AND role.source = 'FANTACALCIO'

                    JOIN player_registrations registration
                        ON registration.player_id = player.id
                        AND registration.season_id = :seasonId
                        AND registration.registration_status =
                            'ACTIVE'

                    JOIN season_clubs season_club
                        ON season_club.season_id = :seasonId
                        AND season_club.club_id = registration.club_id

                    LEFT JOIN player_market_values market
                        ON market.player_id = player.id
                        AND market.season_id = :seasonId
                        AND market.source = 'FANTACALCIO'

                    JOIN player_reconciliation_results
                        reconciliation
                        ON reconciliation.player_id = player.id
                        AND reconciliation.season_id = :seasonId

                    WHERE player.active = TRUE
                ),

                normalized AS (
                    SELECT
                        eligible.*,

                        GREATEST(
                            MAX(fantasy_market_value)
                                OVER (
                                    PARTITION BY fantasy_role
                                ),
                            1
                        ) AS maximum_role_fmv,

                        GREATEST(
                            MAX(quotation)
                                OVER (
                                    PARTITION BY fantasy_role
                                ),
                            1
                        ) AS maximum_role_quotation

                    FROM eligible
                ),

                scored AS (
                    SELECT
                        normalized.*,

                        LEAST(
                            100,
                            GREATEST(
                                0,
                                CASE
                                    WHEN fantasy_market_value > 0
                                    THEN
                                        fantasy_market_value
                                        * 100.0
                                        / maximum_role_fmv

                                    WHEN quotation > 0
                                    THEN
                                        quotation
                                        * 100.0
                                        / maximum_role_quotation

                                    ELSE 0
                                END
                            )
                        ) AS market_score,

                        CASE
                            WHEN sources_count >= 3 THEN 90
                            WHEN sources_count = 2 THEN 75
                            ELSE 55
                        END AS source_score

                    FROM normalized
                ),

                projections AS (
                    SELECT
                        scored.*,

                        ROUND(
                            18
                            + market_score * 0.20
                        )::INTEGER AS projected_appearances,

                        LEAST(
                            ROUND(
                                18
                                + market_score * 0.20
                            )::INTEGER,

                            ROUND(
                                (
                                    18
                                    + market_score * 0.20
                                )
                                *
                                (
                                    0.30
                                    + market_score * 0.0065
                                )
                            )::INTEGER
                        ) AS projected_starts

                    FROM scored
                )

                INSERT INTO player_evaluations (
                    player_id,
                    season_id,
                    projected_fantasy_average,
                    projected_appearances,
                    projected_starts,
                    projected_goals,
                    projected_assists,
                    reliability_score,
                    potential_score,
                    risk_score,
                    value_score,
                    evaluation_summary
                )

                SELECT
                    player_id,
                    :seasonId,

                    ROUND(
                        CASE fantasy_role
                            WHEN 'P' THEN
                                4.80 + market_score * 0.012

                            WHEN 'D' THEN
                                5.20 + market_score * 0.014

                            WHEN 'C' THEN
                                5.30 + market_score * 0.018

                            WHEN 'A' THEN
                                5.40 + market_score * 0.022

                            ELSE
                                5.00 + market_score * 0.010
                        END,
                        2
                    ),

                    projected_appearances,
                    projected_starts,

                    CASE fantasy_role
                        WHEN 'P' THEN 0
                        WHEN 'D' THEN
                            ROUND(market_score / 25.0)::INTEGER
                        WHEN 'C' THEN
                            ROUND(market_score / 12.5)::INTEGER
                        WHEN 'A' THEN
                            ROUND(market_score / 8.3)::INTEGER
                        ELSE 0
                    END,

                    CASE fantasy_role
                        WHEN 'P' THEN 0
                        WHEN 'D' THEN
                            ROUND(market_score / 20.0)::INTEGER
                        WHEN 'C' THEN
                            ROUND(market_score / 14.0)::INTEGER
                        WHEN 'A' THEN
                            ROUND(market_score / 20.0)::INTEGER
                        ELSE 0
                    END,

                    ROUND(
                        LEAST(
                            100,
                            30
                            + market_score * 0.50
                            + source_score * 0.20
                        ),
                        2
                    ),

                    ROUND(
                        LEAST(
                            100,
                            35
                            + market_score * 0.55
                            + source_score * 0.10
                        ),
                        2
                    ),

                    ROUND(
                        LEAST(
                            100,
                            GREATEST(
                                0,
                                90
                                - market_score * 0.55
                                - source_score * 0.15
                            )
                        ),
                        2
                    ),

                    ROUND(
                        LEAST(
                            100,
                            30
                            + market_score * 0.55
                            + source_score * 0.15
                        ),
                        2
                    ),

                    CONCAT(
                        'Valutazione automatica iniziale basata su ',
                        'quotazione Fantacalcio, FVM, ruolo di listone ',
                        'e copertura delle fonti. Indice mercato ',
                        ROUND(market_score, 1),
                        '/100; fonti disponibili ',
                        sources_count,
                        '.'
                    )

                FROM projections

                ON CONFLICT (
                    player_id,
                    season_id
                )
                DO UPDATE SET
                    projected_fantasy_average =
                        EXCLUDED.projected_fantasy_average,

                    projected_appearances =
                        EXCLUDED.projected_appearances,

                    projected_starts =
                        EXCLUDED.projected_starts,

                    projected_goals =
                        EXCLUDED.projected_goals,

                    projected_assists =
                        EXCLUDED.projected_assists,

                    reliability_score =
                        EXCLUDED.reliability_score,

                    potential_score =
                        EXCLUDED.potential_score,

                    risk_score =
                        EXCLUDED.risk_score,

                    value_score =
                        EXCLUDED.value_score,

                    evaluation_summary =
                        EXCLUDED.evaluation_summary,

                    updated_at =
                        CURRENT_TIMESTAMP
                """)
                .param("seasonId", seasonId)
                .update();
    }
}
