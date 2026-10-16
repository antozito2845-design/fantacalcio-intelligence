package it.fantacalciointelligence.backend.auctionstrategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.auctionranking.AuctionRankingItemResponse;
import it.fantacalciointelligence.backend.auctionranking.AuctionRankingResponse;
import it.fantacalciointelligence.backend.auctionranking.IntelligentAuctionRankingService;
import it.fantacalciointelligence.backend.common.BadRequestException;

@Service
public class IntelligentAuctionStrategyService {

    private static final Map<String, Integer> REQUIRED_SLOTS =
            Map.of(
                    "P", 3,
                    "D", 8,
                    "C", 8,
                    "A", 6
            );

    private final IntelligentAuctionRankingService rankingService;

    public IntelligentAuctionStrategyService(
            IntelligentAuctionRankingService rankingService
    ) {
        this.rankingService = rankingService;
    }

    @Transactional
    public AuctionStrategyResponse buildStrategy(
            String season,
            int budget,
            int teams,
            boolean defenseModifier
    ) {
        validateRequest(season, budget, teams);

        String normalizedSeason = season.trim();

        Map<String, BigDecimal> allocations =
                roleAllocations(defenseModifier);

        List<AuctionRoleStrategyResponse> roleStrategies =
                new ArrayList<>();

        List<String> globalWarnings =
                new ArrayList<>();

        int totalPlannedSpending = 0;

        for (String role : List.of("P", "D", "C", "A")) {
            AuctionRoleStrategyResponse roleStrategy =
                    buildRoleStrategy(
                            normalizedSeason,
                            budget,
                            teams,
                            defenseModifier,
                            role,
                            allocations.get(role)
                    );

            roleStrategies.add(roleStrategy);

            totalPlannedSpending +=
                    roleStrategy.plannedSpending();

            if (roleStrategy.primaryTargets().isEmpty()) {
                globalWarnings.add(
                        "Nessun obiettivo principale disponibile "
                                + "per il ruolo "
                                + role
                );
            }
        }

        int remainingBudget =
                budget - totalPlannedSpending;

        boolean withinBudget =
                remainingBudget >= 0;

        if (!withinBudget) {
            globalWarnings.add(
                    "Il piano supera il budget disponibile di "
                            + Math.abs(remainingBudget)
                            + " crediti"
            );
        }

        if (remainingBudget > budget * 0.20) {
            globalWarnings.add(
                    "Il piano conserva una quota elevata di budget: "
                            + "valutare un investimento maggiore "
                            + "sugli obiettivi principali"
            );
        }

        String summary =
                buildSummary(
                        budget,
                        totalPlannedSpending,
                        remainingBudget,
                        defenseModifier,
                        withinBudget
                );

        return new AuctionStrategyResponse(
                normalizedSeason,
                budget,
                teams,
                defenseModifier,
                25,
                totalPlannedSpending,
                remainingBudget,
                withinBudget,
                summary,
                List.copyOf(roleStrategies),
                List.copyOf(globalWarnings)
        );
    }

