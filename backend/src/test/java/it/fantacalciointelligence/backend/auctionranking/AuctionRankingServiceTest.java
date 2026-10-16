package it.fantacalciointelligence.backend.auctionranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
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
import it.fantacalciointelligence.backend.auction.AuctionPriceResponse;
import it.fantacalciointelligence.backend.common.BadRequestException;

@SpringBootTest
@Transactional
class AuctionRankingServiceTest {

    @Autowired
    private IntelligentAuctionRankingService service;

    @Autowired
    private JdbcClient jdbcClient;

    @MockitoBean
    private AuctionApiPriceService auctionPriceService;

    @BeforeEach
    void configureB51Market() {
        when(auctionPriceService.calculateAll(
                anyString(), anyInt(), anyInt(), anyBoolean()
        )).thenAnswer(invocation -> {
            String season = invocation.getArgument(0);
            int budget = invocation.getArgument(1);
            int teams = invocation.getArgument(2);
            boolean modifier = invocation.getArgument(3);

            return jdbcClient.sql("""
                    SELECT DISTINCT
                        player.id,
                        player.display_name,
                        club.name AS club_name,
                        role.fantasy_role
                    FROM players player
                    JOIN player_registrations registration
                      ON registration.player_id = player.id
                     AND registration.registration_status = 'ACTIVE'
                    JOIN seasons season
                      ON season.id = registration.season_id
                    JOIN clubs club
                      ON club.id = registration.club_id
                    JOIN player_roles role
                      ON role.player_id = player.id
                     AND role.season_id = season.id
                     AND role.source = 'FANTACALCIO'
                    WHERE player.active = TRUE
                      AND season.code = :season
                    ORDER BY player.id
                    """)
                    .param("season", season)
                    .query()
                    .listOfRows()
                    .stream()
                    .map(row -> {
                        long id = ((Number) row.get("id")).longValue();
                        int recommended = (int) (id % 40) + 1;
                        BigDecimal score = BigDecimal.valueOf(100 - id % 50);

                        return new AuctionPriceResponse(
                                id,
                                row.get("display_name").toString(),
                                row.get("club_name").toString(),
                                row.get("fantasy_role").toString(),
                                teams,
                                budget,
                                modifier,
                                Math.max(1, recommended - 2),
                                recommended,
                                recommended + 2,
                                BigDecimal.valueOf(recommended)
                                        .multiply(BigDecimal.valueOf(100))
                                        .divide(BigDecimal.valueOf(budget)),
                                score,
                                "PRICED",
                                "B51 test price"
                        );
                    })
                    .toList();
        });
    }

    @Test
    void shouldBuildRankingOrderedByScore() {
        prepareEligiblePlayers();

        AuctionRankingResponse response =
                service.findRanking(
                        "2026/2027",
                        1000,
                        8,
                        true,
                        null,
                        null,
                        null,
                        null,
                        false,
                        20
                );

        assertThat(response.season())
                .isEqualTo("2026/2027");

        assertThat(response.budget())
                .isEqualTo(1000);

        assertThat(response.teams())
                .isEqualTo(8);

        assertThat(response.players())
                .isNotEmpty();

        assertThat(response.totalPlayers())
                .isEqualTo(response.players().size());

        assertThat(response.players().getFirst().position())
                .isEqualTo(1);

        for (int index = 0;
                index < response.players().size();
                index++) {

            assertThat(
                    response.players().get(index).position()
            )
                    .isEqualTo(index + 1);
        }

        List<java.math.BigDecimal> scores =
                response.players()
                        .stream()
                        .map(
                                AuctionRankingItemResponse
                                        ::intelligenceScore
                        )
                        .toList();

        assertThat(scores)
                .isSortedAccordingTo(
                        java.util.Comparator.reverseOrder()
                );

        verify(auctionPriceService, times(1))
                .calculateAll("2026/2027", 1000, 8, true);
    }

    @Test
    void shouldExcludePlayersWithInsufficientB51Evidence() {
        prepareEligiblePlayers();

        when(auctionPriceService.calculateAll(
                "2026/2027", 1000, 8, true
        )).thenReturn(List.of());

        AuctionRankingResponse response =
                service.findRanking(
                        "2026/2027", 1000, 8, true,
                        null, null, null, null,
                        false, 20
                );

        assertThat(response.players()).isEmpty();
        assertThat(response.totalPlayers()).isZero();
    }

    @Test
    void shouldFilterByRole() {
        prepareEligiblePlayers();

        String role = findExistingRole();

        AuctionRankingResponse response =
                service.findRanking(
                        "2026/2027",
                        1000,
                        8,
                        true,
                        role,
                        null,
                        null,
                        null,
                        false,
                        20
                );

        assertThat(response.role())
                .isEqualTo(role);

        assertThat(response.players())
                .allSatisfy(player ->
                        assertThat(player.fantasyRole())
                                .isEqualTo(role)
                );
    }

