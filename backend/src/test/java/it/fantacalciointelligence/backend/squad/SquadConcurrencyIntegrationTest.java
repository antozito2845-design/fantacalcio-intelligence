package it.fantacalciointelligence.backend.squad;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
class SquadConcurrencyIntegrationTest {

    @Autowired SquadService service;
    @Autowired JdbcClient jdbc;
    Long squadId;
    List<Long> players;
    Long seasonId;
    Long clubId;
    String suffix;

    @BeforeEach
    void fixture() {
        cleanup();
        squadId = jdbc.sql("""
                INSERT INTO fantasy_squads(
                    league_configuration_id,name,initial_budget,active,owner_username
                ) SELECT id,'Concurrency squad',150,FALSE,'squad-concurrency'
                  FROM league_configurations WHERE active=TRUE RETURNING id
                """).query(Long.class).single();
        suffix = UUID.randomUUID().toString();
        seasonId = jdbc.sql("SELECT id FROM seasons WHERE current_season=TRUE")
                .query(Long.class).single();
        clubId = jdbc.sql("""
                INSERT INTO clubs(name,short_name,slug,active)
                VALUES(:name,'SCT',:slug,TRUE) RETURNING id
                """).param("name", "Squad concurrency " + suffix)
                .param("slug", "squad-concurrency-" + suffix).query(Long.class).single();
        seedPlayer("A"); seedPlayer("A");
        players = jdbc.sql("""
                SELECT DISTINCT player.id FROM players player
                JOIN player_roles role ON role.player_id=player.id AND role.source='FANTACALCIO'
                JOIN player_registrations registration ON registration.player_id=player.id
                     AND registration.season_id=role.season_id AND registration.registration_status='ACTIVE'
                JOIN player_reconciliation_results reconciliation ON reconciliation.player_id=player.id
                     AND reconciliation.season_id=role.season_id
                WHERE player.active=TRUE ORDER BY player.id LIMIT 2
                """).query(Long.class).list();
        assertThat(players).hasSize(2);
    }

    @AfterEach
    void removeFixture() { cleanup(); }

    private void cleanup() {
        jdbc.sql("DELETE FROM fantasy_squads WHERE owner_username='squad-concurrency'").update();
        jdbc.sql("DELETE FROM player_reconciliation_results r USING players p WHERE r.player_id=p.id AND p.normalized_name LIKE 'squad concurrency %'").update();
        jdbc.sql("DELETE FROM player_roles r USING players p WHERE r.player_id=p.id AND p.normalized_name LIKE 'squad concurrency %'").update();
        jdbc.sql("DELETE FROM player_registrations r USING players p WHERE r.player_id=p.id AND p.normalized_name LIKE 'squad concurrency %'").update();
        jdbc.sql("DELETE FROM players WHERE normalized_name LIKE 'squad concurrency %'").update();
        jdbc.sql("DELETE FROM clubs WHERE slug LIKE 'squad-concurrency-%'").update();
    }

    @Test
    void concurrentOverspendAllowsAtMostOnePurchase() throws Exception {
        race(new AddSquadPlayerRequest(players.get(0), 100),
                new AddSquadPlayerRequest(players.get(1), 100));
        assertInvariants();
        assertThat(countPlayers()).isEqualTo(1);
    }

    @Test
    void concurrentSamePlayerNeverDuplicates() throws Exception {
        race(new AddSquadPlayerRequest(players.get(0), 10),
                new AddSquadPlayerRequest(players.get(0), 10));
        assertInvariants();
        assertThat(countPlayers()).isEqualTo(1);
    }

    @Test
    void concurrentLastSlotAllowsAtMostOnePurchase() throws Exception {
        seedExisting(3, "P"); seedExisting(8, "D"); seedExisting(8, "C"); seedExisting(5, "A");
        Long first = seedPlayer("A"); Long second = seedPlayer("A");
        race(new AddSquadPlayerRequest(first, 1), new AddSquadPlayerRequest(second, 1));
        assertInvariants();
        assertThat(countPlayers()).isEqualTo(25);
    }

    @Test
    void concurrentRoleLimitAllowsAtMostOnePurchase() throws Exception {
        seedExisting(5, "A");
        Long first = seedPlayer("A"); Long second = seedPlayer("A");
        race(new AddSquadPlayerRequest(first, 1), new AddSquadPlayerRequest(second, 1));
        assertInvariants();
        assertThat(roleCount("A")).isEqualTo(6);
    }

