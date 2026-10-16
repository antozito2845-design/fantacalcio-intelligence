package it.fantacalciointelligence.backend.league;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeagueConfigurationService {

    private final JdbcClient jdbcClient;

    public LeagueConfigurationService(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional(readOnly = true)
    public LeagueConfigurationResponse findActive() {
        return jdbcClient.sql("""
                SELECT
                    league.id,
                    league.name,
                    season.code AS season,
                    league.teams_count,
                    league.total_budget,
                    league.goalkeepers_count,
                    league.defenders_count,
                    league.midfielders_count,
                    league.forwards_count,
                    league.max_substitutions,
                    league.defense_modifier,
                    league.captain_bonus,
                    league.vote_source,
                    league.active

                FROM league_configurations league

                JOIN seasons season
                    ON season.id = league.season_id

                WHERE league.active = TRUE

                ORDER BY league.id DESC
                LIMIT 1
                """)
                .query((rs, rowNumber) ->
                        new LeagueConfigurationResponse(
                                rs.getLong("id"),
                                rs.getString("name"),
                                rs.getString("season"),
                                rs.getInt("teams_count"),
                                rs.getInt("total_budget"),
                                rs.getInt("goalkeepers_count"),
                                rs.getInt("defenders_count"),
                                rs.getInt("midfielders_count"),
                                rs.getInt("forwards_count"),
                                rs.getInt("max_substitutions"),
                                rs.getBoolean("defense_modifier"),
                                rs.getBoolean("captain_bonus"),
                                rs.getString("vote_source"),
                                rs.getBoolean("active")
                        )
                )
                .optional()
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Configurazione lega non trovata"
                        )
                );
    }

    @Transactional
    public LeagueConfigurationResponse update(
            UpdateLeagueConfigurationRequest request
    ) {
        Long seasonId = jdbcClient.sql("""
                SELECT id
                FROM seasons
                WHERE code = :season
                """)
                .param("season", request.season())
                .query(Long.class)
                .optional()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Stagione non trovata"
                        )
                );

        Long configurationId = jdbcClient.sql("""
                SELECT id
                FROM league_configurations
                WHERE active = TRUE
                ORDER BY id DESC
                LIMIT 1
                """)
                .query(Long.class)
                .optional()
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Configurazione lega non trovata"
                        )
                );

        jdbcClient.sql("""
                UPDATE league_configurations
                SET
                    name = :name,
                    season_id = :seasonId,
                    teams_count = :teamsCount,
                    total_budget = :totalBudget,
                    goalkeepers_count = :goalkeepersCount,
                    defenders_count = :defendersCount,
                    midfielders_count = :midfieldersCount,
                    forwards_count = :forwardsCount,
                    max_substitutions = :maxSubstitutions,
                    defense_modifier = :defenseModifier,
                    captain_bonus = :captainBonus,
                    vote_source = :voteSource,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :configurationId
                """)
                .param("name", request.name())
                .param("seasonId", seasonId)
                .param("teamsCount", request.teamsCount())
                .param("totalBudget", request.totalBudget())
                .param(
                        "goalkeepersCount",
                        request.goalkeepersCount()
                )
                .param(
                        "defendersCount",
                        request.defendersCount()
                )
                .param(
                        "midfieldersCount",
                        request.midfieldersCount()
                )
                .param(
                        "forwardsCount",
                        request.forwardsCount()
                )
                .param(
                        "maxSubstitutions",
                        request.maxSubstitutions()
                )
                .param(
                        "defenseModifier",
                        request.defenseModifier()
                )
                .param(
                        "captainBonus",
                        request.captainBonus()
                )
                .param(
                        "voteSource",
                        request.voteSource()
                )
                .param(
                        "configurationId",
                        configurationId
                )
                .update();

        return findActive();
    }
}