    @Test
    void shouldFilterByClub() {
        prepareEligiblePlayers();

        String club = findExistingClub();

        AuctionRankingResponse response =
                service.findRanking(
                        "2026/2027",
                        1000,
                        8,
                        true,
                        null,
                        club,
                        null,
                        null,
                        false,
                        20
                );

        assertThat(response.club())
                .isEqualTo(club.toLowerCase());

        assertThat(response.players())
                .allSatisfy(player ->
                        assertThat(player.club())
                                .isEqualToIgnoringCase(club)
                );
    }

    @Test
    void shouldFilterByMaximumPrice() {
        prepareEligiblePlayers();

        AuctionRankingResponse response =
                service.findRanking(
                        "2026/2027",
                        1000,
                        8,
                        true,
                        null,
                        null,
                        50,
                        null,
                        false,
                        100
                );

        assertThat(response.maximumPrice())
                .isEqualTo(50);

        assertThat(response.players())
                .allSatisfy(player ->
                        assertThat(
                                player.recommendedPrice()
                        )
                                .isLessThanOrEqualTo(50)
                );
    }

    @Test
    void shouldExcludeUnavailablePlayers() {
        List<Long> players = prepareEligiblePlayers();

        Long playerId = players.getFirst();

        insertUnavailableStatus(playerId);

        AuctionRankingResponse response =
                service.findRanking(
                        "2026/2027",
                        1000,
                        8,
                        true,
                        null,
                        null,
                        null,
                        null,
                        true,
                        100
                );

        assertThat(response.players())
                .noneSatisfy(player ->
                        assertThat(player.playerId())
                                .isEqualTo(playerId)
                );
    }

    @Test
    void shouldRespectLimit() {
        prepareEligiblePlayers();

        AuctionRankingResponse response =
                service.findRanking(
                        "2026/2027",
                        1000,
                        8,
                        true,
                        null,
                        null,
                        null,
                        null,
                        false,
                        1
                );

        assertThat(response.players())
                .hasSizeLessThanOrEqualTo(1);

        assertThat(response.totalPlayers())
                .isEqualTo(response.players().size());
    }

    @Test
    void shouldRejectInvalidRole() {
        assertThatThrownBy(() ->
                service.findRanking(
                        "2026/2027",
                        1000,
                        8,
                        true,
                        "X",
                        null,
                        null,
                        null,
                        true,
                        50
                )
        )
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining(
                        "ruolo deve essere"
                );
    }

    @Test
    void shouldRejectMaximumPriceHigherThanBudget() {
        assertThatThrownBy(() ->
                service.findRanking(
                        "2026/2027",
                        500,
                        8,
                        true,
                        null,
                        null,
                        600,
                        null,
                        true,
                        50
                )
        )
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining(
                        "non può superare il budget"
                );
    }

    @Test
    void shouldRejectInvalidRecommendation() {
        assertThatThrownBy(() ->
                service.findRanking(
                        "2026/2027",
                        1000,
                        8,
                        true,
                        null,
                        null,
                        null,
                        "SCONOSCIUTA",
                        true,
                        50
                )
        )
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining(
                        "Raccomandazione non valida"
                );
    }

    @Test
    void shouldRejectInvalidLimit() {
        assertThatThrownBy(() ->
                service.findRanking(
                        "2026/2027",
                        1000,
                        8,
                        true,
                        null,
                        null,
                        null,
                        null,
                        true,
                        601
                )
        )
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining(
                        "limite deve essere compreso"
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
                        LIMIT 5
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
    private String findExistingRole() {
        return jdbcClient.sql("""
                SELECT role.fantasy_role

                FROM player_roles role

                JOIN seasons season
                    ON season.id = role.season_id

                WHERE season.code = '2026/2027'
                  AND role.source = 'FANTACALCIO'

                ORDER BY role.player_id
                LIMIT 1
                """)
                .query(String.class)
                .single();
    }

    private String findExistingClub() {
        return jdbcClient.sql("""
                SELECT club.name

                FROM players player

                JOIN player_registrations registration
                    ON registration.player_id = player.id
                    AND registration.registration_status =
                        'ACTIVE'

                JOIN seasons season
                    ON season.id = registration.season_id

                JOIN clubs club
                    ON club.id = registration.club_id

                JOIN player_roles role
                    ON role.player_id = player.id
                    AND role.season_id = season.id
                    AND role.source = 'FANTACALCIO'

                WHERE player.active = TRUE
                  AND season.code = '2026/2027'

                ORDER BY player.id
                LIMIT 1
                """)
                .query(String.class)
                .single();
    }

    private void insertUnavailableStatus(Long playerId) {
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
                    10,
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
}
