package it.fantacalciointelligence.backend.intelligence;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.common.ResourceNotFoundException;
import it.fantacalciointelligence.backend.intelligence.PlayerIntelligenceProfileCalculator.CalculatedProfile;
import it.fantacalciointelligence.backend.intelligence.PlayerIntelligenceProfileCalculator.SeasonData;

@Service
public class PlayerIntelligenceProfileService {

    private final JdbcClient jdbcClient;
    private final IntelligenceCatalogPolicy catalogPolicy;
    private final PlayerIntelligenceProfileCalculator calculator;

    public PlayerIntelligenceProfileService(JdbcClient jdbcClient,
            IntelligenceCatalogPolicy catalogPolicy,
            PlayerIntelligenceProfileCalculator calculator) {
        this.jdbcClient = jdbcClient;
        this.catalogPolicy = catalogPolicy;
        this.calculator = calculator;
    }

    @Transactional(readOnly = true)
    public PlayerIntelligenceProfileResponse find(Long playerId, String season) {
        SeasonRef seasonRef = findSeason(season);
        catalogPolicy.assertReadable(season, seasonRef.current());
        Map<String, Object> player = findOperationalPlayer(playerId, seasonRef.id());
        List<SeasonData> history = loadHistory(playerId, seasonRef.startYear());
        SeasonData current = history.stream().filter(item -> season.equals(item.season())).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Statistiche non disponibili per il giocatore e la stagione"));
        SeasonData previous = history.stream().filter(item -> !season.equals(item.season())).findFirst().orElse(null);
        CalculatedProfile result = calculator.calculate(current, previous);
        return new PlayerIntelligenceProfileResponse(playerId, player.get("display_name").toString(), season,
                player.get("fantasy_role").toString(), PlayerIntelligenceProfileCalculator.MODEL_VERSION,
                result.completeness(), result.confidence(), result.starter(), result.reliability(), result.bonus(),
                result.malus(), result.consistency(), result.trend());
    }

    private SeasonRef findSeason(String code) {
        if (code == null || code.isBlank()) throw new ResourceNotFoundException("Stagione non trovata");
        return jdbcClient.sql("SELECT id, start_year, current_season FROM seasons WHERE code = :code")
                .param("code", code.trim()).query((rs, row) -> new SeasonRef(rs.getLong("id"),
                        rs.getInt("start_year"), rs.getBoolean("current_season"))).optional()
                .orElseThrow(() -> new ResourceNotFoundException("Stagione non trovata: " + code));
    }

    private Map<String, Object> findOperationalPlayer(Long playerId, long seasonId) {
        return jdbcClient.sql("""
                SELECT player.display_name, role.fantasy_role
                FROM player_reconciliation_results result
                JOIN players player ON player.id = result.player_id AND player.active = TRUE
                JOIN player_registrations registration ON registration.player_id = player.id
                    AND registration.season_id = result.season_id AND registration.registration_status = 'ACTIVE'
                JOIN season_clubs season_club ON season_club.season_id = result.season_id
                    AND season_club.club_id = registration.club_id
                JOIN player_roles role ON role.player_id = player.id AND role.season_id = result.season_id
                    AND role.source = 'FANTACALCIO'
                WHERE result.player_id = :playerId AND result.season_id = :seasonId
                  AND result.sources_count >= 2
                """).param("playerId", playerId).param("seasonId", seasonId).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Giocatore non appartenente al catalogo operativo della stagione"));
    }

