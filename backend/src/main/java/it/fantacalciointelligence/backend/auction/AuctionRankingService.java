package it.fantacalciointelligence.backend.auction;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuctionRankingService {

    private final JdbcClient jdbcClient;
    private final AuctionApiPriceService auctionApiPriceService;

    public AuctionRankingService(
            JdbcClient jdbcClient,
            AuctionApiPriceService auctionApiPriceService
    ) {
        this.jdbcClient = jdbcClient;
        this.auctionApiPriceService = auctionApiPriceService;
    }

    @Transactional(readOnly = true)
    public List<AuctionRankingResponse> calculateRanking(
            String season,
            int budget,
            int teams,
            boolean defenseModifier,
            String role,
            String club,
            int limit
    ) {
        int safeLimit = Math.min(
                Math.max(limit, 1),
                500
        );

        StringBuilder sql = new StringBuilder("""
                SELECT DISTINCT
                    p.id
                FROM players p

                JOIN player_reconciliation_results reconciliation
                    ON reconciliation.player_id = p.id

                JOIN seasons s
                    ON s.id = reconciliation.season_id

                JOIN player_registrations registration
                    ON registration.player_id = p.id
                    AND registration.season_id = s.id

                JOIN clubs c
                    ON c.id = registration.club_id

                JOIN season_clubs season_club
                    ON season_club.season_id = s.id
                    AND season_club.club_id = c.id

                JOIN player_roles player_role
                    ON player_role.player_id = p.id
                    AND player_role.season_id = s.id
                    AND player_role.source = 'FANTACALCIO'

                WHERE s.code = :season
                  AND p.active = TRUE
                  AND registration.registration_status = 'ACTIVE'

                """);

        if (role != null && !role.isBlank()) {
            sql.append("""
                    AND player_role.fantasy_role = :role
                    """);
        }

        if (club != null && !club.isBlank()) {
            sql.append("""
                    AND (
                        LOWER(c.slug) = LOWER(:club)
                        OR LOWER(c.name) = LOWER(:club)
                    )
                    """);
        }

        sql.append("""
                ORDER BY p.id
                """);

        var query = jdbcClient
                .sql(sql.toString())
                .param("season", season);

        if (role != null && !role.isBlank()) {
            query = query.param(
                    "role",
                    role.trim().toUpperCase()
            );
        }

        if (club != null && !club.isBlank()) {
            query = query.param(
                    "club",
                    club.trim()
            );
        }

        Set<Long> allowedPlayerIds =
                new HashSet<>(
                        query
                                .query(Long.class)
                                .list()
                );

        if (allowedPlayerIds.isEmpty()) {
            return List.of();
        }

        List<AuctionPriceResponse> prices =
                auctionApiPriceService.calculateAll(
                                season,
                                budget,
                                teams,
                                defenseModifier
                        )
                        .stream()
                        .filter(price ->
                                allowedPlayerIds.contains(
                                        price.playerId()
                                )
                        )
                        .sorted(
                                Comparator
                                        .comparing(
                                                AuctionPriceResponse
                                                        ::calculationScore
                                        )
                                        .reversed()
                                        .thenComparing(
                                                AuctionPriceResponse
                                                        ::recommendedPrice,
                                                Comparator.reverseOrder()
                                        )
                                        .thenComparing(
                                                AuctionPriceResponse
                                                        ::playerName
                                        )
                        )
                        .limit(safeLimit)
                        .toList();

        return java.util.stream.IntStream
                .range(
                        0,
                        prices.size()
                )
                .mapToObj(index -> {
                    AuctionPriceResponse price =
                            prices.get(index);

                    return new AuctionRankingResponse(
                            index + 1,
                            price.playerId(),
                            price.playerName(),
                            price.club(),
                            price.role(),
                            price.minimumPrice(),
                            price.recommendedPrice(),
                            price.maximumPrice(),
                            price.budgetPercentage(),
                            price.calculationScore(),
                            price.recommendation()
                    );
                })
                .toList();
    }
}
