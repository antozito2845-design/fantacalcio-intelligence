package it.fantacalciointelligence.backend.intelligence;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.auction.AuctionPriceResponse;
import it.fantacalciointelligence.backend.auction.AuctionApiPriceService;
import it.fantacalciointelligence.backend.common.BadRequestException;
import it.fantacalciointelligence.backend.common.ResourceNotFoundException;

@Service
public class PlayerIntelligenceService {

    private final JdbcClient jdbcClient;
    private final AuctionApiPriceService auctionPriceService;

    public PlayerIntelligenceService(
            JdbcClient jdbcClient,
            AuctionApiPriceService auctionPriceService
    ) {
        this.jdbcClient = jdbcClient;
        this.auctionPriceService = auctionPriceService;
    }

    @Transactional
    public PlayerIntelligenceResponse findByPlayer(
            Long playerId,
            String season,
            int budget,
            int teams,
            boolean defenseModifier
    ) {
        validateRequest(
                playerId,
                season,
                budget,
                teams
        );

        AuctionPriceResponse auction =
                auctionPriceService.calculate(
                        playerId,
                        season.trim(),
                        budget,
                        teams,
                        defenseModifier
                );

        return findByPlayer(
                playerId,
                season,
                budget,
                teams,
                defenseModifier,
                auction
        );
    }

    /**
     * Builds the intelligence profile with a price from a B51 market that
     * was already calculated by a batch consumer.
     */
    public PlayerIntelligenceResponse findByPlayer(
            Long playerId,
            String season,
            int budget,
            int teams,
            boolean defenseModifier,
            AuctionPriceResponse auction
    ) {
        validateRequest(playerId, season, budget, teams);

        if (auction == null || !playerId.equals(auction.playerId())) {
            throw new IllegalArgumentException(
                    "Prezzo asta B51 mancante o non coerente"
            );
        }

        Map<String, Object> data =
                findIntelligenceData(
                        playerId,
                        season.trim()
                );

        List<String> strengths =
                buildStrengths(data);

        List<String> risks =
                buildRisks(data);

        String finalRecommendation =
                calculateFinalRecommendation(
                        auction,
                        data,
                        risks
                );

        String summary =
                buildSummary(
                        data,
                        auction,
                        finalRecommendation
                );

        boolean foreignScoutingAvailable =
                data.get("scouting_recommendation") != null;

        return new PlayerIntelligenceResponse(
                longValue(data, "player_id"),
                stringValue(data, "display_name"),
                stringValue(data, "club_name"),
                stringValue(data, "season_code"),
                stringValue(data, "fantasy_role"),
                stringValue(data, "real_position"),
                stringValue(data, "tactical_role"),

                decimalValue(
                        data,
                        "projected_fantasy_average"
                ),
                integerValue(
                        data,
                        "projected_appearances"
                ),
                integerValue(
                        data,
                        "projected_starts"
                ),
                integerValue(data, "projected_goals"),
                integerValue(data, "projected_assists"),
                decimalValue(data, "reliability_score"),
                decimalValue(data, "potential_score"),
                decimalValue(data, "risk_score"),
                decimalValue(data, "value_score"),
                decimalValue(data, "starter_probability"),
                booleanValue(data, "penalty_taker"),
                booleanValue(data, "set_piece_taker"),

                stringValue(data, "development_trend"),
                decimalValue(data, "growth_score"),
                decimalValue(
                        data,
                        "decline_risk_score"
                ),
                decimalValue(
                        data,
                        "development_confidence"
                ),
                stringValue(
                        data,
                        "development_summary"
                ),

                foreignScoutingAvailable,
                stringValue(data, "previous_club"),
                stringValue(data, "previous_league"),
                stringValue(
                        data,
                        "previous_country_code"
                ),
                decimalValue(data, "adaptation_score"),
                stringValue(
                        data,
                        "scouting_recommendation"
                ),
                stringValue(data, "scouting_summary"),

                integerValue(
                        data,
                        "availability_matchday"
                ),
                stringValue(data, "availability_status"),
                decimalValue(
                        data,
                        "availability_probability"
                ),
                decimalValue(
                        data,
                        "starting_probability"
                ),
                stringValue(data, "injury_description"),
                localDateValue(
                        data,
                        "expected_return_date"
                ),

                auction.minimumPrice(),
                auction.recommendedPrice(),
                auction.maximumPrice(),
                auction.budgetPercentage(),
                auction.calculationScore(),

                strengths,
                risks,
                finalRecommendation,
                summary
        );
    }

