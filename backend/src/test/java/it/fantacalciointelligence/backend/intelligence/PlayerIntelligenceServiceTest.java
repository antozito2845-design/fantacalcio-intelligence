package it.fantacalciointelligence.backend.intelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import it.fantacalciointelligence.backend.auction.AuctionApiPriceService;
import it.fantacalciointelligence.backend.auction.AuctionPriceResponse;
import it.fantacalciointelligence.backend.common.BadRequestException;
import it.fantacalciointelligence.backend.common.ResourceNotFoundException;

@SpringBootTest
@Transactional
class PlayerIntelligenceServiceTest {

    @Autowired
    private PlayerIntelligenceService service;

    @Autowired
    private JdbcClient jdbcClient;

    @MockitoBean
    private AuctionApiPriceService auctionPriceService;

    @BeforeEach
    void configureB51Price() {
        when(auctionPriceService.calculate(
                anyLong(), anyString(), anyInt(), anyInt(), anyBoolean()
        )).thenAnswer(invocation -> {
            Long playerId = invocation.getArgument(0);

            if (playerId == 999999L) {
                throw new ResourceNotFoundException(
                        "Giocatore non trovato"
                );
            }

            int budget = invocation.getArgument(2);
            int teams = invocation.getArgument(3);
            boolean modifier = invocation.getArgument(4);

            return new AuctionPriceResponse(
                    playerId,
                    "B51 test player",
                    "B51 test club",
                    "A",
                    teams,
                    budget,
                    modifier,
                    10,
                    Math.min(20, budget),
                    Math.min(30, budget),
                    new BigDecimal("2.00"),
                    new BigDecimal("80.00"),
                    "PRICED",
                    "B51 test price"
            );
        });
    }

    @Test
    void shouldBuildCompletePlayerIntelligenceProfile() {
        Long playerId = findEligiblePlayerId();

        PlayerIntelligenceResponse response =
                service.findByPlayer(
                        playerId,
                        "2026/2027",
                        1000,
                        8,
                        true
                );

        assertThat(response.playerId())
                .isEqualTo(playerId);

        assertThat(response.playerName())
                .isNotBlank();

        assertThat(response.club())
                .isNotBlank();

        assertThat(response.season())
                .isEqualTo("2026/2027");

        assertThat(response.fantasyRole())
                .isIn("P", "D", "C", "A");

        assertThat(response.minimumPrice())
                .isPositive();

        assertThat(response.recommendedPrice())
                .isBetween(
                        response.minimumPrice(),
                        response.maximumPrice()
                );

        assertThat(response.maximumPrice())
                .isLessThanOrEqualTo(1000);

        assertThat(response.budgetPercentage())
                .isNotNull();

        assertThat(response.auctionScore())
                .isNotNull();

        assertThat(response.strengths())
                .isNotEmpty();

        assertThat(response.risks())
                .isNotNull();

        assertThat(response.finalRecommendation())
                .isNotBlank();

        assertThat(response.intelligenceSummary())
                .contains(response.playerName())
                .contains("Prezzo consigliato");
    }

    @Test
    void shouldRespectDifferentAuctionConfiguration() {
        Long playerId = findEligiblePlayerId();

        PlayerIntelligenceResponse smallLeague =
                service.findByPlayer(
                        playerId,
                        "2026/2027",
                        500,
                        6,
                        false
                );

        PlayerIntelligenceResponse largeLeague =
                service.findByPlayer(
                        playerId,
                        "2026/2027",
                        1000,
                        12,
                        true
                );

        assertThat(smallLeague.playerId())
                .isEqualTo(largeLeague.playerId());

        assertThat(smallLeague.recommendedPrice())
                .isLessThanOrEqualTo(500);

        assertThat(largeLeague.recommendedPrice())
                .isLessThanOrEqualTo(1000);

        assertThat(smallLeague.budgetPercentage())
                .isNotNull();

        assertThat(largeLeague.budgetPercentage())
                .isNotNull();
    }

    @Test
    void shouldIncludeForeignScoutingWhenAvailable() {
        Long playerId = findEligiblePlayerId();

        insertScoutingReport(playerId);

        PlayerIntelligenceResponse response =
                service.findByPlayer(
                        playerId,
                        "2026/2027",
                        1000,
                        8,
                        true
                );

        assertThat(response.foreignScoutingAvailable())
                .isTrue();

        assertThat(response.previousClub())
                .isEqualTo("Ajax");

        assertThat(response.previousLeague())
                .isEqualTo("Eredivisie");

        assertThat(response.previousCountryCode())
                .isEqualTo("NL");

        assertThat(response.adaptationScore())
                .isEqualByComparingTo(
                        new BigDecimal("85.00")
                );

        assertThat(response.scoutingRecommendation())
                .isEqualTo("HIGH_PRIORITY");

        assertThat(response.scoutingSummary())
                .contains("adattamento");

        assertThat(response.strengths())
                .contains("Scouting estero positivo");
    }

    @Test
    void shouldIncludeLatestAvailabilityAndRisks() {
        Long playerId = findEligiblePlayerId();

        insertAvailability(
                playerId,
                7,
                "INJURED",
                0,
                0,
                "Problema muscolare"
        );

        PlayerIntelligenceResponse response =
                service.findByPlayer(
                        playerId,
                        "2026/2027",
                        1000,
                        8,
                        true
                );

        assertThat(response.availabilityMatchday())
                .isEqualTo(7);

        assertThat(response.availabilityStatus())
                .isEqualTo("INJURED");

        assertThat(response.startingProbability())
                .isEqualByComparingTo(BigDecimal.ZERO);

        assertThat(response.availabilityProbability())
                .isEqualByComparingTo(BigDecimal.ZERO);

        assertThat(response.injuryDescription())
                .isEqualTo("Problema muscolare");

        assertThat(response.risks())
                .contains(
                        "Giocatore attualmente infortunato"
                );

        assertThat(response.finalRecommendation())
                .isEqualTo("ATTENDERE AGGIORNAMENTI");
    }