    @Test
    void concurrentAddAndRemovePreserveAllInvariants() throws Exception {
        Long existing = seedPlayer("A");
        insertExisting(existing, "A");
        Long addition = seedPlayer("A");
        race(() -> service.removePlayer(squadId, existing),
                () -> service.addPlayer(squadId, new AddSquadPlayerRequest(addition, 1)));
        assertInvariants();
        assertThat(countPlayers()).isEqualTo(1);
    }

    private void race(AddSquadPlayerRequest firstRequest, AddSquadPlayerRequest secondRequest) throws Exception {
        race(() -> service.addPlayer(squadId, firstRequest), () -> service.addPlayer(squadId, secondRequest));
    }

    private void race(CheckedOperation firstOperation, CheckedOperation secondOperation) throws Exception {
        CountDownLatch ready = new CountDownLatch(2); CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> attempt(firstOperation, ready, start));
            var second = executor.submit(() -> attempt(secondOperation, ready, start));
            ready.await(); start.countDown(); first.get(); second.get();
        }
    }

    private Object attempt(CheckedOperation operation, CountDownLatch ready, CountDownLatch start) {
        try { ready.countDown(); start.await(); return operation.run(); }
        catch (Exception exception) { return exception; }
    }

    private void assertInvariants() {
        Integer spent = jdbc.sql("SELECT COALESCE(SUM(purchase_price),0) FROM fantasy_squad_players WHERE squad_id=:id")
                .param("id", squadId).query(Integer.class).single();
        assertThat(spent).isBetween(0, 150);
        assertThat(countPlayers()).isLessThanOrEqualTo(25);
        assertThat(roleCount("P")).isLessThanOrEqualTo(3);
        assertThat(roleCount("D")).isLessThanOrEqualTo(8);
        assertThat(roleCount("C")).isLessThanOrEqualTo(8);
        assertThat(roleCount("A")).isLessThanOrEqualTo(6);
        Integer duplicates = jdbc.sql("""
                SELECT COUNT(*) FROM (
                    SELECT player_id FROM fantasy_squad_players WHERE squad_id=:id
                    GROUP BY player_id HAVING COUNT(*)>1
                ) duplicate
                """).param("id", squadId).query(Integer.class).single();
        assertThat(duplicates).isZero();
    }

    private int countPlayers() {
        return jdbc.sql("SELECT COUNT(*) FROM fantasy_squad_players WHERE squad_id=:id")
                .param("id", squadId).query(Integer.class).single();
    }

    private int roleCount(String role) {
        return jdbc.sql("SELECT COUNT(*) FROM fantasy_squad_players WHERE squad_id=:id AND fantasy_role=:role")
                .param("id", squadId).param("role", role).query(Integer.class).single();
    }

    private void seedExisting(int count, String role) {
        for (int index = 0; index < count; index++) insertExisting(seedPlayer(role), role);
    }

    private void insertExisting(Long playerId, String role) {
        jdbc.sql("""
                INSERT INTO fantasy_squad_players(squad_id,player_id,season_id,fantasy_role,purchase_price)
                VALUES(:squad,:player,:season,:role,1)
                """).param("squad", squadId).param("player", playerId).param("season", seasonId)
                .param("role", role).update();
    }

    private Long seedPlayer(String role) {
        String normalizedName = "squad concurrency " + suffix + " " + UUID.randomUUID();
        Long playerId = jdbc.sql("""
                INSERT INTO players(last_name,display_name,normalized_name,active)
                VALUES('Concurrency player','Concurrency player',:normalized,TRUE) RETURNING id
                """).param("normalized", normalizedName).query(Long.class).single();
        jdbc.sql("INSERT INTO player_registrations(player_id,club_id,season_id,registration_status) VALUES(:player,:club,:season,'ACTIVE')")
                .param("player", playerId).param("club", clubId).param("season", seasonId).update();
        jdbc.sql("INSERT INTO player_roles(player_id,season_id,source,fantasy_role,primary_role) VALUES(:player,:season,'FANTACALCIO',:role,TRUE)")
                .param("player", playerId).param("season", seasonId).param("role", role).update();
        jdbc.sql("INSERT INTO player_reconciliation_results(season_id,player_id,normalized_name,normalized_club_name,sources_count) VALUES(:season,:player,:name,'squad concurrency',1)")
                .param("season", seasonId).param("player", playerId).param("name", normalizedName).update();
        return playerId;
    }

    @FunctionalInterface
    private interface CheckedOperation { Object run(); }
}
