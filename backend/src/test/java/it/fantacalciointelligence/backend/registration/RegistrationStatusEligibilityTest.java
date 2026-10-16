package it.fantacalciointelligence.backend.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.auction.AuctionApiPriceService;
import it.fantacalciointelligence.backend.auction.AuctionRankingService;
import it.fantacalciointelligence.backend.auction.AuctionPriceResponse;
import it.fantacalciointelligence.backend.development.PlayerDevelopmentAssessmentService;
import it.fantacalciointelligence.backend.evaluation.PlayerEvaluationGenerationService;
import it.fantacalciointelligence.backend.fixture.FixtureDifficultyService;
import it.fantacalciointelligence.backend.pairing.PlayerPairingService;
import it.fantacalciointelligence.backend.player.PlayerCatalogResponse;
import it.fantacalciointelligence.backend.player.PlayerCatalogService;

@SpringBootTest
@Transactional
class RegistrationStatusEligibilityTest {

    private static final String SEASON_CODE =
            "2190/2191";

    private static final String EXCLUDED_SEASON_CODE =
            "2191/2192";

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private PlayerCatalogService playerCatalogService;

    @Autowired
    private PlayerEvaluationGenerationService evaluationService;

    @Autowired
    private PlayerDevelopmentAssessmentService developmentService;

    @Test
    void onlyActiveRegistrationMustRemainOperationallyEligible() {

        TestContext context =
                createContext(
                        SEASON_CODE,
                        2190,
                        2191,
                        "status-contract-club-2190"
                );

        Long active =
                createOfficialPlayer(
                        context,
                        "Status Active Player",
                        "status active player",
                        "ACTIVE"
                );

        Long inactive =
                createOfficialPlayer(
                        context,
                        "Status Inactive Player",
                        "status inactive player",
                        "INACTIVE"
                );

        Long loaned =
                createOfficialPlayer(
                        context,
                        "Status Loaned Player",
                        "status loaned player",
                        "LOANED"
                );

        Long released =
                createOfficialPlayer(
                        context,
                        "Status Released Player",
                        "status released player",
                        "RELEASED"
                );

        /*
         * Tutti hanno:
         * - players.active = TRUE
         * - registration per la stagione
         * - ruolo Fantacalcio
         * - reconciliation
         * - market value
         *
         * L'unica differenza intenzionale è registration_status.
         */

        var catalog =
                playerCatalogService.findPlayers(
                        SEASON_CODE,
                        "Status",
                        null,
                        null,
                        0,
                        25
                );

        assertThat(
                catalog.totalElements()
        ).isEqualTo(1);

        assertThat(
                catalog.content()
        )
                .extracting(
                        PlayerCatalogResponse::id
                )
                .containsExactly(active);

        assertThat(
                catalog.content()
        )
                .extracting(
                        PlayerCatalogResponse::id
                )
                .doesNotContain(
                        inactive,
                        loaned,
                        released
                );

        var evaluation =
                evaluationService.generate(
                        SEASON_CODE
                );

        assertThat(
                evaluation.eligiblePlayers()
        ).isEqualTo(1);

        assertThat(
                evaluation.generatedEvaluations()
        ).isEqualTo(1);

        assertThat(
                evaluationExists(
                        active,
                        context.seasonId()
                )
        ).isTrue();

        assertThat(
                evaluationExists(
                        inactive,
                        context.seasonId()
                )
        ).isFalse();

        assertThat(
                evaluationExists(
                        loaned,
                        context.seasonId()
                )
        ).isFalse();

        assertThat(
                evaluationExists(
                        released,
                        context.seasonId()
                )
        ).isFalse();

        var development =
                developmentService.generate(
                        SEASON_CODE
                );

        assertThat(
                development.eligiblePlayers()
        ).isEqualTo(1);

        assertThat(
                development.generatedAssessments()
        ).isEqualTo(1);

        assertThat(
                developmentExists(
                        active,
                        context.seasonId()
                )
        ).isTrue();

        assertThat(
                developmentExists(
                        inactive,
                        context.seasonId()
                )
        ).isFalse();

        assertThat(
                developmentExists(
                        loaned,
                        context.seasonId()
                )
        ).isFalse();

        assertThat(
                developmentExists(
                        released,
                        context.seasonId()
                )
        ).isFalse();
    }

