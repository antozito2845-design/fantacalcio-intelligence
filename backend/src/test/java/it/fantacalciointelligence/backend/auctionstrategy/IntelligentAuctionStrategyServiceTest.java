package it.fantacalciointelligence.backend.auctionstrategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import it.fantacalciointelligence.backend.auction.AuctionApiPriceService;
import it.fantacalciointelligence.backend.auction.B51TestMarket;
import it.fantacalciointelligence.backend.common.BadRequestException;

@SpringBootTest
@Transactional
class IntelligentAuctionStrategyServiceTest {

    @Autowired
    private IntelligentAuctionStrategyService service;

    @Autowired
    private JdbcClient jdbcClient;

    @MockitoBean
    private AuctionApiPriceService auctionPriceService;

    @BeforeEach
    void configureB51Market() {
        when(auctionPriceService.calculateAll(
                anyString(), anyInt(), anyInt(), anyBoolean()
        )).thenAnswer(invocation -> B51TestMarket.pricedPlayers(
                jdbcClient,
                invocation.getArgument(0),
                invocation.getArgument(1),
                invocation.getArgument(2),
                invocation.getArgument(3)
        ));
    }

    @Test
    void shouldBuildCompleteAuctionStrategy() {
        prepareEligiblePlayers();

        AuctionStrategyResponse response =
                service.buildStrategy(
                        "2026/2027",
                        1000,
                        8,
                        true
                );

        assertThat(response.season())
                .isEqualTo("2026/2027");

        assertThat(response.totalBudget())
                .isEqualTo(1000);

        assertThat(response.teams())
                .isEqualTo(8);

        assertThat(response.defenseModifier())
                .isTrue();

        assertThat(response.totalRequiredPlayers())
                .isEqualTo(25);

        assertThat(response.roles())
                .hasSize(4);

        assertThat(response.roles())
                .extracting(
                        AuctionRoleStrategyResponse::role
                )
                .containsExactly(
                        "P",
                        "D",
                        "C",
                        "A"
                );

        assertThat(response.roles())
                .extracting(
                        AuctionRoleStrategyResponse
                                ::requiredSlots
                )
                .containsExactly(
                        3,
                        8,
                        8,
                        6
                );

        assertThat(response.strategySummary())
                .contains("spesa prevista")
                .contains("residuo previsto");
    }

    @Test
    void shouldUseModifierBudgetAllocation() {
        prepareEligiblePlayers();

        AuctionStrategyResponse response =
                service.buildStrategy(
                        "2026/2027",
                        1000,
                        8,
                        true
                );

        assertThat(findRole(response, "P")
                .budgetPercentage())
                .isEqualByComparingTo(
                        new BigDecimal("8.0")
                );

        assertThat(findRole(response, "D")
                .budgetPercentage())
                .isEqualByComparingTo(
                        new BigDecimal("22.0")
                );

        assertThat(findRole(response, "C")
                .budgetPercentage())
                .isEqualByComparingTo(
                        new BigDecimal("27.0")
                );

        assertThat(findRole(response, "A")
                .budgetPercentage())
                .isEqualByComparingTo(
                        new BigDecimal("43.0")
                );

        assertThat(findRole(response, "D")
                .allocatedBudget())
                .isEqualTo(220);
    }

    @Test
    void shouldUseDifferentAllocationWithoutModifier() {
        prepareEligiblePlayers();

        AuctionStrategyResponse response =
                service.buildStrategy(
                        "2026/2027",
                        1000,
                        8,
                        false
                );

        assertThat(findRole(response, "D")
                .budgetPercentage())
                .isEqualByComparingTo(
                        new BigDecimal("17.0")
                );

        assertThat(findRole(response, "C")
                .budgetPercentage())
                .isEqualByComparingTo(
                        new BigDecimal("30.0")
                );

        assertThat(findRole(response, "A")
                .budgetPercentage())
                .isEqualByComparingTo(
                        new BigDecimal("45.0")
                );

        assertThat(findRole(response, "D")
                .allocatedBudget())
                .isEqualTo(170);
    }

    @Test
    void shouldKeepPlannedSpendingInsideRoleBudget() {
        prepareEligiblePlayers();

        AuctionStrategyResponse response =
                service.buildStrategy(
                        "2026/2027",
                        1000,
                        8,
                        true
                );

        assertThat(response.roles())
                .allSatisfy(role -> {
                    assertThat(role.plannedSpending())
                            .isLessThanOrEqualTo(
                                    role.allocatedBudget()
                            );

                    assertThat(
                            role.remainingRoleBudget()
                    )
                            .isEqualTo(
                                    role.allocatedBudget()
                                            - role.plannedSpending()
                            );
                });
    }

