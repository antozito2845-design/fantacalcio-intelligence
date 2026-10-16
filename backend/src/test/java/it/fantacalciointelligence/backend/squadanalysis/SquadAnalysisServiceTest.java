package it.fantacalciointelligence.backend.squadanalysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.common.BadRequestException;

@SpringBootTest
@Transactional
class SquadAnalysisServiceTest {

    @Autowired
    private SquadAnalysisService service;

    @Autowired
    private JdbcClient jdbcClient;

    private Long squadId;

    @BeforeEach
    void setUp() {
        squadId =
                jdbcClient.sql("""
                        SELECT id
                        FROM fantasy_squads
                        WHERE active = TRUE
                        ORDER BY id DESC
                        LIMIT 1
                        """)
                        .query(Long.class)
                        .single();

        jdbcClient.sql("""
                DELETE FROM fantasy_squad_players
                WHERE squad_id = :squadId
                """)
                .param("squadId", squadId)
                .update();

        prepareEligiblePlayers();
    }

    @Test
    void shouldAnalyzeEmptyActiveSquad() {
        SquadAnalysisResponse response =
                service.analyzeActiveSquad(
                        1,
                        5
                );

        assertThat(response.squadId())
                .isEqualTo(squadId);

        assertThat(response.totalPlayers())
                .isZero();

        assertThat(response.remainingSlots())
                .isEqualTo(25);

        assertThat(response.constraintsValid())
                .isTrue();

        assertThat(response.roles())
                .hasSize(4);

        assertThat(response.players())
                .isEmpty();

        assertThat(response.pairings())
                .isEmpty();
    }

    @Test
    void shouldExposeRoleConstraints() {
        SquadAnalysisResponse response =
                service.analyzeSquad(
                        squadId,
                        1,
                        5
                );

        SquadConstraintRoleResponse goalkeepers =
                findRole(
                        response,
                        "P"
                );

        SquadConstraintRoleResponse defenders =
                findRole(
                        response,
                        "D"
                );

        assertThat(goalkeepers.maximumPlayers())
                .isEqualTo(3);

        assertThat(goalkeepers.remainingSlots())
                .isEqualTo(3);

        assertThat(goalkeepers.coverageStatus())
                .isEqualTo("SCOPERTO");

        assertThat(defenders.maximumPlayers())
                .isEqualTo(8);

        assertThat(defenders.remainingSlots())
                .isEqualTo(8);
    }

    @Test
    void shouldAnalyzePurchasedPlayers() {
        PlayerData player =
                findPlayersByRole(
                        "D",
                        1
                ).getFirst();

        insertPurchase(
                player,
                25
        );

        SquadAnalysisResponse response =
                service.analyzeSquad(
                        squadId,
                        1,
                        5
                );

        assertThat(response.totalPlayers())
                .isEqualTo(1);

        assertThat(response.spentBudget())
                .isEqualTo(25);

        assertThat(response.remainingBudget())
                .isEqualTo(975);

        assertThat(response.remainingSlots())
                .isEqualTo(24);

        assertThat(response.players())
                .hasSize(1);

        assertThat(response.players()
                .getFirst()
                .playerId())
                .isEqualTo(
                        player.playerId()
                );
    }

    @Test
    void shouldBuildPairingForPlayersWithSameRole() {
        List<PlayerData> defenders =
                findPlayersByRole(
                        "D",
                        2
                );

        assertThat(defenders)
                .hasSize(2);

        insertPurchase(
                defenders.get(0),
                20
        );

        insertPurchase(
                defenders.get(1),
                30
        );

        SquadAnalysisResponse response =
                service.analyzeSquad(
                        squadId,
                        1,
                        5
                );

        assertThat(response.pairings())
                .isNotEmpty();

        SquadPairingResponse pairing =
                response.pairings()
                        .getFirst();

        assertThat(pairing.role())
                .isEqualTo("D");

        assertThat(pairing.combinedPurchasePrice())
                .isEqualTo(50);

        assertThat(pairing.firstPlayerId())
                .isNotEqualTo(
                        pairing.secondPlayerId()
                );

        assertThat(pairing.complementarityScore())
                .isBetween(
                        new java.math.BigDecimal("0.00"),
                        new java.math.BigDecimal("100.00")
                );
    }

    @Test
    void shouldNotPairPlayersWithDifferentRoles() {
        PlayerData goalkeeper =
                findPlayersByRole(
                        "P",
                        1
                ).getFirst();

        PlayerData defender =
                findPlayersByRole(
                        "D",
                        1
                ).getFirst();

        insertPurchase(
                goalkeeper,
                10
        );

        insertPurchase(
                defender,
                20
        );

        SquadAnalysisResponse response =
                service.analyzeSquad(
                        squadId,
                        1,
                        5
                );

        assertThat(response.pairings())
                .isEmpty();
    }

    @Test
    void shouldRecalculateAfterPurchase() {
        SquadAnalysisResponse before =
                service.analyzeSquad(
                        squadId,
                        1,
                        5
                );

        PlayerData player =
                findPlayersByRole(
                        "C",
                        1
                ).getFirst();

        insertPurchase(
                player,
                70
        );

        SquadAnalysisResponse after =
                service.analyzeSquad(
                        squadId,
                        1,
                        5
                );

        assertThat(before.totalPlayers())
                .isZero();

        assertThat(after.totalPlayers())
                .isEqualTo(1);

        assertThat(after.remainingBudget())
                .isEqualTo(930);

        assertThat(
                findRole(
                        after,
                        "C"
                ).currentPlayers()
        )
                .isEqualTo(1);

        assertThat(
                findRole(
                        after,
                        "C"
                ).remainingSlots()
        )
                .isEqualTo(7);
    }