    @Test
    void auctionRankingMustNotSendNonActiveRegistrationsToPricing() {

        TestContext context =
                createContext(
                        EXCLUDED_SEASON_CODE,
                        2191,
                        2192,
                        "status-auction-club-2191"
                );

        createOfficialPlayer(
                context,
                "Auction Inactive Player",
                "auction inactive player",
                "INACTIVE"
        );

        createOfficialPlayer(
                context,
                "Auction Loaned Player",
                "auction loaned player",
                "LOANED"
        );

        createOfficialPlayer(
                context,
                "Auction Released Player",
                "auction released player",
                "RELEASED"
        );

        AuctionApiPriceService auctionApiPriceService =
                mock(
                        AuctionApiPriceService.class
                );

        AuctionRankingService service =
                new AuctionRankingService(
                        jdbcClient,
                        auctionApiPriceService
                );

        var ranking =
                service.calculateRanking(
                        EXCLUDED_SEASON_CODE,
                        1000,
                        8,
                        true,
                        "A",
                        null,
                        100
                );

        assertThat(ranking)
                .isEmpty();

        verifyNoInteractions(
                auctionApiPriceService
        );
    }

    @Test
    void pairingMustNotSendNonActiveRegistrationsToDownstreamServices() {

        TestContext context =
                createContext(
                        "2192/2193",
                        2192,
                        2193,
                        "status-pairing-club-2192"
                );

        createOfficialPlayer(
                context,
                "Pairing Inactive Player",
                "pairing inactive player",
                "INACTIVE"
        );

        createOfficialPlayer(
                context,
                "Pairing Loaned Player",
                "pairing loaned player",
                "LOANED"
        );

        createOfficialPlayer(
                context,
                "Pairing Released Player",
                "pairing released player",
                "RELEASED"
        );

        AuctionApiPriceService auctionPriceService =
                mock(
                        AuctionApiPriceService.class
                );

        FixtureDifficultyService fixtureDifficultyService =
                mock(
                        FixtureDifficultyService.class
                );

        PlayerPairingService service =
                new PlayerPairingService(
                        jdbcClient,
                        auctionPriceService,
                        fixtureDifficultyService
                );

        var pairings =
                service.findPairings(
                        "2192/2193",
                        "A",
                        1000,
                        8,
                        true,
                        1,
                        5,
                        20
                );

        assertThat(pairings)
                .isEmpty();

        verifyNoInteractions(
                auctionPriceService,
                fixtureDifficultyService
        );
    }

    @Test
    void pairingMustUseOneB51MarketAndSkipMissingEvidence() {
        TestContext context =
                createContext(
                        "2193/2194",
                        2193,
                        2194,
                        "status-pairing-club-2193"
                );

        Long first = createOfficialPlayer(
                context,
                "Pairing Priced Player",
                "pairing priced player",
                "ACTIVE"
        );

        createOfficialPlayer(
                context,
                "Pairing Missing Evidence Player",
                "pairing missing evidence player",
                "ACTIVE"
        );

        AuctionApiPriceService auctionPriceService =
                mock(AuctionApiPriceService.class);

        FixtureDifficultyService fixtureDifficultyService =
                mock(FixtureDifficultyService.class);

        when(auctionPriceService.calculateAll(
                "2193/2194", 1000, 8, true
        )).thenReturn(List.of(
                new AuctionPriceResponse(
                        first,
                        "Pairing Priced Player",
                        "Status Club 2193",
                        "A",
                        8,
                        1000,
                        true,
                        10,
                        20,
                        30,
                        new BigDecimal("2.00"),
                        new BigDecimal("80.00"),
                        "PRICED",
                        "B51 test price"
                )
        ));

        when(fixtureDifficultyService.findClubFixtures(
                "2193/2194",
                "status-pairing-club-2193",
                1,
                5
        )).thenReturn(List.of());

        PlayerPairingService service =
                new PlayerPairingService(
                        jdbcClient,
                        auctionPriceService,
                        fixtureDifficultyService
                );

        assertThat(service.findPairings(
                "2193/2194", "A", 1000, 8, true,
                1, 5, 20
        )).isEmpty();

        verify(auctionPriceService, times(1))
                .calculateAll("2193/2194", 1000, 8, true);

        verify(fixtureDifficultyService, times(1))
                .findClubFixtures(
                        "2193/2194",
                        "status-pairing-club-2193",
                        1,
                        5
                );
    }

    private TestContext createContext(
            String seasonCode,
            int startYear,
            int endYear,
            String clubSlug
    ) {

        Long seasonId =
                jdbcClient.sql("""
                        INSERT INTO seasons (
                            code,
                            start_year,
                            end_year,
                            current_season
                        )
                        VALUES (
                            :code,
                            :startYear,
                            :endYear,
                            FALSE
                        )
                        RETURNING id
                        """)
                        .param(
                                "code",
                                seasonCode
                        )
                        .param(
                                "startYear",
                                startYear
                        )
                        .param(
                                "endYear",
                                endYear
                        )
                        .query(Long.class)
                        .single();

        Long clubId =
                jdbcClient.sql("""
                        INSERT INTO clubs (
                            name,
                            short_name,
                            slug,
                            country_code,
                            active
                        )
                        VALUES (
                            :name,
                            :name,
                            :slug,
                            'IT',
                            TRUE
                        )
                        RETURNING id
                        """)
                        .param(
                                "name",
                                "Status Club "
                                        + startYear
                        )
                        .param(
                                "slug",
                                clubSlug
                        )
                        .query(Long.class)
                        .single();

        jdbcClient.sql("INSERT INTO season_clubs(season_id,club_id) VALUES(:seasonId,:clubId)")
                .param("seasonId",seasonId).param("clubId",clubId).update();

        return new TestContext(
                seasonId,
                clubId,
                seasonCode
        );
    }

