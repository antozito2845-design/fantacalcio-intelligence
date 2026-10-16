package it.fantacalciointelligence.backend.auctionlive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import it.fantacalciointelligence.backend.auction.AuctionApiPriceService;
import it.fantacalciointelligence.backend.auction.B51TestMarket;
import it.fantacalciointelligence.backend.common.BadRequestException;
import it.fantacalciointelligence.backend.squad.SquadResponse;
import it.fantacalciointelligence.backend.squad.SquadService;

@SpringBootTest
@Transactional
class LiveAuctionStrategyServiceTest {

    @Autowired
    private LiveAuctionStrategyService service;

    @Autowired
    private SquadService squadService;

    @Autowired
    private JdbcClient jdbcClient;

    @MockitoBean
    private AuctionApiPriceService auctionPriceService;

    private Long squadId;

    @BeforeEach
    void setUp() {
        when(auctionPriceService.calculateAll(
                anyString(), anyInt(), anyInt(), anyBoolean()
        )).thenAnswer(invocation -> B51TestMarket.pricedPlayers(
                jdbcClient,
                invocation.getArgument(0),
                invocation.getArgument(1),
                invocation.getArgument(2),
                invocation.getArgument(3)
        ));

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
    void shouldBuildStrategyForActiveSquad() {
        LiveAuctionStrategyResponse response =
                service.buildActiveStrategy();

        assertThat(response.squadId())
                .isEqualTo(squadId);

        assertThat(response.season())
                .isEqualTo("2026/2027");

        assertThat(response.roles())
                .hasSize(4);

        assertThat(response.remainingSlots())
                .isEqualTo(25);

        assertThat(response.globalMaximumBid())
                .isEqualTo(976);

        assertThat(response.squadComplete())
                .isFalse();
    }

    @Test
    void shouldBuildStrategyForSpecificSquad() {
        LiveAuctionStrategyResponse response =
                service.buildStrategy(squadId);

        assertThat(response.squadId())
                .isEqualTo(squadId);

        assertThat(response.initialBudget())
                .isEqualTo(1000);

        assertThat(response.spentBudget())
                .isZero();

        assertThat(response.remainingBudget())
                .isEqualTo(1000);
    }

    @Test
    void shouldExposeCorrectRoleSlots() {
        LiveAuctionStrategyResponse response =
                service.buildStrategy(squadId);

        assertThat(findRole(response, "P")
                .remainingSlots())
                .isEqualTo(3);

        assertThat(findRole(response, "D")
                .remainingSlots())
                .isEqualTo(8);

        assertThat(findRole(response, "C")
                .remainingSlots())
                .isEqualTo(8);

        assertThat(findRole(response, "A")
                .remainingSlots())
                .isEqualTo(6);
    }

    @Test
    void shouldAllocateEntireRemainingBudget() {
        LiveAuctionStrategyResponse response =
                service.buildStrategy(squadId);

        int allocated =
                response.roles()
                        .stream()
                        .mapToInt(
                                LiveAuctionRoleStrategyResponse
                                        ::allocatedRemainingBudget
                        )
                        .sum();

        assertThat(allocated)
                .isEqualTo(
                        response.remainingBudget()
                );
    }

    @Test
    void shouldRecalculateAfterPurchase() {
        PlayerData player =
                findPlayerNotInSquad();

        insertPurchase(
                player,
                120
        );

        LiveAuctionStrategyResponse response =
                service.buildStrategy(squadId);

        assertThat(response.spentBudget())
                .isEqualTo(120);

        assertThat(response.remainingBudget())
                .isEqualTo(880);

        assertThat(response.totalPlayers())
                .isEqualTo(1);

        assertThat(response.remainingSlots())
                .isEqualTo(24);

        assertThat(response.globalMaximumBid())
                .isEqualTo(857);
    }

    @Test
    void shouldExcludePurchasedPlayerFromTargets() {
        PlayerData player =
                findPlayerNotInSquad();

        insertPurchase(
                player,
                10
        );

        LiveAuctionStrategyResponse response =
                service.buildStrategy(squadId);

        assertThat(allTargets(response))
                .extracting(
                        LiveAuctionTargetResponse::playerId
                )
                .doesNotContain(
                        player.playerId()
                );
    }

    @Test
    void shouldMarkCompletedRole() {
        List<PlayerData> goalkeepers =
                findPlayersByRole(
                        "P",
                        3
                );

        assertThat(goalkeepers)
                .hasSize(3);

        for (PlayerData goalkeeper : goalkeepers) {
            insertPurchase(
                    goalkeeper,
                    1
            );
        }

        LiveAuctionStrategyResponse response =
                service.buildStrategy(squadId);

        LiveAuctionRoleStrategyResponse role =
                findRole(response, "P");

        assertThat(role.roleComplete())
                .isTrue();

        assertThat(role.remainingSlots())
                .isZero();

        assertThat(role.targets())
                .isEmpty();

        assertThat(role.maximumAffordableBid())
                .isZero();
    }

    @Test
    void shouldRespectLiveMaximumBid() {
        PlayerData player =
                findPlayerNotInSquad();

        insertPurchase(
                player,
                950
        );

        LiveAuctionStrategyResponse response =
                service.buildStrategy(squadId);

        assertThat(response.remainingBudget())
                .isEqualTo(50);

        assertThat(response.globalMaximumBid())
                .isEqualTo(27);

        assertThat(allTargets(response))
                .allSatisfy(target ->
                        assertThat(
                                target.liveMaximumBid()
                        )
                                .isLessThanOrEqualTo(
                                        response.globalMaximumBid()
                                )
                );
    }

    @Test
    void shouldRecalculateAfterPurchaseRemoval() {
        PlayerData player =
                findPlayerNotInSquad();

        insertPurchase(
                player,
                100
        );

        LiveAuctionStrategyResponse afterPurchase =
                service.buildStrategy(squadId);

        assertThat(afterPurchase.remainingBudget())
                .isEqualTo(900);

        jdbcClient.sql("""
                DELETE FROM fantasy_squad_players
                WHERE squad_id = :squadId
                  AND player_id = :playerId
                """)
                .param("squadId", squadId)
                .param(
                        "playerId",
                        player.playerId()
                )
                .update();

        LiveAuctionStrategyResponse afterRemoval =
                service.buildStrategy(squadId);

        assertThat(afterRemoval.remainingBudget())
                .isEqualTo(1000);

        assertThat(afterRemoval.totalPlayers())
                .isZero();

        assertThat(afterRemoval.remainingSlots())
                .isEqualTo(25);
    }

    @Test
    void shouldRejectInvalidSquadId() {
        assertThatThrownBy(() ->
                service.buildStrategy(0L)
        )
                .isInstanceOf(
                        BadRequestException.class
                )
                .hasMessageContaining(
                        "identificativo della rosa"
                );
    }

    private void prepareEligiblePlayers() {
        List<Map<String, Object>> players =
                jdbcClient.sql("""
                        SELECT DISTINCT
                            player.id AS player_id,
                            season.id AS season_id,
                            role.fantasy_role,
                            role.tactical_role

                        FROM players player

                        JOIN player_registrations registration
                            ON registration.player_id = player.id
                            AND registration.registration_status =
                                'ACTIVE'

                        JOIN seasons season
                            ON season.id = registration.season_id

                        JOIN player_roles role
                            ON role.player_id = player.id
                            AND role.season_id = season.id

                        WHERE player.active = TRUE
                          AND season.code = '2026/2027'

                        ORDER BY player.id
                        """)
                        .query()
                        .listOfRows();

        assertThat(players)
                .isNotEmpty();

        for (Map<String, Object> player : players) {
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

    private PlayerData findPlayerNotInSquad() {
        return jdbcClient.sql("""
                SELECT DISTINCT
                    player.id AS player_id,
                    season.id AS season_id,
                    role.fantasy_role

                FROM players player

                JOIN player_registrations registration
                    ON registration.player_id = player.id
                    AND registration.registration_status =
                        'ACTIVE'

                JOIN seasons season
                    ON season.id = registration.season_id

                JOIN player_roles role
                    ON role.player_id = player.id
                    AND role.season_id = season.id
                    AND role.source = 'FANTACALCIO'

                WHERE player.active = TRUE
                  AND season.code = '2026/2027'

                  AND NOT EXISTS (
                      SELECT 1
                      FROM fantasy_squad_players squad_player
                      WHERE squad_player.squad_id = :squadId
                        AND squad_player.player_id = player.id
                  )

                ORDER BY player.id
                LIMIT 1
                """)
                .param("squadId", squadId)
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
                .single();
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
                    ON registration.player_id = player.id
                    AND registration.registration_status =
                        'ACTIVE'

                JOIN seasons season
                    ON season.id = registration.season_id

                JOIN player_roles player_role
                    ON player_role.player_id = player.id
                    AND player_role.season_id = season.id
                    AND player_role.source = 'FANTACALCIO'

                WHERE player.active = TRUE
                  AND season.code = '2026/2027'
                  AND player_role.fantasy_role = :role

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

    private LiveAuctionRoleStrategyResponse findRole(
            LiveAuctionStrategyResponse response,
            String role
    ) {
        return response.roles()
                .stream()
                .filter(item ->
                        role.equals(item.role())
                )
                .findFirst()
                .orElseThrow();
    }

    private List<LiveAuctionTargetResponse> allTargets(
            LiveAuctionStrategyResponse response
    ) {
        return response.roles()
                .stream()
                .flatMap(role ->
                        role.targets().stream()
                )
                .toList();
    }

    private record PlayerData(
            Long playerId,
            Long seasonId,
            String role
    ) {
    }
}
