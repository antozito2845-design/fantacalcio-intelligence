package it.fantacalciointelligence.backend.auction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(
        named = "B51_SANDBOX_CERTIFICATION",
        matches = "true"
)
class AuctionPricePersistenceServiceIntegrationTest {

    private static final String SEASON = "2026/2027";
    private static final int BUDGET = 1000;
    private static final int TEAMS = 8;
    private static final boolean DEFENSE_MODIFIER = false;

    @Autowired
    private AuctionPricePersistenceService persistenceService;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void shouldPersistB51IdempotentlyAndRespectCertifiedInvariants() {

        clearOnlyB51CertificationConfiguration();

        AuctionPricePersistenceService.PersistResult pass1 =
                persistenceService.generateAndPersist(
                        SEASON,
                        BUDGET,
                        TEAMS,
                        DEFENSE_MODIFIER
                );

        assertThat(pass1.algorithmVersion())
                .isEqualTo(AuctionPriceModelService.ALGORITHM_VERSION);

        assertThat(pass1.calculatedProfiles())
                .isEqualTo(495);

        assertThat(pass1.insertedProfiles())
                .isEqualTo(495);

        assertThat(pass1.unchangedProfiles())
                .isZero();

        assertDatabaseState();

        AuctionPricePersistenceService.PersistResult pass2 =
                persistenceService.generateAndPersist(
                        SEASON,
                        BUDGET,
                        TEAMS,
                        DEFENSE_MODIFIER
                );

        assertThat(pass2.calculatedProfiles())
                .isEqualTo(495);

        assertThat(pass2.insertedProfiles())
                .isZero();

        assertThat(pass2.unchangedProfiles())
                .isEqualTo(495);

        assertDatabaseState();

        System.out.println();
        System.out.println("=== B51 SANDBOX PASS1 ===");
        System.out.println(
                "calculated=" + pass1.calculatedProfiles()
                        + " inserted=" + pass1.insertedProfiles()
                        + " unchanged=" + pass1.unchangedProfiles()
        );

        System.out.println();
        System.out.println("=== B51 SANDBOX PASS2 ===");
        System.out.println(
                "calculated=" + pass2.calculatedProfiles()
                        + " inserted=" + pass2.insertedProfiles()
                        + " unchanged=" + pass2.unchangedProfiles()
        );

        printRoleAudit();
        printCriticalPlayers();
    }

