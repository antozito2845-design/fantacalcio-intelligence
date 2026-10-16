package it.fantacalciointelligence.backend.auction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class B67AuctionPricePersistenceIntegrationTest {

    private static final String SEASON = "2026/2027";
    private static final String B66_VERSION = "B66_FEATURES_V3";
    private static final String B67_VERSION = "B67_AUCTION_PRICE_V1";

    @Autowired
    private B67AuctionPriceModelService modelService;

    @Autowired
    private B67AuctionPricePersistenceService persistenceService;

    @Autowired
    private JdbcClient jdbcClient;

    private Long marketOnlyPlayerId;
    private int expectedOperationalPlayers;

    @BeforeEach
    void prepareIsolatedB67Dataset() {

        Long seasonId = jdbcClient.sql("""
                        SELECT id
                        FROM seasons
                        WHERE code = :seasonCode
                        """)
                .param("seasonCode", SEASON)
                .query(Long.class)
                .single();

        /*
         * B67 depends on an ACTIVE B66 algorithm.
         *
         * This is test-only data and the whole test is transactional:
         * Spring rolls it back automatically after every test.
         */
        jdbcClient.sql("""
                        INSERT INTO player_performance_algorithms (
                            version,
                            reference_season_id,
                            status,
                            model_decision
                        )
                        VALUES (
                            :version,
                            :seasonId,
                            'ACTIVE',
                            'COMPONENT_FEATURES_ONLY'
                        )
                        ON CONFLICT (version)
                        DO UPDATE SET
                            reference_season_id = EXCLUDED.reference_season_id,
                            status = 'ACTIVE',
                            model_decision = 'COMPONENT_FEATURES_ONLY'
                        """)
                .param("version", B66_VERSION)
                .param("seasonId", seasonId)
                .update();

        /*
         * The common test catalogue already contains players,
         * registrations and INTERNAL roles.
         *
         * B67 deliberately accepts only the authoritative
         * FANTACALCIO fantasy role, so derive that role from the
         * existing deterministic test role.
         */
        jdbcClient.sql("""
                        INSERT INTO player_roles (
                            player_id,
                            season_id,
                            source,
                            fantasy_role,
                            tactical_role,
                            primary_role
                        )
                        SELECT
                            role.player_id,
                            role.season_id,
                            'FANTACALCIO',
                            role.fantasy_role,
                            role.tactical_role,
                            TRUE
                        FROM player_roles role
                        WHERE role.season_id = :seasonId
                          AND role.source = 'INTERNAL'
                        ON CONFLICT (
                            player_id,
                            season_id,
                            source
                        )
                        DO UPDATE SET
                            fantasy_role = EXCLUDED.fantasy_role,
                            tactical_role = EXCLUDED.tactical_role,
                            primary_role = TRUE
                        """)
                .param("seasonId", seasonId)
                .update();

        /*
         * Pick one deterministic player to exercise B67's
         * certified NO_HISTORY -> MARKET_ONLY policy.
         */
        marketOnlyPlayerId = jdbcClient.sql("""
                        SELECT MIN(player.id)
                        FROM players player
                        JOIN player_registrations registration
                            ON registration.player_id = player.id
                           AND registration.season_id = :seasonId
                           AND registration.registration_status = 'ACTIVE'
                        JOIN player_roles role
                            ON role.player_id = player.id
                           AND role.season_id = :seasonId
                           AND role.source = 'FANTACALCIO'
                        JOIN season_clubs season_club
                            ON season_club.season_id = :seasonId
                           AND season_club.club_id = registration.club_id
                        WHERE player.active = TRUE
                        """)
                .param("seasonId", seasonId)
                .query(Long.class)
                .single();

        assertThat(marketOnlyPlayerId)
                .isNotNull();

        /*
         * Deterministic B66 feature profiles.
         *
         * No invented production data is involved:
         * these rows exist exclusively inside the rollback-only
         * test transaction and exercise B67's actual DB contract.
         */
        jdbcClient.sql("""
                        INSERT INTO player_performance_feature_profiles (
                            player_id,
                            reference_season_id,
                            current_club_id,
                            role_code,
                            algorithm_version,
                            history_seasons_available,
                            latest_history_season,
                            source_mix,
                            small_sample,
                            data_confidence_score,
                            input_fingerprint,
                            feature_values,
                            evidence
                        )
                        SELECT
                            player.id,
                            :seasonId,
                            registration.club_id,
                            role.fantasy_role,
                            :algorithmVersion,

                            CASE
                                WHEN player.id = :marketOnlyPlayerId
                                    THEN 0
                                ELSE 1
                            END,

                            CASE
                                WHEN player.id = :marketOnlyPlayerId
                                    THEN NULL
                                ELSE '2025/2026'
                            END,

                            CASE
                                WHEN player.id = :marketOnlyPlayerId
                                    THEN 'NO_HISTORY'
                                ELSE 'ONE_SEASON_ONLY'
                            END,

                            CASE
                                WHEN player.id = :marketOnlyPlayerId
                                    THEN TRUE
                                ELSE FALSE
                            END,

                            CASE
                                WHEN player.id = :marketOnlyPlayerId
                                    THEN 0
                                ELSE 80
                            END,

                            repeat(
                                CASE
                                    WHEN player.id = :marketOnlyPlayerId
                                        THEN 'a'
                                    ELSE 'b'
                                END,
                                64
                            ),

                            CASE
                                WHEN player.id = :marketOnlyPlayerId
                                    THEN jsonb_build_object(
                                        'fantasy_average_role_percentile', NULL,
                                        'usage_role_percentile', NULL,
                                        'goal_rate_role_percentile', NULL,
                                        'assist_rate_role_percentile', NULL
                                    )
                                ELSE jsonb_build_object(
                                    'fantasy_average_role_percentile',
                                        ((player.id * 17) % 101),
                                    'usage_role_percentile',
                                        ((player.id * 23) % 101),
                                    'goal_rate_role_percentile',
                                        ((player.id * 31) % 101),
                                    'assist_rate_role_percentile',
                                        ((player.id * 43) % 101)
                                )
                            END,

                            jsonb_build_object(
                                'test_dataset', TRUE,
                                'purpose', 'B67 isolated integration test'
                            )

                        FROM players player

                        JOIN player_registrations registration
                            ON registration.player_id = player.id
                           AND registration.season_id = :seasonId
                           AND registration.registration_status = 'ACTIVE'

                        JOIN player_roles role
                            ON role.player_id = player.id
                           AND role.season_id = :seasonId
                           AND role.source = 'FANTACALCIO'

                        JOIN season_clubs season_club
                            ON season_club.season_id = :seasonId
                           AND season_club.club_id = registration.club_id

                        WHERE player.active = TRUE

                        ON CONFLICT (
                            player_id,
                            reference_season_id,
                            algorithm_version
                        )
                        DO UPDATE SET
                            current_club_id = EXCLUDED.current_club_id,
                            role_code = EXCLUDED.role_code,
                            history_seasons_available =
                                EXCLUDED.history_seasons_available,
                            latest_history_season =
                                EXCLUDED.latest_history_season,
                            source_mix = EXCLUDED.source_mix,
                            small_sample = EXCLUDED.small_sample,
                            data_confidence_score =
                                EXCLUDED.data_confidence_score,
                            input_fingerprint =
                                EXCLUDED.input_fingerprint,
                            feature_values =
                                EXCLUDED.feature_values,
                            evidence =
                                EXCLUDED.evidence
                        """)
                .param("seasonId", seasonId)
                .param("algorithmVersion", B66_VERSION)
                .param("marketOnlyPlayerId", marketOnlyPlayerId)
                .update();

        /*
         * Fantacalcio market evidence is deterministic as well.
         *
         * It gives every test player genuine market signals while
         * the selected NO_HISTORY player has no performance signals.
         */
        jdbcClient.sql("""
                        INSERT INTO player_market_values (
                            player_id,
                            season_id,
                            source,
                            quotation,
                            fantasy_market_value
                        )
                        SELECT
                            player.id,
                            :seasonId,
                            'FANTACALCIO',
                            1 + (player.id % 30),
                            1 + (player.id % 80)
                        FROM players player
                        JOIN player_registrations registration
                            ON registration.player_id = player.id
                           AND registration.season_id = :seasonId
                           AND registration.registration_status = 'ACTIVE'
                        JOIN player_roles role
                            ON role.player_id = player.id
                           AND role.season_id = :seasonId
                           AND role.source = 'FANTACALCIO'
                        WHERE player.active = TRUE
                        ON CONFLICT (
                            player_id,
                            season_id,
                            source
                        )
                        DO UPDATE SET
                            quotation = EXCLUDED.quotation,
                            fantasy_market_value =
                                EXCLUDED.fantasy_market_value
                        """)
                .param("seasonId", seasonId)
                .update();

        expectedOperationalPlayers = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM seasons season

                        JOIN player_registrations registration
                            ON registration.season_id = season.id
                           AND registration.registration_status = 'ACTIVE'

                        JOIN players player
                            ON player.id = registration.player_id
                           AND player.active = TRUE

                        JOIN season_clubs season_club
                            ON season_club.season_id = season.id
                           AND season_club.club_id =
                               registration.club_id

                        JOIN player_roles role
                            ON role.player_id = player.id
                           AND role.season_id = season.id
                           AND role.source = 'FANTACALCIO'

                        JOIN player_performance_feature_profiles performance
                            ON performance.player_id = player.id
                           AND performance.reference_season_id =
                               season.id
                           AND performance.algorithm_version =
                               :algorithmVersion

                        WHERE season.code = :seasonCode
                        """)
                .param("algorithmVersion", B66_VERSION)
                .param("seasonCode", SEASON)
                .query(Integer.class)
                .single();

        assertThat(expectedOperationalPlayers)
                .isGreaterThan(0);
    }

    @Test
    void shouldGenerateAndPersistOperationalB67MarketIdempotently() {

        List<B67AuctionPriceModelService.AuctionPriceModelResult>
                calculated =
                modelService.calculateAll(
                        SEASON,
                        1000,
                        8,
                        false
                );

        assertThat(calculated)
                .hasSize(expectedOperationalPlayers);

        Set<String> roles = calculated
                .stream()
                .map(
                        B67AuctionPriceModelService
                                .AuctionPriceModelResult
                                ::role
                )
                .collect(Collectors.toSet());

        assertThat(roles)
                .containsExactlyInAnyOrder(
                        "P",
                        "D",
                        "C",
                        "A"
                );

        assertThat(calculated)
                .allSatisfy(result -> {

                    assertThat(result.pricingStatus())
                            .isEqualTo("PRICED");

                    assertThat(result.minimumPrice())
                            .isNotNull()
                            .isGreaterThanOrEqualTo(1);

                    assertThat(result.recommendedPrice())
                            .isNotNull()
                            .isGreaterThanOrEqualTo(
                                    result.minimumPrice()
                            );

                    assertThat(result.maximumPrice())
                            .isNotNull()
                            .isGreaterThanOrEqualTo(
                                    result.recommendedPrice()
                            );

                    assertThat(result.auctionScore())
                            .isNotNull()
                            .isBetween(
                                    0.0,
                                    100.0
                            );
                });

        B67AuctionPriceModelService.AuctionPriceModelResult
                marketOnly =
                calculated
                        .stream()
                        .filter(result ->
                                result.playerId()
                                        .equals(
                                                marketOnlyPlayerId
                                        )
                        )
                        .findFirst()
                        .orElseThrow();

        assertThat(marketOnly.historySeasonsAvailable())
                .isZero();

        assertThat(marketOnly.dataConfidenceScore())
                .isZero();

        assertThat(marketOnly.performanceSignalCount())
                .isZero();

        assertThat(marketOnly.evidenceTier())
                .isEqualTo("MARKET_ONLY");

        assertThat(marketOnly.quotation())
                .isNotNull()
                .isGreaterThanOrEqualTo(1.0);

        assertThat(marketOnly.fantasyMarketValue())
                .isNotNull()
                .isGreaterThanOrEqualTo(1.0);

        assertThat(marketOnly.pricingStatus())
                .isEqualTo("PRICED");

        B67AuctionPricePersistenceService.PersistResult first =
                persistenceService.generateAndPersist(
                        SEASON,
                        1000,
                        8,
                        false
                );

        assertThat(first.calculatedProfiles())
                .isEqualTo(expectedOperationalPlayers);

        assertThat(
                first.insertedProfiles()
                        + first.unchangedProfiles()
        ).isEqualTo(expectedOperationalPlayers);

        B67AuctionPricePersistenceService.PersistResult second =
                persistenceService.generateAndPersist(
                        SEASON,
                        1000,
                        8,
                        false
                );

        assertThat(second.calculatedProfiles())
                .isEqualTo(expectedOperationalPlayers);

        assertThat(second.insertedProfiles())
                .isZero();

        assertThat(second.unchangedProfiles())
                .isEqualTo(expectedOperationalPlayers);

        Integer persisted = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM auction_price_profiles
                        WHERE algorithm_version =
                            :algorithmVersion
                          AND league_teams = 8
                          AND total_budget = 1000
                          AND defense_modifier = FALSE
                        """)
                .param(
                        "algorithmVersion",
                        B67_VERSION
                )
                .query(Integer.class)
                .single();

        assertThat(persisted)
                .isEqualTo(expectedOperationalPlayers);

        Integer priced = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM auction_price_profiles
                        WHERE algorithm_version =
                            :algorithmVersion
                          AND league_teams = 8
                          AND total_budget = 1000
                          AND defense_modifier = FALSE
                          AND pricing_status = 'PRICED'
                        """)
                .param(
                        "algorithmVersion",
                        B67_VERSION
                )
                .query(Integer.class)
                .single();

        assertThat(priced)
                .isEqualTo(expectedOperationalPlayers);

        Integer distinctPlayers = jdbcClient.sql("""
                        SELECT COUNT(DISTINCT player_id)
                        FROM auction_price_profiles
                        WHERE algorithm_version =
                            :algorithmVersion
                          AND league_teams = 8
                          AND total_budget = 1000
                          AND defense_modifier = FALSE
                        """)
                .param(
                        "algorithmVersion",
                        B67_VERSION
                )
                .query(Integer.class)
                .single();

        assertThat(distinctPlayers)
                .isEqualTo(expectedOperationalPlayers);

        Integer invalidPriceStates = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM auction_price_profiles
                        WHERE algorithm_version =
                            :algorithmVersion
                          AND league_teams = 8
                          AND total_budget = 1000
                          AND defense_modifier = FALSE
                          AND (
                              pricing_status <> 'PRICED'
                              OR minimum_price IS NULL
                              OR recommended_price IS NULL
                              OR maximum_price IS NULL
                              OR minimum_price < 1
                              OR recommended_price < minimum_price
                              OR maximum_price < recommended_price
                          )
                        """)
                .param(
                        "algorithmVersion",
                        B67_VERSION
                )
                .query(Integer.class)
                .single();

        assertThat(invalidPriceStates)
                .isZero();
    }

    @Test
    void shouldSupportAllLeagueConfigurationBoundaries() {

        List<B67AuctionPriceModelService.AuctionPriceModelResult>
                minimumLeague =
                modelService.calculateAll(
                        SEASON,
                        150,
                        6,
                        false
                );

        assertThat(minimumLeague)
                .hasSize(expectedOperationalPlayers);

        List<B67AuctionPriceModelService.AuctionPriceModelResult>
                maximumLeague =
                modelService.calculateAll(
                        SEASON,
                        1000,
                        12,
                        false
                );

        assertThat(maximumLeague)
                .hasSize(expectedOperationalPlayers);

        List<B67AuctionPriceModelService.AuctionPriceModelResult>
                modifierOn =
                modelService.calculateAll(
                        SEASON,
                        1000,
                        8,
                        true
                );

        assertThat(modifierOn)
                .hasSize(expectedOperationalPlayers);

        long defenseTotalNoModifier =
                modelService.calculateAll(
                                SEASON,
                                1000,
                                8,
                                false
                        )
                        .stream()
                        .filter(result ->
                                "D".equals(
                                        result.role()
                                )
                        )
                        .mapToLong(
                                B67AuctionPriceModelService
                                        .AuctionPriceModelResult
                                        ::recommendedPrice
                        )
                        .sum();

        long defenseTotalModifier =
                modifierOn
                        .stream()
                        .filter(result ->
                                "D".equals(
                                        result.role()
                                )
                        )
                        .mapToLong(
                                B67AuctionPriceModelService
                                        .AuctionPriceModelResult
                                        ::recommendedPrice
                        )
                        .sum();

        assertThat(defenseTotalModifier)
                .isGreaterThanOrEqualTo(
                        defenseTotalNoModifier
                );
    }
}