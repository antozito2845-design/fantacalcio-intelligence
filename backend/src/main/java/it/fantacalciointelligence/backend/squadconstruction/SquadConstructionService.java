package it.fantacalciointelligence.backend.squadconstruction;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.auction.AuctionApiPriceService;
import it.fantacalciointelligence.backend.auction.AuctionPriceResponse;
import it.fantacalciointelligence.backend.squadconstruction.SquadTargetScorer.Candidate;

@Service
public class SquadConstructionService {
    private final JdbcClient jdbcClient;
    private final AuctionApiPriceService priceService;
    private final DynamicBudgetEngine budgetEngine;
    private final SquadTargetScorer scorer;

    public SquadConstructionService(JdbcClient jdbcClient, AuctionApiPriceService priceService,
            DynamicBudgetEngine budgetEngine, SquadTargetScorer scorer) {
        this.jdbcClient = jdbcClient; this.priceService = priceService;
        this.budgetEngine = budgetEngine; this.scorer = scorer;
    }

    @Transactional(readOnly = true)
    public SquadConstructionResponse evaluate(SquadConstructionRequest request) {
        DynamicBudgetResponse budget = budgetEngine.calculate(request);
        String phase = DynamicBudgetEngine.normalizeRole(request.currentPhaseRole());
        Map<Long, AuctionPriceResponse> prices = priceService.calculateAll(request.season().trim(),
                request.initialBudget(), request.teams(), request.defenseModifier()).stream()
                .collect(Collectors.toMap(AuctionPriceResponse::playerId, Function.identity()));
        List<BoughtPlayerRequest> boughtPlayers = request.playersAlreadyBought() == null
                ? List.of()
                : request.playersAlreadyBought();
        Set<Long> bought = boughtPlayers.stream()
                .map(BoughtPlayerRequest::playerId).collect(Collectors.toSet());

        List<Candidate> candidates = loadCandidates(
                request.season().trim(),
                phase
        );
        Map<Long, Candidate> candidatesById = candidates.stream()
                .collect(Collectors.toMap(
                        Candidate::playerId,
                        Function.identity()
                ));
        RosterProfileContext roster = buildRosterProfile(
                boughtPlayers,
                candidatesById,
                prices,
                budget,
                phase,
                request.defenseModifier()
        );

        List<SquadTargetResponse> targets = candidates.stream()
                .filter(c -> !bought.contains(c.playerId()))
                .filter(c -> prices.containsKey(c.playerId())) // explicit B51 INSUFFICIENT_EVIDENCE exclusion
                .map(c -> withPrice(c, prices.get(c.playerId()).recommendedPrice()))
                .map(c -> scorer.score(c, budget, phase, roster))
                .sorted(Comparator.comparing(SquadTargetResponse::auctionPriorityScore).reversed()
                        .thenComparing(SquadTargetResponse::playerId))
                .limit(Math.min(100, Math.max(1, request.limit())))
                .toList();

        List<String> needs = missingProfileNeeds(
                roster,
                phase,
                budget
        );
        List<String> alternatives = "A".equals(phase)
                ? List.of("ONE_TOP_BALANCED", "THREE_SEMI_TOP", "VALUE_HEAVY")
                : List.of("BALANCED", "VALUE_HEAVY");
        return new SquadConstructionResponse(request.season().trim(), request.teams(), request.initialBudget(),
                request.remainingBudget(), phase, request.defenseModifier(), budget, needs, targets,
                targets.stream().filter(t -> t.valueScore().doubleValue() >= 70).toList(),
                targets.stream().filter(t -> t.upsideScore() != null && t.upsideScore().doubleValue() >= 70).toList(),
                targets.stream().filter(t -> "EVITARE SOVRAPREZZO".equals(t.recommendation())).toList(), alternatives);
    }

    private RosterProfileContext buildRosterProfile(
            List<BoughtPlayerRequest> boughtPlayers,
            Map<Long, Candidate> candidates,
            Map<Long, AuctionPriceResponse> prices,
            DynamicBudgetResponse budget,
            String phase,
            boolean defenseModifier
    ) {
        Map<String, Integer> covered = new LinkedHashMap<>();
        int unknown = 0;
        int remaining = budget.slotsRemainingByRole().get(phase);
        int averageSlotBudget = Math.max(
                1,
                budget.remainingBudgetTargets().get(phase)
                        / Math.max(1, remaining)
        );

        for (BoughtPlayerRequest bought : boughtPlayers) {
            if (!phase.equals(
                    DynamicBudgetEngine.normalizeRole(bought.role())
            )) {
                continue;
            }
            Candidate candidate = candidates.get(bought.playerId());
            if (candidate == null) {
                unknown++;
                continue;
            }
            AuctionPriceResponse market = prices.get(bought.playerId());
            int evidencePrice = market == null
                    ? bought.pricePaid()
                    : market.recommendedPrice();
            String category = scorer.classifyForRoster(
                    candidate,
                    evidencePrice,
                    averageSlotBudget
            );
            covered.merge(category, 1, Integer::sum);
        }

        return new RosterProfileContext(
                Map.copyOf(covered),
                unknown,
                remaining,
                defenseModifier
        );
    }