    List<SeasonData> loadHistory(Long playerId, int targetStartYear) {
        return jdbcClient.sql("""
                SELECT season.code, role.fantasy_role,
                    COALESCE(MAX(stats.appearances) FILTER (WHERE stats.source = 'FBREF'),
                             MAX(stats.appearances) FILTER (WHERE stats.source = 'FANTACALCIO'),
                             MAX(stats.appearances) FILTER (WHERE stats.source = 'GAZZETTA'),
                             MAX(stats.appearances) FILTER (WHERE stats.source = 'FANTAMARKT')) AS appearances,
                    COALESCE(MAX(stats.starts) FILTER (WHERE stats.source = 'FBREF'),
                             MAX(stats.starts) FILTER (WHERE stats.source = 'FANTACALCIO'),
                             MAX(stats.starts) FILTER (WHERE stats.source = 'GAZZETTA'),
                             MAX(stats.starts) FILTER (WHERE stats.source = 'FANTAMARKT')) AS starts,
                    COALESCE(MAX(stats.minutes_played) FILTER (WHERE stats.source = 'FBREF'),
                             MAX(stats.minutes_played) FILTER (WHERE stats.source = 'FANTACALCIO'),
                             MAX(stats.minutes_played) FILTER (WHERE stats.source = 'GAZZETTA'),
                             MAX(stats.minutes_played) FILTER (WHERE stats.source = 'FANTAMARKT')) AS minutes,
                    COALESCE(MAX(stats.goals) FILTER (WHERE stats.source = 'FBREF'),
                             MAX(stats.goals) FILTER (WHERE stats.source = 'FANTACALCIO'),
                             MAX(stats.goals) FILTER (WHERE stats.source = 'GAZZETTA'),
                             MAX(stats.goals) FILTER (WHERE stats.source = 'FANTAMARKT')) AS goals,
                    COALESCE(MAX(stats.assists) FILTER (WHERE stats.source = 'FBREF'),
                             MAX(stats.assists) FILTER (WHERE stats.source = 'FANTACALCIO'),
                             MAX(stats.assists) FILTER (WHERE stats.source = 'GAZZETTA'),
                             MAX(stats.assists) FILTER (WHERE stats.source = 'FANTAMARKT')) AS assists,
                    COALESCE(MAX(stats.yellow_cards) FILTER (WHERE stats.source = 'FBREF'),
                             MAX(stats.yellow_cards) FILTER (WHERE stats.source = 'FANTACALCIO'),
                             MAX(stats.yellow_cards) FILTER (WHERE stats.source = 'GAZZETTA'),
                             MAX(stats.yellow_cards) FILTER (WHERE stats.source = 'FANTAMARKT')) AS yellow_cards,
                    COALESCE(MAX(stats.red_cards) FILTER (WHERE stats.source = 'FBREF'),
                             MAX(stats.red_cards) FILTER (WHERE stats.source = 'FANTACALCIO'),
                             MAX(stats.red_cards) FILTER (WHERE stats.source = 'GAZZETTA'),
                             MAX(stats.red_cards) FILTER (WHERE stats.source = 'FANTAMARKT')) AS red_cards,
                    COALESCE(MAX(stats.penalties_missed) FILTER (WHERE stats.source = 'FBREF'),
                             MAX(stats.penalties_missed) FILTER (WHERE stats.source = 'FANTACALCIO'),
                             MAX(stats.penalties_missed) FILTER (WHERE stats.source = 'GAZZETTA'),
                             MAX(stats.penalties_missed) FILTER (WHERE stats.source = 'FANTAMARKT')) AS penalties_missed,
                    COALESCE(MAX(stats.average_rating) FILTER (WHERE stats.source = 'FANTACALCIO'),
                             MAX(stats.average_rating) FILTER (WHERE stats.source = 'GAZZETTA')) AS average_rating,
                    COALESCE(MAX(stats.fantasy_average) FILTER (WHERE stats.source = 'FANTACALCIO'),
                             MAX(stats.fantasy_average) FILTER (WHERE stats.source = 'GAZZETTA')) AS fantasy_average
                FROM player_season_statistics stats
                JOIN seasons season ON season.id = stats.season_id
                LEFT JOIN player_roles role ON role.player_id = stats.player_id
                    AND role.season_id = stats.season_id AND role.source = 'FANTACALCIO'
                WHERE stats.player_id = :playerId AND season.start_year <= :targetStartYear
                GROUP BY season.id, season.code, season.start_year, role.fantasy_role
                ORDER BY season.start_year DESC
                """).param("playerId", playerId).param("targetStartYear", targetStartYear)
                .query((rs, row) -> new SeasonData(rs.getString("code"), rs.getString("fantasy_role"),
                        rs.getObject("appearances", Integer.class), rs.getObject("starts", Integer.class),
                        rs.getObject("minutes", Integer.class), rs.getObject("goals", Integer.class),
                        rs.getObject("assists", Integer.class), rs.getObject("yellow_cards", Integer.class),
                        rs.getObject("red_cards", Integer.class), rs.getObject("penalties_missed", Integer.class),
                        rs.getBigDecimal("average_rating"), rs.getBigDecimal("fantasy_average"))).list();
    }

    private record SeasonRef(long id, int startYear, boolean current) {}
}