    private AuctionRoleStrategyResponse buildRoleStrategy(
            String season,
            int totalBudget,
            int teams,
            boolean defenseModifier,
            String role,
            BigDecimal allocation
    ) {
        int requiredSlots =
                REQUIRED_SLOTS.get(role);

        int allocatedBudget =
                allocation
                        .multiply(
                                BigDecimal.valueOf(totalBudget)
                        )
                        .setScale(
                                0,
                                RoundingMode.HALF_UP
                        )
                        .intValue();

        AuctionRankingResponse ranking =
                rankingService.findRanking(
                        season,
                        totalBudget,
                        teams,
                        defenseModifier,
                        role,
                        null,
                        null,
                        null,
                        true,
                        100
                );

        List<AuctionRankingItemResponse> players =
                ranking.players();

        List<AuctionStrategyPlayerResponse> primaryTargets =
                players.stream()
                        .filter(this::isPrimaryTarget)
                        .limit(primaryTargetCount(role))
                        .map(player ->
                                toStrategyPlayer(
                                        player,
                                        "OBIETTIVO PRINCIPALE"
                                )
                        )
                        .toList();

        List<AuctionStrategyPlayerResponse> alternatives =
                players.stream()
                        .filter(player ->
                                isValueAlternative(
                                        player,
                                        allocatedBudget,
                                        requiredSlots
                                )
                        )
                        .filter(player ->
                                !containsPlayer(
                                        primaryTargets,
                                        player.playerId()
                                )
                        )
                        .limit(alternativeCount(role))
                        .map(player ->
                                toStrategyPlayer(
                                        player,
                                        "ALTERNATIVA ECONOMICA"
                                )
                        )
                        .toList();

        List<AuctionStrategyPlayerResponse> bets =
                players.stream()
                        .filter(this::isHighPotentialBet)
                        .filter(player ->
                                !containsPlayer(
                                        primaryTargets,
                                        player.playerId()
                                )
                        )
                        .filter(player ->
                                !containsPlayer(
                                        alternatives,
                                        player.playerId()
                                )
                        )
                        .limit(2)
                        .map(player ->
                                toStrategyPlayer(
                                        player,
                                        "SCOMMESSA"
                                )
                        )
                        .toList();

        List<AuctionStrategyPlayerResponse> avoid =
                players.stream()
                        .filter(this::shouldAvoid)
                        .limit(3)
                        .map(player ->
                                toStrategyPlayer(
                                        player,
                                        "DA EVITARE"
                                )
                        )
                        .toList();

        int plannedSpending =
                calculatePlannedSpending(
                        allocatedBudget,
                        requiredSlots,
                        primaryTargets,
                        alternatives,
                        bets
                );

        int remainingRoleBudget =
                allocatedBudget - plannedSpending;

        List<String> warnings =
                buildRoleWarnings(
                        role,
                        requiredSlots,
                        allocatedBudget,
                        plannedSpending,
                        players,
                        primaryTargets
                );

        return new AuctionRoleStrategyResponse(
                role,
                requiredSlots,
                allocation
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(1, RoundingMode.HALF_UP),
                allocatedBudget,
                plannedSpending,
                remainingRoleBudget,
                primaryTargets,
                alternatives,
                bets,
                avoid,
                warnings
        );
    }

    private int calculatePlannedSpending(
            int allocatedBudget,
            int requiredSlots,
            List<AuctionStrategyPlayerResponse> primaryTargets,
            List<AuctionStrategyPlayerResponse> alternatives,
            List<AuctionStrategyPlayerResponse> bets
    ) {
        List<AuctionStrategyPlayerResponse> selected =
                new ArrayList<>();

        selected.addAll(primaryTargets);
        selected.addAll(alternatives);
        selected.addAll(bets);

        int spending =
                selected.stream()
                        .limit(requiredSlots)
                        .mapToInt(
                                AuctionStrategyPlayerResponse
                                        ::recommendedPrice
                        )
                        .sum();

        int selectedSlots =
                Math.min(
                        selected.size(),
                        requiredSlots
                );

        int minimumReserve =
                Math.max(
                        0,
                        requiredSlots - selectedSlots
                );

        spending += minimumReserve;

        return Math.min(
                spending,
                allocatedBudget
        );
    }

    private boolean isPrimaryTarget(
            AuctionRankingItemResponse player
    ) {
        return (
                "OBIETTIVO PRIORITARIO".equals(
                        player.finalRecommendation()
                )
                || "ACQUISTO CONSIGLIATO".equals(
                        player.finalRecommendation()
                )
        )
                && decimal(player.riskScore()) <= 60
                && decimal(player.starterProbability()) >= 60;
    }

    private boolean isValueAlternative(
            AuctionRankingItemResponse player,
            int allocatedBudget,
            int requiredSlots
    ) {
        double averageSlotBudget =
                allocatedBudget / (double) requiredSlots;

        return player.recommendedPrice()
                <= Math.max(
                        1,
                        Math.round(averageSlotBudget)
                )
                && decimal(player.riskScore()) < 70
                && !"ATTENDERE AGGIORNAMENTI".equals(
                        player.finalRecommendation()
                );
    }

    private boolean isHighPotentialBet(
            AuctionRankingItemResponse player
    ) {
        return decimal(player.potentialScore()) >= 70
                && player.recommendedPrice()
                        <= player.maximumPrice()
                && decimal(player.riskScore()) < 80
                && !"ATTENDERE AGGIORNAMENTI".equals(
                        player.finalRecommendation()
                );
    }