    private Map<String, Object> findIntelligenceData(
            Long playerId,
            String season
    ) {
        return jdbcClient.sql("""
                SELECT
                    player.id AS player_id,
                    player.display_name,
                    club.name AS club_name,
                    season.code AS season_code,

                    role.fantasy_role,

                    tactical.real_position,
                    tactical.tactical_role,
                    tactical.starter_probability,
                    tactical.penalty_taker,
                    tactical.set_piece_taker,

                    evaluation.projected_fantasy_average,
                    evaluation.projected_appearances,
                    evaluation.projected_starts,
                    evaluation.projected_goals,
                    evaluation.projected_assists,
                    evaluation.reliability_score,
                    evaluation.potential_score,
                    evaluation.risk_score,
                    evaluation.value_score,

                    development.trend
                        AS development_trend,

                    development.growth_score,

                    development.decline_risk_score,

                    development.confidence_score
                        AS development_confidence,

                    development.analysis_summary
                        AS development_summary,

                    scouting.previous_club,
                    scouting.previous_league,
                    scouting.previous_country_code,
                    scouting.adaptation_score,

                    scouting.recommendation
                        AS scouting_recommendation,

                    scouting.scouting_summary,

                    availability.matchday
                        AS availability_matchday,

                    availability.status
                        AS availability_status,

                    availability.availability_probability,

                    availability.starting_probability,

                    availability.injury_description,

                    availability.expected_return_date

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

                LEFT JOIN player_tactical_profiles tactical
                    ON tactical.player_id = player.id
                    AND tactical.season_id = season.id

                LEFT JOIN player_evaluations evaluation
                    ON evaluation.player_id = player.id
                    AND evaluation.season_id = season.id

                LEFT JOIN player_development_assessments
                    development
                    ON development.player_id = player.id
                    AND development.target_season_id =
                        season.id

                LEFT JOIN foreign_player_scouting_reports
                    scouting
                    ON scouting.player_id = player.id
                    AND scouting.season_id = season.id

                LEFT JOIN LATERAL (
                    SELECT
                        player_availability.matchday,
                        player_availability.status,
                        player_availability
                            .availability_probability,
                        player_availability
                            .starting_probability,
                        player_availability
                            .injury_description,
                        player_availability
                            .expected_return_date

                    FROM player_availabilities
                        player_availability

                    WHERE player_availability.player_id =
                        player.id

                      AND player_availability.season_id =
                        season.id

                    ORDER BY
                        player_availability.matchday DESC,
                        player_availability.updated_at DESC

                    LIMIT 1
                ) availability ON TRUE

                WHERE player.id = :playerId
                  AND player.active = TRUE
                  AND season.code = :season
                """)
                .param("playerId", playerId)
                .param("season", season)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Giocatore non trovato o non "
                                        + "registrato nella stagione"
                        )
                );
    }

    private List<String> buildStrengths(
            Map<String, Object> data
    ) {
        List<String> strengths = new ArrayList<>();

        if (atLeast(data, "reliability_score", 75)) {
            strengths.add("Alta affidabilità");
        }

        if (atLeast(data, "potential_score", 80)) {
            strengths.add("Potenziale elevato");
        }

        if (atLeast(data, "value_score", 75)) {
            strengths.add("Buon rapporto qualità-prezzo");
        }

        if (atLeast(data, "starter_probability", 75)) {
            strengths.add("Elevata probabilità di titolarità");
        }

        if (Boolean.TRUE.equals(
                booleanValue(data, "penalty_taker")
        )) {
            strengths.add("Possibile rigorista");
        }

        if (Boolean.TRUE.equals(
                booleanValue(data, "set_piece_taker")
        )) {
            strengths.add("Possibile battitore di piazzati");
        }

        String trend =
                stringValue(data, "development_trend");

        if ("GROWING".equals(trend)) {
            strengths.add("Profilo in crescita");
        }

        String scouting =
                stringValue(
                        data,
                        "scouting_recommendation"
                );

        if ("HIGH_PRIORITY".equals(scouting)
                || "RECOMMENDED".equals(scouting)) {
            strengths.add(
                    "Scouting estero positivo"
            );
        }

        if (strengths.isEmpty()) {
            strengths.add(
                    "Profilo equilibrato da valutare nel contesto"
            );
        }

        return List.copyOf(strengths);
    }

    private List<String> buildRisks(
            Map<String, Object> data
    ) {
        List<String> risks = new ArrayList<>();

        if (atLeast(data, "risk_score", 60)) {
            risks.add("Rischio tecnico elevato");
        }

        if (below(data, "starter_probability", 55)) {
            risks.add("Titolarità incerta");
        }

        if ("DECLINING".equals(
                stringValue(
                        data,
                        "development_trend"
                )
        )) {
            risks.add("Possibile rendimento in calo");
        }

        String availability =
                stringValue(
                        data,
                        "availability_status"
                );

        if ("INJURED".equals(availability)) {
            risks.add("Giocatore attualmente infortunato");
        }
        else if ("SUSPENDED".equals(availability)) {
            risks.add("Giocatore squalificato");
        }
        else if ("UNAVAILABLE".equals(availability)) {
            risks.add("Giocatore indisponibile");
        }
        else if ("DOUBTFUL".equals(availability)) {
            risks.add("Disponibilità in dubbio");
        }

        String scouting =
                stringValue(
                        data,
                        "scouting_recommendation"
                );

        if ("HIGH_RISK".equals(scouting)) {
            risks.add(
                    "Elevato rischio di adattamento alla Serie A"
            );
        }

        return List.copyOf(risks);
    }

    private String calculateFinalRecommendation(
            AuctionPriceResponse auction,
            Map<String, Object> data,
            List<String> risks
    ) {
        double score =
                auction.calculationScore()
                        .doubleValue();

        String availability =
                stringValue(
                        data,
                        "availability_status"
                );

        if ("INJURED".equals(availability)
                || "UNAVAILABLE".equals(availability)) {
            return "ATTENDERE AGGIORNAMENTI";
        }

        if (score >= 85 && risks.size() <= 1) {
            return "OBIETTIVO PRIORITARIO";
        }

        if (score >= 70 && risks.size() <= 2) {
            return "ACQUISTO CONSIGLIATO";
        }

        if (score >= 55) {
            return "ACQUISTARE SOLO AL PREZZO GIUSTO";
        }

        return "EVITARE O PRENDERE A BASSO COSTO";
    }

    private String buildSummary(
            Map<String, Object> data,
            AuctionPriceResponse auction,
            String finalRecommendation
    ) {
        String playerName =
                stringValue(data, "display_name");

        String club =
                stringValue(data, "club_name");

        String role =
                stringValue(data, "fantasy_role");

        String realPosition =
                stringValue(data, "real_position");

        String positionText =
                realPosition == null
                        ? ""
                        : ", impiegato realmente come "
                                + realPosition;

        return playerName
                + " gioca nel "
                + club
                + " ed è listato "
                + role
                + positionText
                + ". Prezzo consigliato "
                + auction.recommendedPrice()
                + " crediti, con intervallo "
                + auction.minimumPrice()
                + "-"
                + auction.maximumPrice()
                + ". Valutazione finale: "
                + finalRecommendation
                + ".";
    }

    private boolean atLeast(
            Map<String, Object> data,
            String key,
            double threshold
    ) {
        BigDecimal value = decimalValue(data, key);

        return value != null
                && value.doubleValue() >= threshold;
    }

    private boolean below(
            Map<String, Object> data,
            String key,
            double threshold
    ) {
        BigDecimal value = decimalValue(data, key);

        return value != null
                && value.doubleValue() < threshold;
    }

    private String stringValue(
            Map<String, Object> data,
            String key
    ) {
        Object value = data.get(key);

        return value == null
                ? null
                : value.toString();
    }

    private Long longValue(
            Map<String, Object> data,
            String key
    ) {
        Object value = data.get(key);

        return value instanceof Number number
                ? number.longValue()
                : null;
    }

    private Integer integerValue(
            Map<String, Object> data,
            String key
    ) {
        Object value = data.get(key);

        return value instanceof Number number
                ? number.intValue()
                : null;
    }

    private BigDecimal decimalValue(
            Map<String, Object> data,
            String key
    ) {
        Object value = data.get(key);

        if (value instanceof BigDecimal decimal) {
            return decimal;
        }

        if (value instanceof Number number) {
            return BigDecimal.valueOf(
                    number.doubleValue()
            );
        }

        return null;
    }

    private Boolean booleanValue(
            Map<String, Object> data,
            String key
    ) {
        Object value = data.get(key);

        return value instanceof Boolean booleanValue
                ? booleanValue
                : null;
    }

    private java.time.LocalDate localDateValue(
            Map<String, Object> data,
            String key
    ) {
        Object value = data.get(key);

        return value instanceof java.time.LocalDate localDate
                ? localDate
                : null;
    }

    private void validateRequest(
            Long playerId,
            String season,
            int budget,
            int teams
    ) {
        if (playerId == null || playerId <= 0) {
            throw new BadRequestException(
                    "L'identificativo del giocatore non è valido"
            );
        }

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
    }
}