    @Test
    void shouldUseMostRecentAvailabilityMatchday() {
        Long playerId = findEligiblePlayerId();

        insertAvailability(
                playerId,
                4,
                "DOUBTFUL",
                45,
                60,
                null
        );

        insertAvailability(
                playerId,
                8,
                "AVAILABLE",
                85,
                100,
                null
        );

        PlayerIntelligenceResponse response =
                service.findByPlayer(
                        playerId,
                        "2026/2027",
                        1000,
                        8,
                        true
                );

        assertThat(response.availabilityMatchday())
                .isEqualTo(8);

        assertThat(response.availabilityStatus())
                .isEqualTo("AVAILABLE");

        assertThat(response.availabilityProbability())
                .isEqualByComparingTo(
                        new BigDecimal("100.00")
                );
    }

    @Test
    void shouldRejectInvalidPlayerId() {
        assertThatThrownBy(() ->
                service.findByPlayer(
                        0L,
                        "2026/2027",
                        1000,
                        8,
                        true
                )
        )
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining(
                        "identificativo del giocatore"
                );
    }

    @Test
    void shouldRejectInvalidBudget() {
        Long playerId = findEligiblePlayerId();

        assertThatThrownBy(() ->
                service.findByPlayer(
                        playerId,
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
        Long playerId = findEligiblePlayerId();

        assertThatThrownBy(() ->
                service.findByPlayer(
                        playerId,
                        "2026/2027",
                        1000,
                        15,
                        true
                )
        )
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining(
                        "squadre devono essere comprese"
                );
    }

    @Test
    void shouldReturnNotFoundForUnknownPlayer() {
        assertThatThrownBy(() ->
                service.findByPlayer(
                        999999L,
                        "2026/2027",
                        1000,
                        8,
                        true
                )
        )
                .isInstanceOf(
                        ResourceNotFoundException.class
                )
                .hasMessageContaining(
                        "Giocatore non trovato"
                );
    }

    private Long findEligiblePlayerId() {
        Map<String, Object> player =
                jdbcClient.sql("""
                        SELECT
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

                        ORDER BY
                            CASE
                                WHEN role.source = 'FANTACALCIO'
                                    THEN 0
                                ELSE 1
                            END,
                            player.id

                        LIMIT 1
                        """)
                        .query()
                        .singleRow();

        Long playerId =
                ((Number) player.get("player_id"))
                        .longValue();

        Long seasonId =
                ((Number) player.get("season_id"))
                        .longValue();

        String fantasyRole =
                player.get("fantasy_role").toString();

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

        return playerId;
    }
    private Long findSeasonId() {
        return jdbcClient.sql("""
                SELECT id
                FROM seasons
                WHERE code = '2026/2027'
                """)
                .query(Long.class)
                .single();
    }

    private void insertScoutingReport(Long playerId) {
        Long seasonId = findSeasonId();

        jdbcClient.sql("""
                INSERT INTO foreign_player_scouting_reports (
                    player_id,
                    season_id,
                    previous_club,
                    previous_league,
                    previous_country_code,
                    transfer_type,
                    adaptation_score,
                    potential_score,
                    risk_score,
                    confidence_score,
                    recommendation,
                    scouting_summary
                )
                VALUES (
                    :playerId,
                    :seasonId,
                    'Ajax',
                    'Eredivisie',
                    'NL',
                    'PERMANENT',
                    85,
                    90,
                    25,
                    80,
                    'HIGH_PRIORITY',
                    'Ottimo potenziale e buon adattamento '
                        || 'previsto alla Serie A.'
                )
                ON CONFLICT (
                    player_id,
                    season_id
                )
                DO UPDATE SET
                    previous_club = 'Ajax',
                    previous_league = 'Eredivisie',
                    previous_country_code = 'NL',
                    transfer_type = 'PERMANENT',
                    adaptation_score = 85,
                    potential_score = 90,
                    risk_score = 25,
                    confidence_score = 80,
                    recommendation = 'HIGH_PRIORITY',
                    scouting_summary =
                        'Ottimo potenziale e buon adattamento '
                        || 'previsto alla Serie A.',
                    updated_at = CURRENT_TIMESTAMP
                """)
                .param("playerId", playerId)
                .param("seasonId", seasonId)
                .update();
    }

    private void insertAvailability(
            Long playerId,
            int matchday,
            String status,
            int startingProbability,
            int availabilityProbability,
            String injuryDescription
    ) {
        Long seasonId = findSeasonId();

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
                    :matchday,
                    :status,
                    :startingProbability,
                    :availabilityProbability,
                    :injuryDescription,
                    'MANUAL'
                )
                ON CONFLICT (
                    player_id,
                    season_id,
                    matchday
                )
                DO UPDATE SET
                    status = EXCLUDED.status,
                    starting_probability =
                        EXCLUDED.starting_probability,
                    availability_probability =
                        EXCLUDED.availability_probability,
                    injury_description =
                        EXCLUDED.injury_description,
                    source = EXCLUDED.source,
                    updated_at = CURRENT_TIMESTAMP
                """)
                .param("playerId", playerId)
                .param("seasonId", seasonId)
                .param("matchday", matchday)
                .param("status", status)
                .param(
                        "startingProbability",
                        startingProbability
                )
                .param(
                        "availabilityProbability",
                        availabilityProbability
                )
                .param(
                        "injuryDescription",
                        injuryDescription
                )
                .update();
    }
}