    private boolean shouldAvoid(
            AuctionRankingItemResponse player
    ) {
        return decimal(player.riskScore()) >= 70
                || "EVITARE O PRENDERE A BASSO COSTO".equals(
                        player.finalRecommendation()
                )
                || "ATTENDERE AGGIORNAMENTI".equals(
                        player.finalRecommendation()
                );
    }

    private AuctionStrategyPlayerResponse toStrategyPlayer(
            AuctionRankingItemResponse player,
            String category
    ) {
        return new AuctionStrategyPlayerResponse(
                player.playerId(),
                player.playerName(),
                player.club(),
                player.fantasyRole(),
                category,
                player.intelligenceScore(),
                player.potentialScore(),
                player.riskScore(),
                player.starterProbability(),
                player.minimumPrice(),
                player.recommendedPrice(),
                player.maximumPrice(),
                player.finalRecommendation(),
                player.strengths(),
                player.risks()
        );
    }

    private List<String> buildRoleWarnings(
            String role,
            int requiredSlots,
            int allocatedBudget,
            int plannedSpending,
            List<AuctionRankingItemResponse> availablePlayers,
            List<AuctionStrategyPlayerResponse> primaryTargets
    ) {
        List<String> warnings =
                new ArrayList<>();

        if (availablePlayers.size() < requiredSlots) {
            warnings.add(
                    "Disponibili soltanto "
                            + availablePlayers.size()
                            + " giocatori per "
                            + requiredSlots
                            + " slot richiesti"
            );
        }

        if (primaryTargets.isEmpty()) {
            warnings.add(
                    "Nessun profilo di priorità alta individuato"
            );
        }

        if (plannedSpending > allocatedBudget) {
            warnings.add(
                    "La spesa prevista supera il budget del ruolo"
            );
        }

        if ("D".equals(role)
                && allocatedBudget > 0
                && requiredSlots == 8) {
            warnings.add(
                    "Mantenere almeno due difensori affidabili "
                            + "per proteggere il modificatore"
            );
        }

        return List.copyOf(warnings);
    }

    private boolean containsPlayer(
            List<AuctionStrategyPlayerResponse> players,
            Long playerId
    ) {
        return players.stream()
                .anyMatch(player ->
                        player.playerId().equals(playerId)
                );
    }

    private int primaryTargetCount(String role) {
        return switch (role) {
            case "P" -> 1;
            case "D" -> 3;
            case "C" -> 3;
            case "A" -> 3;
            default -> 1;
        };
    }

    private int alternativeCount(String role) {
        return switch (role) {
            case "P" -> 2;
            case "D" -> 4;
            case "C" -> 4;
            case "A" -> 3;
            default -> 2;
        };
    }

    private Map<String, BigDecimal> roleAllocations(
            boolean defenseModifier
    ) {
        Map<String, BigDecimal> allocations =
                new LinkedHashMap<>();

        allocations.put("P", BigDecimal.valueOf(0.08));

        allocations.put(
                "D",
                BigDecimal.valueOf(
                        defenseModifier
                                ? 0.22
                                : 0.17
                )
        );

        allocations.put(
                "C",
                BigDecimal.valueOf(
                        defenseModifier
                                ? 0.27
                                : 0.30
                )
        );

        allocations.put(
                "A",
                BigDecimal.valueOf(
                        defenseModifier
                                ? 0.43
                                : 0.45
                )
        );

        return allocations;
    }

    private String buildSummary(
            int budget,
            int plannedSpending,
            int remainingBudget,
            boolean defenseModifier,
            boolean withinBudget
    ) {
        String modifierText =
                defenseModifier
                        ? "con modificatore difesa"
                        : "senza modificatore difesa";

        String status =
                withinBudget
                        ? "Il piano rispetta il budget."
                        : "Il piano supera il budget.";

        return "Strategia per lega "
                + modifierText
                + ": spesa prevista "
                + plannedSpending
                + " crediti su "
                + budget
                + ", residuo previsto "
                + remainingBudget
                + ". "
                + status;
    }

    private double decimal(BigDecimal value) {
        return value == null
                ? 0
                : value.doubleValue();
    }

    private void validateRequest(
            String season,
            int budget,
            int teams
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
    }
}