    List<String> missingProfileNeeds(
            RosterProfileContext roster,
            String phase,
            DynamicBudgetResponse budget
    ) {
        if (roster.slotsRemaining() == 0) {
            return List.of("REPARTO_COMPLETO");
        }

        List<String> needs = new ArrayList<>();
        int premium = roster.covered("PREMIUM")
                + roster.covered("SEMI_TOP");
        int depth = roster.covered("VALUE_PICK")
                + roster.covered("STARTER")
                + roster.covered("LOW_COST");

        if (premium == 0) {
            needs.add(
                    "MANCA_PREMIUM_O_SEMI_TOP"
                            + ("D".equals(phase)
                                    && roster.defenseModifier()
                                            ? "_PRIORITA_MODIFICATORE"
                                            : "")
            );
        }
        else {
            needs.add("QUALITA_PREMIUM_COPERTA");
        }
        if (roster.covered("BONUS_SPECIALIST") == 0
                && !"P".equals(phase)) {
            needs.add("MANCA_BONUS_SPECIALIST");
        }
        if (depth < Math.max(1, roster.slotsRemaining() / 2)) {
            needs.add("SERVE_VALUE_DEPTH");
        }
        if (roster.covered("UPSIDE_BET") == 0
                && roster.slotsRemaining() >= 2) {
            needs.add("SERVE_UPSIDE");
        }
        if (roster.covered("LOW_COST")
                > Math.max(2, budget.slotsFilledByRole().get(phase) / 2)) {
            needs.add("TROPPI_LOW_COST");
        }
        if (roster.unknownProfiles() > 0) {
            needs.add("PROFILI_ACQUISTATI_UNKNOWN="
                    + roster.unknownProfiles());
        }
        return List.copyOf(needs);
    }

    private Candidate withPrice(Candidate c, int price) {
        return new Candidate(c.playerId(), c.playerName(), c.club(), c.role(), c.qualityScore(), c.b50Confidence(),
                price, c.potentialScore(), c.starterProbability(), c.penaltyTaker(), c.setPieceTaker(),
                c.cornerTaker(), c.freeKickTaker(), c.attackingInvolvement(), c.realPosition());
    }

    private List<Candidate> loadCandidates(String season, String role) {
        return jdbcClient.sql("""
                SELECT p.id, p.display_name, c.name club, r.fantasy_role,
                       v.player_value_score, v.data_confidence_score,
                       e.potential_score, t.starter_probability, t.penalty_taker,
                       t.set_piece_taker, t.corner_taker, t.free_kick_taker,
                       t.attacking_involvement, t.real_position
                FROM players p
                JOIN player_registrations pr ON pr.player_id=p.id AND pr.registration_status='ACTIVE'
                JOIN seasons s ON s.id=pr.season_id AND s.code=:season
                JOIN clubs c ON c.id=pr.club_id
                JOIN season_clubs season_club ON season_club.season_id=s.id
                     AND season_club.club_id=c.id
                JOIN player_roles r ON r.player_id=p.id AND r.season_id=s.id
                     AND r.source='FANTACALCIO' AND r.fantasy_role=:role
                JOIN player_value_profiles v ON v.player_id=p.id AND v.reference_season_id=s.id
                     AND v.algorithm_version='B50_FANTASY_VALUE_V1' AND v.ranking_status='RANKED'
                LEFT JOIN player_evaluations e ON e.player_id=p.id AND e.season_id=s.id
                LEFT JOIN player_tactical_profiles t ON t.player_id=p.id AND t.season_id=s.id
                WHERE p.active=TRUE
                ORDER BY p.id
                """).param("season", season).param("role", role).query((rs, n) -> new Candidate(
                        rs.getLong("id"), rs.getString("display_name"), rs.getString("club"), rs.getString("fantasy_role"),
                        rs.getDouble("player_value_score"), rs.getDouble("data_confidence_score"), 1,
                        number(rs.getObject("potential_score")), number(rs.getObject("starter_probability")),
                        bool(rs.getObject("penalty_taker")), bool(rs.getObject("set_piece_taker")),
                        bool(rs.getObject("corner_taker")), bool(rs.getObject("free_kick_taker")),
                        number(rs.getObject("attacking_involvement")), rs.getString("real_position"))).list();
    }
    private Double number(Object value) { return value instanceof Number n ? n.doubleValue() : null; }
    private Boolean bool(Object value) { return value instanceof Boolean b ? b : null; }
}
