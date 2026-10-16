package it.fantacalciointelligence.backend.auction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class AuctionPriceModelService {

    public static final String ALGORITHM_VERSION =
            "B51_AUCTION_PRICE_V1";

    public static final String B50_VERSION =
            "B50_FANTASY_VALUE_V1";

    private static final double MARKET_PERCENTILE_WEIGHT = 0.50;
    private static final double MARKET_STRENGTH_WEIGHT = 0.50;

    private final JdbcClient jdbcClient;

    public AuctionPriceModelService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<AuctionPriceModelResult> calculateAll(
            String seasonCode,
            int budget,
            int teams,
            boolean defenseModifier
    ) {
        validateConfiguration(
                budget,
                teams
        );

        List<PlayerInput> players =
                loadPlayers(seasonCode);

        if (players.isEmpty()) {
            throw new IllegalArgumentException(
                    "Nessun giocatore disponibile per la stagione "
                            + seasonCode
            );
        }

        Map<String, List<PlayerInput>> byRole =
                groupByRole(players);

        List<AuctionPriceModelResult> results =
                new ArrayList<>();

        for (String role : List.of("P", "D", "C", "A")) {

            List<PlayerInput> rolePlayers =
                    byRole.getOrDefault(
                            role,
                            List.of()
                    );

            if (rolePlayers.isEmpty()) {
                continue;
            }

            results.addAll(
                    calculateRole(
                            role,
                            rolePlayers,
                            budget,
                            teams,
                            defenseModifier
                    )
            );
        }

        return results;
    }

    private List<AuctionPriceModelResult> calculateRole(
            String role,
            List<PlayerInput> players,
            int budget,
            int teams,
            boolean defenseModifier
    ) {
        List<PlayerInput> rankedPlayers =
                players.stream()
                        .filter(player ->
                                "RANKED".equals(
                                        player.rankingStatus()
                                )
                        )
                        .filter(player ->
                                player.b50RolePercentile()
                                        != null
                        )
                        .toList();

        List<PlayerInput> insufficientPlayers =
                players.stream()
                        .filter(player ->
                                !"RANKED".equals(
                                        player.rankingStatus()
                                )
                        )
                        .toList();

        Map<Long, Double> fvmPercentiles =
                calculateFvmPercentiles(
                        rankedPlayers
                );

        Double maxFvm =
                rankedPlayers.stream()
                        .map(PlayerInput::fantasyMarketValue)
                        .filter(value ->
                                value != null
                        )
                        .max(Double::compareTo)
                        .orElse(null);

        List<ScoredPlayer> scored =
                new ArrayList<>();

        for (PlayerInput player : rankedPlayers) {

            Double fvmPercentile =
                    fvmPercentiles.get(
                            player.playerId()
                    );

            Double fvmStrength = null;
            Double marketSignal = null;

            double auctionScore;

            if (
                    player.fantasyMarketValue() == null
                    || maxFvm == null
            ) {
                auctionScore =
                        player.b50RolePercentile();

            } else {
                /*
                 * Important:
                 *
                 * FVM = 0 is VALID market evidence.
                 * It is NOT equivalent to NULL.
                 */
                fvmStrength =
                        maxFvm > 0
                                ? (
                                        Math.log1p(
                                                player.fantasyMarketValue()
                                        )
                                        / Math.log1p(maxFvm)
                                        * 100.0
                                )
                                : 0.0;

                marketSignal =
                        fvmPercentile
                                * MARKET_PERCENTILE_WEIGHT
                                + fvmStrength
                                * MARKET_STRENGTH_WEIGHT;

                double b50Weight =
                        b50Weight(role);

                double marketWeight =
                        1.0 - b50Weight;

                auctionScore =
                        player.b50RolePercentile()
                                * b50Weight
                                + marketSignal
                                * marketWeight;
            }

            scored.add(
                    new ScoredPlayer(
                            player,
                            round2(fvmPercentile),
                            round2(fvmStrength),
                            round2(marketSignal),
                            auctionScore
                    )
            );
        }

        scored.sort(
                Comparator
                        .comparing(
                                ScoredPlayer::auctionScore
                        )
                        .reversed()
                        .thenComparing(
                                item ->
                                        item.player()
                                                .playerId()
                        )
        );

        int marketSlots =
                teams * roleSlots(role);

        List<ScoredPlayer> contenders =
                scored.stream()
                        .limit(marketSlots)
                        .toList();

        double leagueRoleBudget =
                budget
                        * teams
                        * roleAllocation(
                                role,
                                defenseModifier
                        );

        double budgetFactor =
                budget / 1000.0;

        double teamFactor =
                1.0
                        + (
                                (teams - 8)
                                        * 0.05
                        );

        Map<Long, Double> provisionalPrices =
                new HashMap<>();

        for (ScoredPlayer player : contenders) {

            double rawPrice =
                    interpolatePrice(
                            role,
                            player.auctionScore()
                    );

            rawPrice *= budgetFactor;
            rawPrice *= teamFactor;

            provisionalPrices.put(
                    player.player()
                            .playerId(),
                    Math.max(
                            1.0,
                            rawPrice
                    )
            );
        }

        double provisionalTotal =
                provisionalPrices
                        .values()
                        .stream()
                        .mapToDouble(Double::doubleValue)
                        .sum();

        boolean budgetCapApplied =
                provisionalTotal > leagueRoleBudget;

        if (budgetCapApplied) {

            int floors =
                    provisionalPrices.size();

            double distributableBudget =
                    Math.max(
                            0,
                            leagueRoleBudget
                                    - floors
                    );

            double rawExtra =
                    provisionalPrices
                            .values()
                            .stream()
                            .mapToDouble(price ->
                                    Math.max(
                                            0,
                                            price - 1
                                    )
                            )
                            .sum();

            double scale =
                    rawExtra > 0
                            ? distributableBudget
                                    / rawExtra
                            : 0;

            provisionalPrices.replaceAll(
                    (playerId, price) ->
                            1
                                    + Math.max(
                                            0,
                                            price - 1
                                    )
                                    * scale
            );
        }

        Map<Long, Integer> recommendedPrices =
                new HashMap<>();

        /*
         * Players outside the auction-market slots still receive
         * the minimum actionable price of 1 credit.
         */
        for (ScoredPlayer scoredPlayer : scored) {
            recommendedPrices.put(
                    scoredPlayer.player()
                            .playerId(),
                    1
            );
        }

        if (budgetCapApplied) {

            int integerBudgetTarget =
                    (int) Math.floor(
                            leagueRoleBudget
                                    + 0.000000001
                    );

            List<PriceRemainder> remainders =
                    new ArrayList<>();

            int assignedCredits = 0;

            for (ScoredPlayer contender : contenders) {

                Long playerId =
                        contender.player()
                                .playerId();

                double exactPrice =
                        provisionalPrices.get(
                                playerId
                        );

                int floorPrice =
                        Math.max(
                                1,
                                (int) Math.floor(
                                        exactPrice
                                )
                        );

                recommendedPrices.put(
                        playerId,
                        floorPrice
                );

                assignedCredits += floorPrice;

                remainders.add(
                        new PriceRemainder(
                                playerId,
                                exactPrice
                                        - floorPrice,
                                contender.auctionScore()
                        )
                );
            }

            int creditsToDistribute =
                    Math.max(
                            0,
                            integerBudgetTarget
                                    - assignedCredits
                    );

            remainders.sort(
                    Comparator
                            .comparing(
                                    PriceRemainder::fraction
                            )
                            .reversed()
                            .thenComparing(
                                    PriceRemainder::auctionScore,
                                    Comparator.reverseOrder()
                            )
                            .thenComparing(
                                    PriceRemainder::playerId
                            )
            );

            int distributableCredits =
                    Math.min(
                            creditsToDistribute,
                            remainders.size()
                    );

            for (
                    int index = 0;
                    index < distributableCredits;
                    index++
            ) {
                Long playerId =
                        remainders.get(index)
                                .playerId();

                recommendedPrices.computeIfPresent(
                        playerId,
                        (ignored, currentPrice) ->
                                currentPrice + 1
                );
            }

        } else {

            for (ScoredPlayer contender : contenders) {

                Long playerId =
                        contender.player()
                                .playerId();

                int roundedPrice =
                        Math.max(
                                1,
                                (int) Math.round(
                                        provisionalPrices.get(
                                                playerId
                                        )
                                )
                        );

                recommendedPrices.put(
                        playerId,
                        roundedPrice
                );
            }
        }

        List<AuctionPriceModelResult> results =
                new ArrayList<>();

        int position = 0;
        int auctionRank = 0;
        Double previousAuctionScore = null;

        for (ScoredPlayer scoredPlayer : scored) {

            position++;

            if (
                    previousAuctionScore == null
                    || Double.compare(
                            scoredPlayer.auctionScore(),
                            previousAuctionScore
                    ) != 0
            ) {
                auctionRank = position;
            }

            previousAuctionScore =
                    scoredPlayer.auctionScore();

            PlayerInput player =
                    scoredPlayer.player();

            int recommendedPrice =
                    recommendedPrices.getOrDefault(
                            player.playerId(),
                            1
                    );

            PriceRange range =
                    calculateRange(
                            recommendedPrice,
                            player.dataConfidenceScore(),
                            player.fantasyMarketValue()
                                    != null
                    );

            double auctionPercentile =
                    competitionPercentile(
                            auctionRank,
                            scored.size()
                    );

            results.add(
                    new AuctionPriceModelResult(
                            player.playerId(),
                            player.playerName(),
                            player.club(),
                            player.role(),
                            player.b50RoleRank(),
                            round2(
                                    player.b50RolePercentile()
                            ),
                            round2(
                                    player.dataConfidenceScore()
                            ),
                            player.quotation(),
                            player.fantasyMarketValue(),
                            scoredPlayer.fvmRolePercentile(),
                            scoredPlayer.fvmStrength(),
                            scoredPlayer.marketSignal(),
                            round2(
                                    scoredPlayer.auctionScore()
                            ),
                            auctionRank,
                            round2(
                                    auctionPercentile
                            ),
                            "PRICED",
                            range.minimumPrice(),
                            recommendedPrice,
                            range.maximumPrice(),
                            BigDecimal.valueOf(
                                            recommendedPrice
                                                    * 100.0
                                                    / budget
                                    )
                                    .setScale(
                                            3,
                                            RoundingMode.HALF_UP
                                    )
                    )
            );

        }

        for (PlayerInput player : insufficientPlayers) {

            results.add(
                    new AuctionPriceModelResult(
                            player.playerId(),
                            player.playerName(),
                            player.club(),
                            player.role(),
                            null,
                            null,
                            round2(
                                    player.dataConfidenceScore()
                            ),
                            player.quotation(),
                            player.fantasyMarketValue(),
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            "INSUFFICIENT_EVIDENCE",
                            null,
                            null,
                            null,
                            null
                    )
            );
        }

        return results;
    }

    private List<PlayerInput> loadPlayers(
            String seasonCode
    ) {
        return jdbcClient.sql("""
                SELECT
                    player.id AS player_id,
                    player.display_name,
                    club.name AS club_name,
                    role.fantasy_role,

                    value_profile.role_rank,
                    value_profile.role_percentile,
                    value_profile.data_confidence_score,
                    value_profile.ranking_status,

                    market.quotation,
                    market.fantasy_market_value

                FROM player_value_profiles value_profile

                JOIN seasons season
                    ON season.id =
                        value_profile.reference_season_id

                JOIN players player
                    ON player.id =
                        value_profile.player_id

                JOIN player_registrations registration
                    ON registration.player_id =
                        player.id
                    AND registration.season_id =
                        season.id
                    AND registration.registration_status =
                        'ACTIVE'

                JOIN clubs club
                    ON club.id =
                        registration.club_id

                JOIN season_clubs season_club
                    ON season_club.season_id = season.id
                    AND season_club.club_id = club.id

                JOIN player_roles role
                    ON role.player_id =
                        player.id
                    AND role.season_id =
                        season.id
                    AND role.source =
                        'FANTACALCIO'

                LEFT JOIN player_market_values market
                    ON market.player_id =
                        player.id
                    AND market.season_id =
                        season.id
                    AND market.source =
                        'FANTACALCIO'

                WHERE season.code = :seasonCode

                  AND value_profile.algorithm_version =
                        :b50Version

                  AND player.active = TRUE

                ORDER BY
                    role.fantasy_role,
                    value_profile.role_rank NULLS LAST,
                    player.id
                """)
                .param(
                        "seasonCode",
                        seasonCode
                )
                .param(
                        "b50Version",
                        B50_VERSION
                )
                .query(
                        (rs, rowNumber) ->
                                new PlayerInput(
                                        rs.getLong(
                                                "player_id"
                                        ),
                                        rs.getString(
                                                "display_name"
                                        ),
                                        rs.getString(
                                                "club_name"
                                        ),
                                        rs.getString(
                                                "fantasy_role"
                                        ),
                                        integerOrNull(
                                                rs.getObject(
                                                        "role_rank"
                                                )
                                        ),
                                        doubleOrNull(
                                                rs.getObject(
                                                        "role_percentile"
                                                )
                                        ),
                                        doubleOrNull(
                                                rs.getObject(
                                                        "data_confidence_score"
                                                )
                                        ),
                                        rs.getString(
                                                "ranking_status"
                                        ),
                                        doubleOrNull(
                                                rs.getObject(
                                                        "quotation"
                                                )
                                        ),
                                        doubleOrNull(
                                                rs.getObject(
                                                        "fantasy_market_value"
                                                )
                                        )
                                )
                )
                .list();
    }

    private Map<String, List<PlayerInput>> groupByRole(
            List<PlayerInput> players
    ) {
        Map<String, List<PlayerInput>> result =
                new HashMap<>();

        for (PlayerInput player : players) {
            result
                    .computeIfAbsent(
                            player.role(),
                            ignored ->
                                    new ArrayList<>()
                    )
                    .add(player);
        }

        return result;
    }

    private Map<Long, Double> calculateFvmPercentiles(
            List<PlayerInput> players
    ) {
        List<Double> values =
                players.stream()
                        .map(
                                PlayerInput
                                        ::fantasyMarketValue
                        )
                        .filter(value ->
                                value != null
                        )
                        .sorted()
                        .toList();

        Map<Long, Double> percentiles =
                new HashMap<>();

        int n = values.size();

        if (n == 0) {
            return percentiles;
        }

        for (PlayerInput player : players) {

            Double value =
                    player.fantasyMarketValue();

            if (value == null) {
                continue;
            }

            long lower =
                    values.stream()
                            .filter(item ->
                                    item < value
                            )
                            .count();

            long equal =
                    values.stream()
                            .filter(item ->
                                    Double.compare(
                                            item,
                                            value
                                    ) == 0
                            )
                            .count();

            double percentile;

            if (n == 1) {
                percentile = 100.0;

            } else {
                double averageRankZeroBased =
                        lower
                                + (
                                        equal - 1
                                ) / 2.0;

                percentile =
                        averageRankZeroBased
                                / (n - 1)
                                * 100.0;
            }

            percentiles.put(
                    player.playerId(),
                    percentile
            );
        }

        return percentiles;
    }

    private double b50Weight(String role) {
        return switch (role) {
            case "P" -> 0.55;
            case "D", "C", "A" -> 0.65;
            default ->
                    throw new IllegalArgumentException(
                            "Ruolo non valido: "
                                    + role
                    );
        };
    }

    private double roleAllocation(
            String role,
            boolean defenseModifier
    ) {
        if (defenseModifier) {
            return switch (role) {
                case "P" -> 0.07;
                case "D" -> 0.15;
                case "C" -> 0.23;
                case "A" -> 0.55;
                default ->
                        throw new IllegalArgumentException(
                                "Ruolo non valido"
                        );
            };
        }

        return switch (role) {
            case "P" -> 0.06;
            case "D" -> 0.10;
            case "C" -> 0.25;
            case "A" -> 0.59;
            default ->
                    throw new IllegalArgumentException(
                            "Ruolo non valido"
                    );
        };
    }

    private int roleSlots(String role) {
        return switch (role) {
            case "P" -> 3;
            case "D" -> 8;
            case "C" -> 8;
            case "A" -> 6;
            default ->
                    throw new IllegalArgumentException(
                            "Ruolo non valido"
                    );
        };
    }

    private double interpolatePrice(
            String role,
            double percentile
    ) {
        double[][] anchors =
                priceAnchors(role);

        if (percentile >= anchors[0][0]) {
            return anchors[0][1];
        }

        if (
                percentile
                        <= anchors[anchors.length - 1][0]
        ) {
            return anchors[
                    anchors.length - 1
            ][1];
        }

        for (int index = 0;
                index < anchors.length - 1;
                index++) {

            double highPercentile =
                    anchors[index][0];

            double highPrice =
                    anchors[index][1];

            double lowPercentile =
                    anchors[index + 1][0];

            double lowPrice =
                    anchors[index + 1][1];

            if (
                    percentile <= highPercentile
                    && percentile >= lowPercentile
            ) {
                double span =
                        highPercentile
                                - lowPercentile;

                if (span == 0) {
                    return highPrice;
                }

                double position =
                        (
                                percentile
                                        - lowPercentile
                        ) / span;

                return lowPrice
                        + position
                        * (
                                highPrice
                                        - lowPrice
                        );
            }
        }

        return 1.0;
    }

    private double[][] priceAnchors(String role) {
        return switch (role) {
            case "P" -> new double[][] {
                    {100, 70},
                    {97, 58},
                    {95, 50},
                    {90, 38},
                    {80, 22},
                    {70, 12},
                    {50, 5},
                    {30, 2},
                    {0, 1}
            };

            case "D" -> new double[][] {
                    {100, 180},
                    {98, 145},
                    {95, 105},
                    {90, 65},
                    {80, 30},
                    {70, 15},
                    {50, 6},
                    {30, 2},
                    {0, 1}
            };

            case "C" -> new double[][] {
                    {100, 500},
                    {98, 420},
                    {95, 330},
                    {90, 190},
                    {85, 95},
                    {80, 42},
                    {70, 12},
                    {60, 4},
                    {50, 2},
                    {0, 1}
            };

            case "A" -> new double[][] {
                    {100, 340},
                    {98, 310},
                    {95, 270},
                    {90, 220},
                    {80, 150},
                    {70, 95},
                    {50, 40},
                    {30, 12},
                    {0, 1}
            };

            default ->
                    throw new IllegalArgumentException(
                            "Ruolo non valido"
                    );
        };
    }

    private PriceRange calculateRange(
            double price,
            double confidence,
            boolean hasFvm
    ) {
        double lowFactor;
        double highFactor;

        if (confidence >= 90) {
            lowFactor = 0.90;
            highFactor = 1.10;

        } else if (confidence >= 80) {
            lowFactor = 0.85;
            highFactor = 1.15;

        } else if (confidence >= 70) {
            lowFactor = 0.80;
            highFactor = 1.20;

        } else {
            lowFactor = 0.72;
            highFactor = 1.28;
        }

        if (!hasFvm) {
            lowFactor -= 0.05;
            highFactor += 0.05;
        }

        int minimum =
                Math.max(
                        1,
                        (int) Math.round(
                                price * lowFactor
                        )
                );

        int maximum =
                Math.max(
                        (int) Math.round(price),
                        (int) Math.round(
                                price * highFactor
                        )
                );

        return new PriceRange(
                minimum,
                maximum
        );
    }

    private double competitionPercentile(
            int rank,
            int total
    ) {
        if (total <= 1) {
            return 100.0;
        }

        return (
                (total - rank)
                        * 100.0
                        / (total - 1)
        );
    }

    private void validateConfiguration(
            int budget,
            int teams
    ) {
        if (
                budget < 150
                || budget > 1000
        ) {
            throw new IllegalArgumentException(
                    "Il budget deve essere compreso tra 150 e 1000"
            );
        }

        if (
                teams < 6
                || teams > 12
        ) {
            throw new IllegalArgumentException(
                    "Le squadre devono essere comprese tra 6 e 12"
            );
        }
    }

    private static Integer integerOrNull(
            Object value
    ) {
        if (value instanceof Number number) {
            return number.intValue();
        }

        return null;
    }

    private static Double doubleOrNull(
            Object value
    ) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }

        return null;
    }

    private static Double round2(
            Double value
    ) {
        if (value == null) {
            return null;
        }

        return BigDecimal
                .valueOf(value)
                .setScale(
                        2,
                        RoundingMode.HALF_UP
                )
                .doubleValue();
    }

    private record PlayerInput(
            Long playerId,
            String playerName,
            String club,
            String role,
            Integer b50RoleRank,
            Double b50RolePercentile,
            Double dataConfidenceScore,
            String rankingStatus,
            Double quotation,
            Double fantasyMarketValue
    ) {
    }

    private record ScoredPlayer(
            PlayerInput player,
            Double fvmRolePercentile,
            Double fvmStrength,
            Double marketSignal,
            Double auctionScore
    ) {
    }

    private record PriceRemainder(
            Long playerId,
            double fraction,
            double auctionScore
    ) {
    }

    private record PriceRange(
            int minimumPrice,
            int maximumPrice
    ) {
    }

    public record AuctionPriceModelResult(
            Long playerId,
            String playerName,
            String club,
            String role,
            Integer b50RoleRank,
            Double b50RolePercentile,
            Double dataConfidenceScore,
            Double quotation,
            Double fantasyMarketValue,
            Double fvmRolePercentile,
            Double fvmStrength,
            Double marketSignal,
            Double auctionScore,
            Integer auctionRoleRank,
            Double auctionRolePercentile,
            String pricingStatus,
            Integer minimumPrice,
            Integer recommendedPrice,
            Integer maximumPrice,
            BigDecimal budgetPercentage
    ) {
    }
}
