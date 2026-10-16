package it.fantacalciointelligence.backend.auctionranking;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.common.BadRequestException;
import it.fantacalciointelligence.backend.auction.AuctionApiPriceService;
import it.fantacalciointelligence.backend.auction.AuctionPriceResponse;
import it.fantacalciointelligence.backend.intelligence.PlayerIntelligenceResponse;
import it.fantacalciointelligence.backend.intelligence.PlayerIntelligenceService;

@Service
public class IntelligentAuctionRankingService {

    private static final Set<String> ROLES =
            Set.of("P", "D", "C", "A");

    private static final Set<String> RECOMMENDATIONS =
            Set.of(
                    "OBIETTIVO PRIORITARIO",
                    "ACQUISTO CONSIGLIATO",
                    "ACQUISTARE SOLO AL PREZZO GIUSTO",
                    "ATTENDERE AGGIORNAMENTI",
                    "EVITARE O PRENDERE A BASSO COSTO"
            );

    private static final Set<String> UNAVAILABLE_STATUSES =
            Set.of(
                    "INJURED",
                    "UNAVAILABLE",
                    "SUSPENDED"
            );

    private final JdbcClient jdbcClient;
    private final PlayerIntelligenceService intelligenceService;
    private final AuctionApiPriceService auctionPriceService;

    public IntelligentAuctionRankingService(
            JdbcClient jdbcClient,
            PlayerIntelligenceService intelligenceService,
            AuctionApiPriceService auctionPriceService
    ) {
        this.jdbcClient = jdbcClient;
        this.intelligenceService = intelligenceService;
        this.auctionPriceService = auctionPriceService;
    }

    @Transactional
    public AuctionRankingResponse findRanking(
            String season,
            int budget,
            int teams,
            boolean defenseModifier,
            String role,
            String club,
            Integer maximumPrice,
            String recommendation,
            boolean excludeUnavailable,
            int limit
    ) {
        RankingRequest request =
                validateAndNormalize(
                        season,
                        budget,
                        teams,
                        role,
                        club,
                        maximumPrice,
                        recommendation,
                        limit
                );

        List<Long> playerIds =
                findEligiblePlayerIds(
                        request.season(),
                        request.role(),
                        request.club()
                );

        List<PlayerIntelligenceResponse> profiles =
                new ArrayList<>();

        if (playerIds.isEmpty()) {
            return emptyResponse(
                    request,
                    budget,
                    teams,
                    defenseModifier,
                    excludeUnavailable
            );
        }

        Map<Long, AuctionPriceResponse> pricesByPlayer =
                auctionPriceService.calculateAll(
                                request.season(),
                                budget,
                                teams,
                                defenseModifier
                        )
                        .stream()
                        .collect(Collectors.toMap(
                                AuctionPriceResponse::playerId,
                                Function.identity()
                        ));

        for (Long playerId : playerIds) {
            AuctionPriceResponse price =
                    pricesByPlayer.get(playerId);

            // An absent entry is explicitly INSUFFICIENT_EVIDENCE in B51.
            if (price == null) {
                continue;
            }

            PlayerIntelligenceResponse profile =
                    intelligenceService.findByPlayer(
                            playerId,
                            request.season(),
                            budget,
                            teams,
                            defenseModifier,
                            price
                    );

            if (matchesFilters(
                    profile,
                    request.maximumPrice(),
                    request.recommendation(),
                    excludeUnavailable
            )) {
                profiles.add(profile);
            }
        }

        profiles.sort(
                Comparator
                        .comparing(
                                PlayerIntelligenceResponse::auctionScore,
                                Comparator.nullsLast(
                                        Comparator.reverseOrder()
                                )
                        )
                        .thenComparing(
                                PlayerIntelligenceResponse
                                        ::recommendedPrice,
                                Comparator.reverseOrder()
                        )
                        .thenComparing(
                                PlayerIntelligenceResponse::playerName,
                                String.CASE_INSENSITIVE_ORDER
                        )
        );

        List<PlayerIntelligenceResponse> limitedProfiles =
                profiles.stream()
                        .limit(request.limit())
                        .toList();

        List<AuctionRankingItemResponse> items =
                new ArrayList<>();

        for (int index = 0;
                index < limitedProfiles.size();
                index++) {

            PlayerIntelligenceResponse profile =
                    limitedProfiles.get(index);

            items.add(
                    new AuctionRankingItemResponse(
                            index + 1,
                            profile.playerId(),
                            profile.playerName(),
                            profile.club(),
                            profile.fantasyRole(),
                            safeDecimal(profile.auctionScore()),
                            profile.minimumPrice(),
                            profile.recommendedPrice(),
                            profile.maximumPrice(),
                            safeDecimal(
                                    profile.budgetPercentage()
                            ),
                            safeDecimal(
                                    profile.reliabilityScore()
                            ),
                            safeDecimal(
                                    profile.potentialScore()
                            ),
                            safeDecimal(
                                    profile.riskScore()
                            ),
                            safeDecimal(
                                    profile.starterProbability()
                            ),
                            profile.availabilityStatus(),
                            profile.availabilityMatchday(),
                            profile.strengths(),
                            profile.risks(),
                            profile.finalRecommendation()
                    )
            );
        }

        return new AuctionRankingResponse(
                request.season(),
                budget,
                teams,
                defenseModifier,
                request.role(),
                request.club(),
                request.maximumPrice(),
                request.recommendation(),
                excludeUnavailable,
                items.size(),
                List.copyOf(items)
        );
    }