    @Test
    void shouldKeepTotalStrategyInsideBudget() {
        prepareEligiblePlayers();

        AuctionStrategyResponse response =
                service.buildStrategy(
                        "2026/2027",
                        1000,
                        8,
                        true
                );

        assertThat(response.totalPlannedSpending())
                .isLessThanOrEqualTo(
                        response.totalBudget()
                );

        assertThat(response.expectedRemainingBudget())
                .isEqualTo(
                        response.totalBudget()
                                - response.totalPlannedSpending()
                );

        assertThat(response.withinBudget())
                .isTrue();
    }

    @Test
    void shouldExcludeUnavailablePlayersFromTargets() {
        List<Long> playerIds =
                prepareEligiblePlayers();

        Long playerId =
                playerIds.getFirst();

        insertUnavailableStatus(playerId);

        AuctionStrategyResponse response =
                service.buildStrategy(
                        "2026/2027",
                        1000,
                        8,
                        true
                );

        assertThat(allSelectedPlayers(response))
                .noneSatisfy(player ->
                        assertThat(player.playerId())
                                .isEqualTo(playerId)
                );
    }

    @Test
    void shouldCreateRoleWarningsWhenFewPlayersAreAvailable() {
        prepareEligiblePlayers();

        AuctionStrategyResponse response =
                service.buildStrategy(
                        "2026/2027",
                        1000,
                        8,
                        true
                );

        assertThat(response.roles())
                .allSatisfy(role ->
                        assertThat(role.warnings())
                                .isNotNull()
                );
    }

    @Test
    void shouldRejectBlankSeason() {
        assertThatThrownBy(() ->
                service.buildStrategy(
                        " ",
                        1000,
                        8,
                        true
                )
        )
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining(
                        "stagione è obbligatoria"
                );
    }

    @Test
    void shouldRejectInvalidBudget() {
        assertThatThrownBy(() ->
                service.buildStrategy(
                        "2026/2027",
                        100,
                        8,
                        true
                )
        )
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining(
                        "budget deve essere compreso"
                );
    }

    @Test
    void shouldRejectInvalidTeamsCount() {
        assertThatThrownBy(() ->
                service.buildStrategy(
                        "2026/2027",
                        1000,
                        5,
                        true
                )
        )
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining(
                        "squadre devono essere comprese"
                );
    }

    private List<Long> prepareEligiblePlayers() {
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
                        LIMIT 12
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
                    .param("fantasyRole", fantasyRole)
                    .param("tacticalRole", tacticalRole)
                    .update();
        }

        return players.stream()
                .map(player ->
                        ((Number) player.get("player_id"))
                                .longValue()
                )
                .toList();
    }

    private void insertUnavailableStatus(
            Long playerId
    ) {
        Long seasonId =
                jdbcClient.sql("""
                        SELECT id
                        FROM seasons
                        WHERE code = '2026/2027'
                        """)
                        .query(Long.class)
                        .single();

        jdbcClient.sql("""
                INSERT INTO player_availabilities (
                    player_id,
                    season_id,
                    matchday,
                    status,
                    starting_probability,
                    availability_probability,
                    injury_description,
                    source
                )
                VALUES (
                    :playerId,
                    :seasonId,
                    15,
                    'INJURED',
                    0,
                    0,
                    'Problema muscolare',
                    'MANUAL'
                )
                ON CONFLICT (
                    player_id,
                    season_id,
                    matchday
                )
                DO UPDATE SET
                    status = 'INJURED',
                    starting_probability = 0,
                    availability_probability = 0,
                    injury_description =
                        'Problema muscolare',
                    source = 'MANUAL',
                    updated_at = CURRENT_TIMESTAMP
                """)
                .param("playerId", playerId)
                .param("seasonId", seasonId)
                .update();
    }

    private AuctionRoleStrategyResponse findRole(
            AuctionStrategyResponse response,
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

    private List<AuctionStrategyPlayerResponse>
    allSelectedPlayers(
            AuctionStrategyResponse response
    ) {
        return response.roles()
                .stream()
                .flatMap(role ->
                        java.util.stream.Stream.of(
                                role.primaryTargets(),
                                role.valueAlternatives(),
                                role.highPotentialBets(),
                                role.playersToAvoid()
                        )
                )
                .flatMap(List::stream)
                .toList();
    }
}
