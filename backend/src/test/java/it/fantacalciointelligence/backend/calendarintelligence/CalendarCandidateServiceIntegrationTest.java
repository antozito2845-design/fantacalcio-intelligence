package it.fantacalciointelligence.backend.calendarintelligence;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
class CalendarCandidateServiceIntegrationTest {
    private static final String PREFIX="B57 Calendar Candidate ";
    @Autowired CalendarCandidateService service;
    @Autowired JdbcClient jdbc;
    private Long activePlayer,inactivePlayer,clubId,season2025,season2026;

    @BeforeEach void setUp(){clean();clubId=jdbc.sql("SELECT id FROM clubs WHERE active=TRUE ORDER BY id LIMIT 1").query(Long.class).single();
        season2025=season("2025/2026");season2026=season("2026/2027");activePlayer=player("Active");inactivePlayer=player("Inactive");
        membership(season2025);membership(season2026);
        registration(activePlayer,season2026,"ACTIVE");registration(inactivePlayer,season2026,"INACTIVE");registration(inactivePlayer,season2025,"ACTIVE");
        role(activePlayer,season2026);role(inactivePlayer,season2026);role(inactivePlayer,season2025);}
    @AfterEach void tearDown(){clean();}
    @Test void currentUseCaseIncludesOnlyActiveRegistration(){
        var candidates=service.loadActive("2026/2027","P");
        assertThat(candidates).extracting(CalendarCandidateService.CalendarPlayerCandidate::playerId).contains(activePlayer).doesNotContain(inactivePlayer);
    }
    @Test void seasonsAreIsolatedAndHistoricalRegistrationIsPreserved(){
        assertThat(service.loadActive("2025/2026","P")).extracting(CalendarCandidateService.CalendarPlayerCandidate::playerId).contains(inactivePlayer);
        assertThat(jdbc.sql("SELECT registration_status FROM player_registrations WHERE player_id=:p AND season_id=:s").param("p",inactivePlayer).param("s",season2026).query(String.class).single()).isEqualTo("INACTIVE");
    }
    @Test void invalidFantasyRoleIsRejectedWithoutChangingRoles(){
        assertThat(org.assertj.core.api.Assertions.catchThrowable(()->service.loadActive("2026/2027","X"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.sql("SELECT fantasy_role FROM player_roles WHERE player_id=:p AND season_id=:s AND source='FANTACALCIO'").param("p",activePlayer).param("s",season2026).query(String.class).single()).isEqualTo("P");
    }
    @Test void playerAtClubOutsideRequestedSeasonIsExcluded(){
        jdbc.sql("DELETE FROM season_clubs WHERE season_id=:s AND club_id=:c")
                .param("s",season2026).param("c",clubId).update();
        assertThat(service.loadActive("2026/2027","P"))
                .extracting(CalendarCandidateService.CalendarPlayerCandidate::playerId)
                .doesNotContain(activePlayer);
        assertThat(service.loadActive("2025/2026","P"))
                .extracting(CalendarCandidateService.CalendarPlayerCandidate::playerId)
                .contains(inactivePlayer);
    }
    private Long player(String suffix){return jdbc.sql("INSERT INTO players(last_name,display_name,normalized_name,active) VALUES(:n,:n,:norm,TRUE) RETURNING id").param("n",PREFIX+suffix).param("norm",(PREFIX+suffix).toLowerCase()).query(Long.class).single();}
    private void registration(Long p,Long s,String status){jdbc.sql("INSERT INTO player_registrations(player_id,club_id,season_id,registration_status) VALUES(:p,:c,:s,:st)").param("p",p).param("c",clubId).param("s",s).param("st",status).update();}
    private void role(Long p,Long s){jdbc.sql("INSERT INTO player_roles(player_id,season_id,source,fantasy_role) VALUES(:p,:s,'FANTACALCIO','P')").param("p",p).param("s",s).update();}
    private void membership(Long s){jdbc.sql("INSERT INTO season_clubs(season_id,club_id) VALUES(:s,:c) ON CONFLICT DO NOTHING").param("s",s).param("c",clubId).update();}
    private Long season(String code){return jdbc.sql("SELECT id FROM seasons WHERE code=:c").param("c",code).query(Long.class).single();}
    private void clean(){jdbc.sql("DELETE FROM player_roles WHERE player_id IN(SELECT id FROM players WHERE display_name LIKE :p)").param("p",PREFIX+"%").update();jdbc.sql("DELETE FROM player_registrations WHERE player_id IN(SELECT id FROM players WHERE display_name LIKE :p)").param("p",PREFIX+"%").update();jdbc.sql("DELETE FROM players WHERE display_name LIKE :p").param("p",PREFIX+"%").update();}
}