    private AuctionRankingResponse emptyResponse(
            RankingRequest request,
            int budget,
            int teams,
            boolean defenseModifier,
            boolean excludeUnavailable
    ) {
        return new AuctionRankingResponse(
                request.season(),
                budget,
                teams,
                defenseModifier,
                request.role(),
                request.club(),
                request.maximumPrice(),
                request.recommendation(),
                excludeUnavailable,
                0,
                List.of()
        );
    }

    private List<Long> findEligiblePlayerIds(
            String season,
            String role,
            String club
    ) {
        StringBuilder sql = new StringBuilder("""
                SELECT DISTINCT player.id

                FROM players player

                JOIN player_registrations registration
                    ON registration.player_id = player.id
                    AND registration.registration_status =
                        'ACTIVE'

                JOIN seasons season
                    ON season.id = registration.season_id

                JOIN clubs club
                    ON club.id = registration.club_id

                JOIN season_clubs season_club
                    ON season_club.season_id = season.id
                    AND season_club.club_id = club.id

                JOIN player_roles role
                    ON role.player_id = player.id
                    AND role.season_id = season.id
                    AND role.source = 'FANTACALCIO'


                WHERE player.active = TRUE
                  AND season.code = :season
                """);

        if (role != null) {
            sql.append("""
                    AND role.fantasy_role = :role
                    """);
        }

        if (club != null) {
            sql.append("""
                    AND (
                        LOWER(club.name) = :club
                        OR LOWER(club.slug) = :club
                    )
                    """);
        }

        sql.append("""
                ORDER BY player.id
                """);

        var query =
                jdbcClient.sql(sql.toString())
                        .param("season", season);

        if (role != null) {
            query = query.param("role", role);
        }

        if (club != null) {
            query = query.param("club", club);
        }

        return query
                .query(Long.class)
                .list();
    }

    private boolean matchesFilters(
            PlayerIntelligenceResponse profile,
            Integer maximumPrice,
            String recommendation,
            boolean excludeUnavailable
    ) {
        if (maximumPrice != null
                && profile.recommendedPrice() > maximumPrice) {
            return false;
        }

        if (recommendation != null
                && !recommendation.equals(
                        profile.finalRecommendation()
                )) {
            return false;
        }

        if (excludeUnavailable
                && profile.availabilityStatus() != null
                && UNAVAILABLE_STATUSES.contains(
                        profile.availabilityStatus()
                                .toUpperCase(Locale.ROOT)
                )) {
            return false;
        }

        return true;
    }

    private RankingRequest validateAndNormalize(
            String season,
            int budget,
            int teams,
            String role,
            String club,
            Integer maximumPrice,
            String recommendation,
            int limit
    ) {
        if (season == null || season.isBlank()) {
            throw new BadRequestException(
                    "La stagione è obbligatoria"
            );
        }

        if (budget < 150 || budget > 1000) {
            throw new BadRequestException(
                    "Il budget deve essere compreso tra 150 e 1000"
            );
        }

        if (teams < 6 || teams > 12) {
            throw new BadRequestException(
                    "Le squadre devono essere comprese tra 6 e 12"
            );
        }

        String normalizedRole =
                normalizeNullable(role);

        if (normalizedRole != null) {
            normalizedRole =
                    normalizedRole.toUpperCase(Locale.ROOT);

            if (!ROLES.contains(normalizedRole)) {
                throw new BadRequestException(
                        "Il ruolo deve essere P, D, C oppure A"
                );
            }
        }

        String normalizedClub =
                normalizeNullable(club);

        if (normalizedClub != null) {
            normalizedClub =
                    normalizedClub.toLowerCase(Locale.ROOT);
        }

        if (maximumPrice != null
                && maximumPrice < 1) {
            throw new BadRequestException(
                    "Il prezzo massimo deve essere positivo"
            );
        }

        if (maximumPrice != null
                && maximumPrice > budget) {
            throw new BadRequestException(
                    "Il prezzo massimo non può superare il budget"
            );
        }

        String normalizedRecommendation =
                normalizeNullable(recommendation);

        if (normalizedRecommendation != null) {
            normalizedRecommendation =
                    normalizedRecommendation
                            .toUpperCase(Locale.ROOT);

            if (!RECOMMENDATIONS.contains(
                    normalizedRecommendation
            )) {
                throw new BadRequestException(
                        "Raccomandazione non valida"
                );
            }
        }

        if (limit < 1 || limit > 100) {
            throw new BadRequestException(
                    "Il limite deve essere compreso tra 1 e 100"
            );
        }

        return new RankingRequest(
                season.trim(),
                normalizedRole,
                normalizedClub,
                maximumPrice,
                normalizedRecommendation,
                limit
        );
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private BigDecimal safeDecimal(
            BigDecimal value
    ) {
        return value == null
                ? BigDecimal.ZERO
                : value;
    }

    private record RankingRequest(
            String season,
            String role,
            String club,
            Integer maximumPrice,
            String recommendation,
            int limit
    ) {
    }
}