    @Test
    void shouldRejectInvalidSquadId() {
        assertThatThrownBy(() ->
                service.analyzeSquad(
                        0L,
                        1,
                        5
                )
        )
                .isInstanceOf(
                        BadRequestException.class
                )
                .hasMessageContaining(
                        "identificativo della rosa"
                );
    }

    @Test
    void shouldRejectInvalidMatchdayRange() {
        assertThatThrownBy(() ->
                service.analyzeSquad(
                        squadId,
                        10,
                        5
                )
        )
                .isInstanceOf(
                        BadRequestException.class
                )
                .hasMessageContaining(
                        "intervallo delle giornate"
                );
    }

    @Test
    void shouldRejectAnalysisLongerThanFifteenMatchdays() {
        assertThatThrownBy(() ->
                service.analyzeSquad(
                        squadId,
                        1,
                        20
                )
        )
                .isInstanceOf(
                        BadRequestException.class
                )
                .hasMessageContaining(
                        "massimo 15 giornate"
                );
    }

    private void prepareEligiblePlayers() {
        List<java.util.Map<String, Object>> players =
                jdbcClient.sql("""
                        SELECT DISTINCT ON (player.id)
                            player.id AS player_id,
                            season.id AS season_id,
                            role.fantasy_role,
                            role.tactical_role

                        FROM players player

                        JOIN player_registrations registration
                            ON registration.player_id =
                                player.id
                            AND registration.registration_status =
                                'ACTIVE'

                        JOIN seasons season
                            ON season.id =
                                registration.season_id

                        JOIN player_roles role
                            ON role.player_id =
                                player.id
                            AND role.season_id =
                                season.id

                        WHERE player.active = TRUE
                          AND season.code = '2026/2027'

                        ORDER BY
                            player.id,
                            role.primary_role DESC,
                            role.id
                        """)
                        .query()
                        .listOfRows();

        assertThat(players)
                .isNotEmpty();

        for (java.util.Map<String, Object> player : players) {
            Long playerId =
                    ((Number) player.get("player_id"))
                            .longValue();

            Long seasonId =
                    ((Number) player.get("season_id"))
                            .longValue();

            String fantasyRole =
                    player.get("fantasy_role")
                            .toString();

            Object tacticalRole =
                    player.get("tactical_role");

            jdbcClient.sql("""
                    INSERT INTO player_roles (
                        player_id,
                        season_id,
                        source,
                        fantasy_role,
                        tactical_role,
                        primary_role,
                        valid_from
                    )
                    VALUES (
                        :playerId,
                        :seasonId,
                        'FANTACALCIO',
                        :fantasyRole,
                        :tacticalRole,
                        TRUE,
                        DATE '2026-07-01'
                    )

                    ON CONFLICT (
                        player_id,
                        season_id,
                        source
                    )
                    DO UPDATE SET
                        fantasy_role =
                            EXCLUDED.fantasy_role,

                        tactical_role =
                            EXCLUDED.tactical_role,

                        primary_role = TRUE,

                        valid_from =
                            EXCLUDED.valid_from
                    """)
                    .param("playerId", playerId)
                    .param("seasonId", seasonId)
                    .param(
                            "fantasyRole",
                            fantasyRole
                    )
                    .param(
                            "tacticalRole",
                            tacticalRole
                    )
                    .update();
        }
    }

    private List<PlayerData> findPlayersByRole(
            String role,
            int limit
    ) {
        return jdbcClient.sql("""
                SELECT DISTINCT
                    player.id AS player_id,
                    season.id AS season_id,
                    player_role.fantasy_role

                FROM players player

                JOIN player_registrations registration
                    ON registration.player_id =
                        player.id
                    AND registration.registration_status =
                        'ACTIVE'

                JOIN seasons season
                    ON season.id =
                        registration.season_id

                JOIN player_roles player_role
                    ON player_role.player_id =
                        player.id
                    AND player_role.season_id =
                        season.id
                    AND player_role.source =
                        'FANTACALCIO'

                WHERE player.active = TRUE
                  AND season.code = '2026/2027'
                  AND player_role.fantasy_role =
                        :role

                ORDER BY player.id
                LIMIT :limit
                """)
                .param("role", role)
                .param("limit", limit)
                .query((rs, rowNumber) ->
                        new PlayerData(
                                rs.getLong(
                                        "player_id"
                                ),
                                rs.getLong(
                                        "season_id"
                                ),
                                rs.getString(
                                        "fantasy_role"
                                )
                        )
                )
                .list();
    }

    private void insertPurchase(
            PlayerData player,
            int purchasePrice
    ) {
        jdbcClient.sql("""
                INSERT INTO fantasy_squad_players (
                    squad_id,
                    player_id,
                    season_id,
                    fantasy_role,
                    purchase_price
                )
                VALUES (
                    :squadId,
                    :playerId,
                    :seasonId,
                    :role,
                    :purchasePrice
                )
                """)
                .param("squadId", squadId)
                .param(
                        "playerId",
                        player.playerId()
                )
                .param(
                        "seasonId",
                        player.seasonId()
                )
                .param(
                        "role",
                        player.role()
                )
                .param(
                        "purchasePrice",
                        purchasePrice
                )
                .update();
    }

    private SquadConstraintRoleResponse findRole(
            SquadAnalysisResponse response,
            String role
    ) {
        return response.roles()
                .stream()
                .filter(item ->
                        role.equals(
                                item.role()
                        )
                )
                .findFirst()
                .orElseThrow();
    }

    private record PlayerData(
            Long playerId,
            Long seasonId,
            String role
    ) {
    }
}