    private void clearOnlyB51CertificationConfiguration() {

        jdbcClient.sql("""
                DELETE FROM auction_price_profiles
                WHERE algorithm_version = :version
                  AND league_teams = :teams
                  AND total_budget = :budget
                  AND defense_modifier = :defenseModifier
                """)
                .param(
                        "version",
                        AuctionPriceModelService.ALGORITHM_VERSION
                )
                .param("teams", TEAMS)
                .param("budget", BUDGET)
                .param(
                        "defenseModifier",
                        DEFENSE_MODIFIER
                )
                .update();

        Integer remainingProfiles =
                jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM auction_price_profiles
                        WHERE algorithm_version = :version
                        """)
                        .param(
                                "version",
                                AuctionPriceModelService.ALGORITHM_VERSION
                        )
                        .query(Integer.class)
                        .single();

        if (remainingProfiles == 0) {
            jdbcClient.sql("""
                    DELETE FROM auction_price_algorithms
                    WHERE version = :version
                    """)
                    .param(
                            "version",
                            AuctionPriceModelService.ALGORITHM_VERSION
                    )
                    .update();
        }
    }

    private void assertDatabaseState() {

        Integer total =
                count("""
                        SELECT COUNT(*)
                        FROM auction_price_profiles
                        WHERE algorithm_version =
                            'B51_AUCTION_PRICE_V1'
                          AND league_teams = 8
                          AND total_budget = 1000
                          AND defense_modifier = FALSE
                        """);

        Integer priced =
                count("""
                        SELECT COUNT(*)
                        FROM auction_price_profiles
                        WHERE algorithm_version =
                            'B51_AUCTION_PRICE_V1'
                          AND league_teams = 8
                          AND total_budget = 1000
                          AND defense_modifier = FALSE
                          AND pricing_status = 'PRICED'
                        """);

        Integer insufficient =
                count("""
                        SELECT COUNT(*)
                        FROM auction_price_profiles
                        WHERE algorithm_version =
                            'B51_AUCTION_PRICE_V1'
                          AND league_teams = 8
                          AND total_budget = 1000
                          AND defense_modifier = FALSE
                          AND pricing_status =
                            'INSUFFICIENT_EVIDENCE'
                        """);

        Integer invalidPriced =
                count("""
                        SELECT COUNT(*)
                        FROM auction_price_profiles
                        WHERE algorithm_version =
                            'B51_AUCTION_PRICE_V1'
                          AND league_teams = 8
                          AND total_budget = 1000
                          AND defense_modifier = FALSE
                          AND pricing_status = 'PRICED'
                          AND (
                                recommended_price IS NULL
                                OR recommended_price < 1
                                OR minimum_price IS NULL
                                OR maximum_price IS NULL
                                OR minimum_price >
                                    recommended_price
                                OR maximum_price <
                                    recommended_price
                          )
                        """);

        Integer invalidInsufficient =
                count("""
                        SELECT COUNT(*)
                        FROM auction_price_profiles
                        WHERE algorithm_version =
                            'B51_AUCTION_PRICE_V1'
                          AND league_teams = 8
                          AND total_budget = 1000
                          AND defense_modifier = FALSE
                          AND pricing_status =
                            'INSUFFICIENT_EVIDENCE'
                          AND (
                                auction_score IS NOT NULL
                                OR auction_role_rank IS NOT NULL
                                OR recommended_price IS NOT NULL
                                OR minimum_price IS NOT NULL
                                OR maximum_price IS NOT NULL
                          )
                        """);

        Integer algorithms =
                count("""
                        SELECT COUNT(*)
                        FROM auction_price_algorithms
                        WHERE version =
                            'B51_AUCTION_PRICE_V1'
                        """);

        assertThat(total).isEqualTo(495);
        assertThat(priced).isEqualTo(451);
        assertThat(insufficient).isEqualTo(44);
        assertThat(invalidPriced).isZero();
        assertThat(invalidInsufficient).isZero();
        assertThat(algorithms).isEqualTo(1);

        List<Map<String, Object>> roleBudgets =
                jdbcClient.sql("""
                        SELECT
                            role_code,
                            COUNT(*) AS ranked_players,
                            SUM(recommended_price) AS total_price
                        FROM auction_price_profiles
                        WHERE algorithm_version =
                            'B51_AUCTION_PRICE_V1'
                          AND league_teams = 8
                          AND total_budget = 1000
                          AND defense_modifier = FALSE
                          AND pricing_status = 'PRICED'
                        GROUP BY role_code
                        ORDER BY role_code
                        """)
                        .query()
                        .listOfRows();

        for (Map<String, Object> row : roleBudgets) {

            String role =
                    row.get("role_code").toString();

            int rankedPlayers =
                    ((Number) row.get("ranked_players"))
                            .intValue();

            int totalPrice =
                    ((Number) row.get("total_price"))
                            .intValue();

            int marketSlots =
                    switch (role) {
                        case "P" -> TEAMS * 3;
                        case "D" -> TEAMS * 8;
                        case "C" -> TEAMS * 8;
                        case "A" -> TEAMS * 6;
                        default ->
                                throw new IllegalStateException(
                                        "Ruolo inatteso: " + role
                                );
                    };

            int maximumAllowed =
                    switch (role) {
                        case "P" -> 480;
                        case "D" -> 800;
                        case "C" -> 2000;
                        case "A" -> 4720;
                        default ->
                                throw new IllegalStateException(
                                        "Ruolo inatteso: " + role
                                );
                    };

            int outsideMarketSlots =
                    Math.max(
                            0,
                            rankedPlayers - marketSlots
                    );

            int effectiveMarketSpend =
                    totalPrice - outsideMarketSlots;

            assertThat(effectiveMarketSpend)
                    .as(
                            "Budget slot mercato ruolo "
                                    + role
                    )
                    .isLessThanOrEqualTo(maximumAllowed);
        }
    }

    private Integer count(String sql) {
        return jdbcClient
                .sql(sql)
                .query(Integer.class)
                .single();
    }

    private void printRoleAudit() {

        System.out.println();
        System.out.println("=== B51 ROLE AUDIT ===");

        List<Map<String, Object>> rows =
                jdbcClient.sql("""
                        SELECT
                            role_code,
                            COUNT(*) FILTER (
                                WHERE pricing_status = 'PRICED'
                            ) AS priced,
                            COUNT(*) FILTER (
                                WHERE pricing_status =
                                    'INSUFFICIENT_EVIDENCE'
                            ) AS insufficient,
                            SUM(
                                CASE
                                    WHEN pricing_status = 'PRICED'
                                    THEN recommended_price
                                    ELSE 0
                                END
                            ) AS recommended_sum
                        FROM auction_price_profiles
                        WHERE algorithm_version =
                            'B51_AUCTION_PRICE_V1'
                          AND league_teams = 8
                          AND total_budget = 1000
                          AND defense_modifier = FALSE
                        GROUP BY role_code
                        ORDER BY role_code
                        """)
                        .query()
                        .listOfRows();

        for (Map<String, Object> row : rows) {
            System.out.println(row);
        }
    }

    private void printCriticalPlayers() {

        System.out.println();
        System.out.println("=== B51 CRITICAL PLAYERS ===");

        List<Map<String, Object>> rows =
                jdbcClient.sql("""
                        SELECT
                            p.display_name,
                            profile.role_code,
                            profile.fantasy_market_value,
                            profile.auction_score,
                            profile.auction_role_rank,
                            profile.recommended_price
                        FROM auction_price_profiles profile
                        JOIN players p
                            ON p.id = profile.player_id
                        WHERE profile.algorithm_version =
                            'B51_AUCTION_PRICE_V1'
                          AND profile.league_teams = 8
                          AND profile.total_budget = 1000
                          AND profile.defense_modifier = FALSE
                          AND p.display_name IN (
                              'Carnesecchi',
                              'Svilar',
                              'Maignan',
                              'Stankovic F.',
                              'Dimarco',
                              'Mancini',
                              'Paz N.',
                              'McTominay'
                          )
                        ORDER BY
                            profile.role_code,
                            profile.auction_role_rank
                        """)
                        .query()
                        .listOfRows();

        for (Map<String, Object> row : rows) {
            System.out.println(row);
        }
    }
}
