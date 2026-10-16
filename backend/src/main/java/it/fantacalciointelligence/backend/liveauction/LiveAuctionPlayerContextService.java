package it.fantacalciointelligence.backend.liveauction;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.common.BadRequestException;
import it.fantacalciointelligence.backend.common.ResourceNotFoundException;

@Service
public class LiveAuctionPlayerContextService {
    private final JdbcClient jdbcClient;

    public LiveAuctionPlayerContextService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional(readOnly = true)
    public PlayerContext load(Long playerId, String season) {
        boolean exists = jdbcClient.sql("SELECT EXISTS(SELECT 1 FROM players WHERE id=:id)")
                .param("id", playerId).query(Boolean.class).single();
        if (!exists) throw new ResourceNotFoundException("Giocatore non trovato");
        return jdbcClient.sql("""
                SELECT player.active player_active,
                       registration.registration_status='ACTIVE' registration_active,
                       season_club.club_id IS NOT NULL club_active, role.fantasy_role
                FROM players player
                LEFT JOIN seasons season ON season.code=:season
                LEFT JOIN player_registrations registration
                       ON registration.player_id=player.id AND registration.season_id=season.id
                LEFT JOIN clubs club ON club.id=registration.club_id
                LEFT JOIN season_clubs season_club ON season_club.season_id=season.id
                       AND season_club.club_id=registration.club_id
                LEFT JOIN player_roles role ON role.player_id=player.id AND role.season_id=season.id
                       AND role.source='FANTACALCIO'
                WHERE player.id=:id
                """).param("season", season).param("id", playerId)
                .query((rs, n) -> new PlayerContext(rs.getBoolean("player_active"),
                        rs.getBoolean("registration_active"), rs.getBoolean("club_active"),
                        rs.getString("fantasy_role"))).optional()
                .orElseThrow(() -> new BadRequestException("Stagione o registrazione non coerente"));
    }

    public record PlayerContext(boolean active, boolean registrationActive,
            boolean clubActive, String role) { }
}
