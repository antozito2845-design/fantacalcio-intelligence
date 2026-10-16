package it.fantacalciointelligence.backend.squad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.common.ConflictException;
import it.fantacalciointelligence.backend.common.ResourceNotFoundException;

@SpringBootTest
@Transactional
class SquadServiceTest {

    @Autowired
    private SquadService squadService;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void shouldFindSquadsForSeason() {
        List<SquadSummaryResponse> squads =
                squadService.findSquads(
                        "2026/2027"
                );

        assertThat(squads)
                .isNotEmpty();

        SquadSummaryResponse squad =
                squads.getFirst();

        assertThat(squad.id())
                .isNotNull();

        assertThat(squad.season())
                .isEqualTo("2026/2027");

        assertThat(squad.maximumPlayers())
                .isEqualTo(25);

        assertThat(squad.totalPlayers())
                .isLessThanOrEqualTo(
                        squad.maximumPlayers()
                );

        assertThat(squad.remainingBudget())
                .isEqualTo(
                        squad.initialBudget()
                                - squad.spentBudget()
                );
    }

    @Test
    void shouldFindSpecificSquad() {
        Long squadId =
                findActiveSquadId();

        SquadResponse response =
                squadService.findSquad(
                        squadId
                );

        assertThat(response.id())
                .isEqualTo(squadId);

        assertThat(response.season())
                .isEqualTo("2026/2027");

        assertThat(response.totalPlayers())
                .isEqualTo(
                        response.players().size()
                );

        assertThat(response.maximumPlayers())
                .isEqualTo(25);
    }

    @Test
    void shouldRejectUnknownSquad() {
        assertThatThrownBy(() ->
                squadService.findSquad(
                        999999L
                )
        )
                .isInstanceOf(
                        ResourceNotFoundException.class
                )
                .hasMessageContaining(
                        "Rosa non trovata"
                );
    }

    @Test
    void shouldRejectDuplicatePlayer() {
        Long squadId =
                findActiveSquadId();

        Long existingPlayerId =
                jdbcClient.sql("""
                        SELECT player_id
                        FROM fantasy_squad_players
                        WHERE squad_id = :squadId
                        ORDER BY id
                        LIMIT 1
                        """)
                        .param(
                                "squadId",
                                squadId
                        )
                        .query(Long.class)
                        .single();

        assertThatThrownBy(() ->
                squadService.addPlayer(
                        squadId,
                        new AddSquadPlayerRequest(
                                existingPlayerId,
                                1
                        )
                )
        )
                .isInstanceOf(
                        ConflictException.class
                )
                .hasMessage(
                        "Giocatore già presente nella rosa"
                );
    }

    @Test
    void shouldRejectPlayerRemovalWhenPlayerIsMissing() {
        Long squadId =
                findActiveSquadId();

        assertThatThrownBy(() ->
                squadService.removePlayer(
                        squadId,
                        999999L
                )
        )
                .isInstanceOf(
                        ResourceNotFoundException.class
                )
                .hasMessage(
                        "Giocatore non presente nella rosa"
                );
    }

    @Test
    void shouldRejectUnknownPlayerOnAdd() {
        assertThatThrownBy(() -> squadService.addPlayer(
                findActiveSquadId(),
                new AddSquadPlayerRequest(999999999L, 1)
        )).isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Giocatore non disponibile");
    }

    @Test
    void shouldRemoveAndReAddExistingCanonicalPlayer() {
        Long squadId = findActiveSquadId();
        Long playerId = makeExistingPlayerEligible(squadId);

        squadService.removePlayer(squadId, playerId);
        SquadResponse response = squadService.addPlayer(
                squadId,
                new AddSquadPlayerRequest(playerId, 1)
        );

        assertThat(response.players())
                .anyMatch(player -> player.playerId().equals(playerId) && player.purchasePrice() == 1);
    }

    @Test
    void shouldRejectPurchaseWhenBudgetIsInsufficient() {
        Long squadId = findActiveSquadId();
        Long playerId = makeExistingPlayerEligible(squadId);
        squadService.removePlayer(squadId, playerId);

        assertThatThrownBy(() -> squadService.addPlayer(
                squadId,
                new AddSquadPlayerRequest(playerId, 1000)
        )).isInstanceOf(ConflictException.class);
    }

    @Test
    void shouldUpdatePurchasePriceForExistingPlayer() {
        Long squadId = findActiveSquadId();
        Long playerId = jdbcClient.sql("""
                SELECT player_id FROM fantasy_squad_players
                WHERE squad_id = :squadId ORDER BY id LIMIT 1
                """).param("squadId", squadId).query(Long.class).single();

        SquadResponse response = squadService.updatePlayerPrice(
                squadId,
                playerId,
                new UpdateSquadPlayerPriceRequest(1)
        );

        assertThat(response.players())
                .filteredOn(player -> player.playerId().equals(playerId))
                .singleElement()
                .extracting(SquadPlayerResponse::purchasePrice)
                .isEqualTo(1);
    }

    @Test
    void shouldListOnlyRequestedSeason() {
        List<SquadSummaryResponse> squads =
                squadService.findSquads(
                        "2024/2025"
                );

        assertThat(squads)
                .allMatch(squad ->
                        squad.season()
                                .equals("2024/2025")
                );
    }

    private Long findActiveSquadId() {
        return jdbcClient.sql("""
                SELECT id
                FROM fantasy_squads
                WHERE active = TRUE
                  AND EXISTS (
                      SELECT 1 FROM fantasy_squad_players player
                      WHERE player.squad_id = fantasy_squads.id
                  )
                ORDER BY id DESC
                LIMIT 1
                """)
                .query(Long.class)
                .single();
    }

    private Long makeExistingPlayerEligible(Long squadId) {
        Long playerId = jdbcClient.sql("""
                SELECT player_id FROM fantasy_squad_players
                WHERE squad_id = :squadId ORDER BY id LIMIT 1
                """).param("squadId", squadId).query(Long.class).single();
        jdbcClient.sql("""
                INSERT INTO player_roles(player_id, season_id, source, fantasy_role, primary_role)
                SELECT player_id, season_id, 'FANTACALCIO', fantasy_role, TRUE
                FROM fantasy_squad_players WHERE squad_id = :squadId AND player_id = :playerId
                """).param("squadId", squadId).param("playerId", playerId).update();
        jdbcClient.sql("""
                INSERT INTO player_reconciliation_results(
                    season_id, player_id, normalized_name, normalized_club_name, sources_count
                )
                SELECT squad_player.season_id, player.id, player.normalized_name, 'test-transaction', 1
                FROM fantasy_squad_players squad_player
                JOIN players player ON player.id = squad_player.player_id
                WHERE squad_player.squad_id = :squadId AND squad_player.player_id = :playerId
                """).param("squadId", squadId).param("playerId", playerId).update();
        return playerId;
    }
}