    private Long createOfficialPlayer(
            TestContext context,
            String displayName,
            String normalizedName,
            String registrationStatus
    ) {

        Long playerId =
                jdbcClient.sql("""
                        INSERT INTO players (
                            last_name,
                            display_name,
                            normalized_name,
                            active
                        )
                        VALUES (
                            :displayName,
                            :displayName,
                            :normalizedName,
                            TRUE
                        )
                        RETURNING id
                        """)
                        .param(
                                "displayName",
                                displayName
                        )
                        .param(
                                "normalizedName",
                                normalizedName
                        )
                        .query(Long.class)
                        .single();

        jdbcClient.sql("""
                INSERT INTO player_registrations (
                    player_id,
                    club_id,
                    season_id,
                    registration_status
                )
                VALUES (
                    :playerId,
                    :clubId,
                    :seasonId,
                    :status
                )
                """)
                .param(
                        "playerId",
                        playerId
                )
                .param(
                        "clubId",
                        context.clubId()
                )
                .param(
                        "seasonId",
                        context.seasonId()
                )
                .param(
                        "status",
                        registrationStatus
                )
                .update();

        jdbcClient.sql("""
                INSERT INTO player_roles (
                    player_id,
                    season_id,
                    source,
                    fantasy_role,
                    primary_role
                )
                VALUES (
                    :playerId,
                    :seasonId,
                    'FANTACALCIO',
                    'A',
                    TRUE
                )
                """)
                .param(
                        "playerId",
                        playerId
                )
                .param(
                        "seasonId",
                        context.seasonId()
                )
                .update();

        jdbcClient.sql("""
                INSERT INTO player_reconciliation_results (
                    season_id,
                    player_id,
                    normalized_name,
                    normalized_club_name,
                    sources_count,
                    identity_resolution_method,
                    club_resolution_source
                )
                VALUES (
                    :seasonId,
                    :playerId,
                    :normalizedName,
                    :normalizedClubName,
                    2,
                    'VERIFIED_SOURCE_LINK',
                    'FANTACALCIO'
                )
                """)
                .param(
                        "seasonId",
                        context.seasonId()
                )
                .param(
                        "playerId",
                        playerId
                )
                .param(
                        "normalizedName",
                        normalizedName
                )
                .param(
                        "normalizedClubName",
                        "status club"
                )
                .update();

        jdbcClient.sql("""
                INSERT INTO player_market_values (
                    player_id,
                    season_id,
                    source,
                    quotation,
                    fantasy_market_value
                )
                VALUES (
                    :playerId,
                    :seasonId,
                    'FANTACALCIO',
                    :quotation,
                    :fantasyMarketValue
                )
                """)
                .param(
                        "playerId",
                        playerId
                )
                .param(
                        "seasonId",
                        context.seasonId()
                )
                .param(
                        "quotation",
                        new BigDecimal("10")
                )
                .param(
                        "fantasyMarketValue",
                        new BigDecimal("50")
                )
                .update();

        return playerId;
    }

    private boolean evaluationExists(
            Long playerId,
            Long seasonId
    ) {

        return jdbcClient.sql("""
                SELECT EXISTS (
                    SELECT 1
                    FROM player_evaluations
                    WHERE player_id = :playerId
                      AND season_id = :seasonId
                )
                """)
                .param(
                        "playerId",
                        playerId
                )
                .param(
                        "seasonId",
                        seasonId
                )
                .query(Boolean.class)
                .single();
    }

    private boolean developmentExists(
            Long playerId,
            Long seasonId
    ) {

        return jdbcClient.sql("""
                SELECT EXISTS (
                    SELECT 1
                    FROM player_development_assessments
                    WHERE player_id = :playerId
                      AND target_season_id = :seasonId
                )
                """)
                .param(
                        "playerId",
                        playerId
                )
                .param(
                        "seasonId",
                        seasonId
                )
                .query(Boolean.class)
                .single();
    }

    private record TestContext(
            Long seasonId,
            Long clubId,
            String seasonCode
    ) {
    }
}
