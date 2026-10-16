package it.fantacalciointelligence.backend.calendarintelligence;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CalendarCandidateService {
    private final JdbcClient jdbcClient;
    public CalendarCandidateService(JdbcClient jdbcClient){this.jdbcClient=jdbcClient;}

    @Transactional(readOnly=true)
    public List<CalendarPlayerCandidate> loadActive(String season,String fantasyRole){
        if(!List.of("P","D","C","A").contains(fantasyRole))throw new IllegalArgumentException("Invalid fantasy role");
        return jdbcClient.sql("""
                SELECT player.id player_id, player.display_name, registration.club_id,
                       club.name club_name, role.fantasy_role
                FROM players player
                JOIN player_registrations registration ON registration.player_id=player.id
                JOIN seasons season ON season.id=registration.season_id
                JOIN clubs club ON club.id=registration.club_id
                JOIN season_clubs season_club ON season_club.season_id=season.id
                    AND season_club.club_id=club.id
                JOIN player_roles role ON role.player_id=player.id AND role.season_id=season.id
                    AND role.source='FANTACALCIO'
                WHERE season.code=:season AND role.fantasy_role=:role
                  AND player.active=TRUE
                  AND registration.registration_status='ACTIVE'
                ORDER BY player.id
                """).param("season",season).param("role",fantasyRole)
                .query((rs,n)->new CalendarPlayerCandidate(rs.getLong("player_id"),rs.getString("display_name"),
                        rs.getLong("club_id"),rs.getString("club_name"),rs.getString("fantasy_role"))).list();
    }

    public record CalendarPlayerCandidate(Long playerId,String playerName,Long clubId,String clubName,String fantasyRole){}
}
