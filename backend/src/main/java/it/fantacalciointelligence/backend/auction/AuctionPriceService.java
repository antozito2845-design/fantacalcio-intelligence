package it.fantacalciointelligence.backend.auction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuctionPriceService {

    private final JdbcClient jdbcClient;

    public AuctionPriceService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional
    public AuctionPriceResponse calculate(
            Long playerId,
            String seasonCode,
            int budget,
            int teams,
            boolean defenseModifier
    ) {
        validateConfiguration(budget, teams);

        Map<String, Object> data = jdbcClient.sql("""
                SELECT
                    p.id,
                    p.display_name,
                    c.name AS club_name,
                    s.id AS season_id,

                    role.fantasy_role,

                    evaluation.projected_fantasy_average,
                    evaluation.projected_appearances,
                    evaluation.projected_starts,
                    evaluation.projected_goals,
                    evaluation.projected_assists,
                    evaluation.reliability_score,
                    evaluation.potential_score,
                    evaluation.risk_score,
                    evaluation.value_score,

                    tactical.starter_probability,
                    tactical.attacking_involvement,
                    tactical.penalty_taker,
                    tactical.set_piece_taker,

                    market.quotation,
                    market.fantasy_market_value

                FROM players p

                JOIN player_registrations registration
                    ON registration.player_id = p.id

                JOIN seasons s
                    ON s.id = registration.season_id

                JOIN clubs c
                    ON c.id = registration.club_id

                JOIN season_clubs season_club
                    ON season_club.season_id = s.id
                    AND season_club.club_id = c.id

                JOIN player_roles role
                    ON role.player_id = p.id
                    AND role.season_id = s.id
                    AND role.source = 'FANTACALCIO'

                LEFT JOIN player_evaluations evaluation
                    ON evaluation.player_id = p.id
                    AND evaluation.season_id = s.id

                LEFT JOIN player_tactical_profiles tactical
                    ON tactical.player_id = p.id
                    AND tactical.season_id = s.id

                LEFT JOIN player_market_values market
                    ON market.player_id = p.id
                    AND market.season_id = s.id
                    AND market.source = 'FANTACALCIO'

                WHERE p.id = :playerId
                  AND s.code = :seasonCode
                """)
                .param("playerId", playerId)
                .param("seasonCode", seasonCode)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Giocatore non trovato"
                        )
                );

        String role = stringValue(data, "fantasy_role");

        double quotation = number(
                data,
                "quotation",
                0
        );

        double fantasyMarketValue = number(
                data,
                "fantasy_market_value",
                0
        );

        double reliability = number(data, "reliability_score", 50);
        double potential = number(data, "potential_score", 50);
        double risk = number(data, "risk_score", 50);
        double value = number(data, "value_score", 50);

        double fantasyAverage = number(
                data,
                "projected_fantasy_average",
                6
        );

        double starterProbability = number(
                data,
                "starter_probability",
                50
        );

        double attackingInvolvement = number(
                data,
                "attacking_involvement",
                50
        );

        double projectedGoals = number(
                data,
                "projected_goals",
                0
        );

        double projectedAssists = number(
                data,
                "projected_assists",
                0
        );

        boolean penaltyTaker = booleanValue(
                data,
                "penalty_taker"
        );

        boolean setPieceTaker = booleanValue(
                data,
                "set_piece_taker"
        );

        double score =
                reliability * 0.18
                + potential * 0.22
                + value * 0.25
                + (100 - risk) * 0.15
                + starterProbability * 0.12
                + Math.min(attackingInvolvement, 100) * 0.08;

        score += Math.max(0, fantasyAverage - 6) * 8;
        score += projectedGoals * goalWeight(role);
        score += projectedAssists * assistWeight(role);

        if (penaltyTaker) {
            score += 7;
        }

        if (setPieceTaker) {
            score += 3;
        }

        double marketReference =
                maximumAllowedForRole(
                        1000,
                        role,
                        defenseModifier
                );

        double marketStrength = 0;

        if (fantasyMarketValue > 0
                && marketReference > 0) {

            marketStrength = Math.min(
                    100,
                    fantasyMarketValue
                            * 100
                            / marketReference
            );

            score =
                    score * 0.45
                    + marketStrength * 0.55;

        } else if (quotation > 0) {

            double quotationReference =
                    quotationReference(role);

            marketStrength = Math.min(
                    100,
                    quotation
                            * 100
                            / quotationReference
            );

            score =
                    score * 0.60
                    + marketStrength * 0.40;
        }

        score = Math.max(0, Math.min(score, 100));

        double roleAllocation = roleAllocation(
                role,
                defenseModifier
        );

        int roleSlots = roleSlots(role);

        double roleBudget = budget * roleAllocation;
        double slotBasePrice = roleBudget / roleSlots;

        double strengthMultiplier =
                0.40 + (score / 60.0);

        double leagueMultiplier =
                0.82 + (teams / 8.0) * 0.18;

        double scarcityMultiplier =
                scarcityMultiplier(role, teams);

        double calculatedPrice =
                slotBasePrice
                * strengthMultiplier
                * leagueMultiplier
                * scarcityMultiplier;

        if (fantasyMarketValue > 0) {
            double officialMarketPrice =
                    fantasyMarketValue
                    * budget
                    / 1000.0;

            calculatedPrice =
                    officialMarketPrice * 0.75
                    + calculatedPrice * 0.25;
        }

        int recommendedPrice = clamp(
                (int) Math.round(calculatedPrice),
                1,
                budget
        );

        double minimumFactor =
                risk >= 65 ? 0.68 : 0.78;

        double maximumFactor =
                score >= 95 ? 1.25 : 1.16;

        int minimumPrice = Math.max(
                1,
                (int) Math.round(
                        recommendedPrice * minimumFactor
                )
        );

        int maximumPrice = Math.max(
                recommendedPrice,
                (int) Math.round(
                        recommendedPrice * maximumFactor
                )
        );

        maximumPrice = Math.min(
                maximumPrice,
                maximumAllowedForRole(
                        budget,
                        role,
                        defenseModifier
                )
        );

        recommendedPrice = Math.min(
                recommendedPrice,
                maximumPrice
        );

        minimumPrice = Math.min(
                minimumPrice,
                recommendedPrice
        );

        BigDecimal budgetPercentage = BigDecimal
                .valueOf(
                        recommendedPrice * 100.0 / budget
                )
                .setScale(3, RoundingMode.HALF_UP);

        BigDecimal calculationScore = BigDecimal
                .valueOf(score)
                .setScale(2, RoundingMode.HALF_UP);

        String recommendation = recommendation(
                score,
                risk,
                value
        );

        String explanation = explanation(
                role,
                score,
                starterProbability,
                projectedGoals,
                projectedAssists,
                risk,
                penaltyTaker,
                quotation,
                fantasyMarketValue
        );

        Long seasonId =
                ((Number) data.get("season_id")).longValue();

        jdbcClient.sql("""
                INSERT INTO auction_price_snapshots (
                    player_id,
                    season_id,
                    league_teams,
                    total_budget,
                    defense_modifier,
                    fantasy_role,
                    minimum_price,
                    recommended_price,
                    maximum_price,
                    budget_percentage,
                    calculation_score
                )
                VALUES (
                    :playerId,
                    :seasonId,
                    :teams,
                    :budget,
                    :defenseModifier,
                    :role,
                    :minimumPrice,
                    :recommendedPrice,
                    :maximumPrice,
                    :budgetPercentage,
                    :calculationScore
                )
                """)
                .param("playerId", playerId)
                .param("seasonId", seasonId)
                .param("teams", teams)
                .param("budget", budget)
                .param("defenseModifier", defenseModifier)
                .param("role", role)
                .param("minimumPrice", minimumPrice)
                .param("recommendedPrice", recommendedPrice)
                .param("maximumPrice", maximumPrice)
                .param("budgetPercentage", budgetPercentage)
                .param("calculationScore", calculationScore)
                .update();

        return new AuctionPriceResponse(
                playerId,
                stringValue(data, "display_name"),
                stringValue(data, "club_name"),
                role,
                teams,
                budget,
                defenseModifier,
                minimumPrice,
                recommendedPrice,
                maximumPrice,
                budgetPercentage,
                calculationScore,
                recommendation,
                explanation
        );
    }

    private void validateConfiguration(
            int budget,
            int teams
    ) {
        if (budget < 150 || budget > 1000) {
            throw new IllegalArgumentException(
                    "Il budget deve essere compreso tra 150 e 1000"
            );
        }

        if (teams < 6 || teams > 12) {
            throw new IllegalArgumentException(
                    "Le squadre devono essere comprese tra 6 e 12"
            );
        }
    }

    private double roleAllocation(
            String role,
            boolean defenseModifier
    ) {
        return switch (role) {
            case "P" -> 0.08;
            case "D" -> defenseModifier ? 0.22 : 0.17;
            case "C" -> defenseModifier ? 0.27 : 0.30;
            case "A" -> defenseModifier ? 0.43 : 0.45;
            default -> throw new IllegalArgumentException(
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
            default -> throw new IllegalArgumentException(
                    "Ruolo non valido"
            );
        };
    }

    private double scarcityMultiplier(
            String role,
            int teams
    ) {
        double teamImpact = 1 + ((teams - 8) * 0.035);

        double roleImpact = switch (role) {
            case "P" -> 0.90;
            case "D" -> 0.95;
            case "C" -> 1.02;
            case "A" -> 1.12;
            default -> 1;
        };

        return teamImpact * roleImpact;
    }

    private int maximumAllowedForRole(
            int budget,
            String role,
            boolean defenseModifier
    ) {
        double maximumPercentage = switch (role) {
            case "P" -> 0.09;
            case "D" -> defenseModifier ? 0.13 : 0.09;
            case "C" -> 0.20;
            case "A" -> 0.38;
            default -> 1;
        };

        return Math.max(
                1,
                (int) Math.round(
                        budget * maximumPercentage
                )
        );
    }

    private double quotationReference(String role) {
        return switch (role) {
            case "P" -> 20;
            case "D" -> 30;
            case "C" -> 40;
            case "A" -> 50;
            default -> 50;
        };
    }

    private double goalWeight(String role) {
        return switch (role) {
            case "P" -> 0.8;
            case "D" -> 2.2;
            case "C" -> 1.4;
            case "A" -> 1.1;
            default -> 1;
        };
    }

    private double assistWeight(String role) {
        return switch (role) {
            case "P" -> 0.7;
            case "D" -> 1.3;
            case "C" -> 1.2;
            case "A" -> 0.9;
            default -> 1;
        };
    }

    private String recommendation(
            double score,
            double risk,
            double value
    ) {
        if (score >= 92 && value >= 75 && risk <= 45) {
            return "PRIORITÀ ALTA";
        }

        if (score >= 75 && risk <= 60) {
            return "CONSIGLIATO";
        }

        if (score >= 60) {
            return "DA VALUTARE";
        }

        return "ACQUISTO RISCHIOSO";
    }

    private String explanation(
            String role,
            double score,
            double starterProbability,
            double goals,
            double assists,
            double risk,
            boolean penaltyTaker,
            double quotation,
            double fantasyMarketValue
    ) {
        String penalties = penaltyTaker
                ? " È inoltre indicato come possibile rigorista."
                : "";

        String marketInformation =
                fantasyMarketValue > 0
                        ? String.format(
                                " Quotazione %.0f, FVM %.0f.",
                                quotation,
                                fantasyMarketValue
                        )
                        : "";

        return String.format(
                "Valutazione %.1f/100, titolarita prevista %.0f%%, "
                        + "%.0f gol e %.0f assist stimati. "
                        + "Rischio %.0f/100. Ruolo di listone %s.%s%s",
                score,
                starterProbability,
                goals,
                assists,
                risk,
                role,
                penalties,
                marketInformation
        );
    }

    private int clamp(
            int value,
            int minimum,
            int maximum
    ) {
        return Math.max(
                minimum,
                Math.min(value, maximum)
        );
    }

    private String stringValue(
            Map<String, Object> row,
            String key
    ) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private double number(
            Map<String, Object> row,
            String key,
            double defaultValue
    ) {
        Object value = row.get(key);

        if (value instanceof Number number) {
            return number.doubleValue();
        }

        return defaultValue;
    }

    private boolean booleanValue(
            Map<String, Object> row,
            String key
    ) {
        Object value = row.get(key);

        return value instanceof Boolean
                && (Boolean) value;
    }
}
