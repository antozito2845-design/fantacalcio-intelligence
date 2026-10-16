package it.fantacalciointelligence.backend.auctionstate;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;

final class B59TestFixture {
    private B59TestFixture() { }
    static List<Long> addFantacalcioRoles(JdbcClient jdbc){
        List<Long> missing=jdbc.sql("""
                SELECT DISTINCT internal.player_id FROM player_roles internal
                JOIN player_registrations registration ON registration.player_id=internal.player_id
                     AND registration.season_id=internal.season_id AND registration.registration_status='ACTIVE'
                JOIN season_clubs season_club ON season_club.season_id=internal.season_id
                     AND season_club.club_id=registration.club_id
                JOIN players player ON player.id=internal.player_id AND player.active=TRUE
                WHERE internal.source='INTERNAL'
                  AND NOT EXISTS(SELECT 1 FROM player_roles f WHERE f.player_id=internal.player_id
                      AND f.season_id=internal.season_id AND f.source='FANTACALCIO')
                ORDER BY internal.player_id
                """).query(Long.class).list();
        if (missing.isEmpty()) return missing;
        jdbc.sql("""
                INSERT INTO player_roles(player_id,season_id,source,fantasy_role,tactical_role,primary_role)
                SELECT internal.player_id,internal.season_id,'FANTACALCIO',internal.fantasy_role,
                       internal.tactical_role,internal.primary_role
                FROM player_roles internal WHERE internal.source='INTERNAL'
                  AND internal.player_id IN (:ids)
                ON CONFLICT(player_id,season_id,source) DO NOTHING
                """).param("ids",missing).update();
        return missing;
    }
    static Long createSquad(JdbcClient jdbc){return jdbc.sql("""
            INSERT INTO fantasy_squads(
                league_configuration_id,name,initial_budget,active,owner_username
            )
            SELECT id,'B59 sandbox '||CURRENT_TIMESTAMP,total_budget,FALSE,'b59-test-owner'
            FROM league_configurations WHERE active=TRUE RETURNING id
            """).query(Long.class).single();}
}
