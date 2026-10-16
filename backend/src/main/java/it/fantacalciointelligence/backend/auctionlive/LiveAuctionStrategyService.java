package it.fantacalciointelligence.backend.auctionlive;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.auctionranking.AuctionRankingItemResponse;
import it.fantacalciointelligence.backend.auctionranking.AuctionRankingResponse;
import it.fantacalciointelligence.backend.auctionranking.IntelligentAuctionRankingService;
import it.fantacalciointelligence.backend.common.BadRequestException;
import it.fantacalciointelligence.backend.squad.SquadPlayerResponse;
import it.fantacalciointelligence.backend.squad.SquadResponse;
import it.fantacalciointelligence.backend.squad.SquadRoleSummaryResponse;
import it.fantacalciointelligence.backend.squad.SquadService;

@Service
public class LiveAuctionStrategyService {

    private static final List<String> ROLE_ORDER =
            List.of("P", "D", "C", "A");

    private final JdbcClient jdbcClient;
    private final SquadService squadService;
    private final IntelligentAuctionRankingService rankingService;

    public LiveAuctionStrategyService(
            JdbcClient jdbcClient,
            SquadService squadService,
            IntelligentAuctionRankingService rankingService
    ) {
        this.jdbcClient = jdbcClient;
        this.squadService = squadService;
        this.rankingService = rankingService;
    }

    @Transactional
    public LiveAuctionStrategyResponse buildActiveStrategy() {
        SquadResponse squad =
                squadService.findActiveSquad();

        return buildStrategy(squad.id());
    }

    @Transactional
    public LiveAuctionStrategyResponse buildStrategy(
            Long squadId
    ) {
        if (squadId == null || squadId <= 0) {
            throw new BadRequestException(
                    "L'identificativo della rosa non è valido"
            );
        }

        SquadResponse squad =
                squadService.findSquad(squadId);

        LeagueConfiguration configuration =
                findLeagueConfiguration(squadId);

        int remainingSlots =
                Math.max(
                        0,
                        squad.maximumPlayers()
                                - squad.totalPlayers()
                );

        int globalMaximumBid =
                calculateMaximumBid(
                        squad.remainingBudget(),
                        remainingSlots
                );

        Set<Long> acquiredPlayerIds =
                squad.players()
                        .stream()
                        .map(SquadPlayerResponse::playerId)
                        .collect(
                                java.util.stream.Collectors
                                        .toCollection(HashSet::new)
                        );

        Map<String, Integer> roleBudgets =
                allocateRemainingBudget(
                        squad,
                        configuration.defenseModifier()
                );

        List<LiveAuctionRoleStrategyResponse> roles =
                new ArrayList<>();

        List<String> globalWarnings =
                new ArrayList<>();

        for (String role : ROLE_ORDER) {
            SquadRoleSummaryResponse roleSummary =
                    squad.roles().get(role);

            if (roleSummary == null) {
                globalWarnings.add(
                        "Configurazione mancante per il ruolo "
                                + role
                );

                continue;
            }

            roles.add(
                    buildRoleStrategy(
                            squad,
                            configuration,
                            role,
                            roleSummary,
                            roleBudgets.getOrDefault(role, 0),
                            globalMaximumBid,
                            acquiredPlayerIds
                    )
            );
        }

        boolean squadComplete =
                remainingSlots == 0;

        if (squadComplete) {
            globalWarnings.add(
                    "La rosa è completa: non sono necessari "
                            + "altri acquisti"
            );
        }

        if (!squadComplete
                && squad.remainingBudget() < remainingSlots) {

            globalWarnings.add(
                    "Il budget residuo non garantisce un credito "
                            + "per ogni slot libero"
            );
        }

        if (!squadComplete
                && globalMaximumBid <= 1) {

            globalWarnings.add(
                    "Budget critico: il rilancio massimo globale "
                            + "è limitato a un credito"
            );
        }

        String summary =
                buildSummary(
                        squad,
                        remainingSlots,
                        globalMaximumBid,
                        squadComplete
                );

        return new LiveAuctionStrategyResponse(
                squad.id(),
                squad.name(),
                squad.season(),
                configuration.teams(),
                configuration.defenseModifier(),
                squad.initialBudget(),
                squad.spentBudget(),
                squad.remainingBudget(),
                squad.totalPlayers(),
                squad.maximumPlayers(),
                remainingSlots,
                globalMaximumBid,
                squadComplete,
                summary,
                List.copyOf(roles),
                List.copyOf(globalWarnings)
        );
    }

    private LiveAuctionRoleStrategyResponse buildRoleStrategy(
            SquadResponse squad,
            LeagueConfiguration configuration,
            String role,
            SquadRoleSummaryResponse roleSummary,
            int allocatedBudget,
            int globalMaximumBid,
            Set<Long> acquiredPlayerIds
    ) {
        int remainingRoleSlots =
                roleSummary.remaining();

        boolean roleComplete =
                remainingRoleSlots == 0;

        List<String> warnings =
                new ArrayList<>();

        if (roleComplete) {
            warnings.add(
                    "Ruolo completo: nessun altro acquisto richiesto"
            );

            return new LiveAuctionRoleStrategyResponse(
                    role,
                    roleSummary.current(),
                    roleSummary.maximum(),
                    0,
                    0,
                    0,
                    true,
                    List.of(),
                    List.copyOf(warnings)
            );
        }

        int roleMaximumBid =
                Math.min(
                        globalMaximumBid,
                        Math.max(
                                1,
                                allocatedBudget
                                        - Math.max(
                                                0,
                                                remainingRoleSlots - 1
                                        )
                        )
                );

        AuctionRankingResponse ranking =
                rankingService.findRanking(
                        squad.season(),
                        squad.initialBudget(),
                        configuration.teams(),
                        configuration.defenseModifier(),
                        role,
                        null,
                        null,
                        null,
                        true,
                        100
                );

        List<LiveAuctionTargetResponse> targets =
                ranking.players()
                        .stream()
                        .filter(player ->
                                !acquiredPlayerIds.contains(
                                        player.playerId()
                                )
                        )
                        .map(player ->
                                toLiveTarget(
                                        player,
                                        roleMaximumBid
                                )
                        )
                        .filter(target ->
                                target.liveMaximumBid() >= 1
                        )
                        .limit(
                                Math.min(
                                        10,
                                        Math.max(
                                                4,
                                                remainingRoleSlots * 2
                                        )
                                )
                        )
                        .toList();

        if (targets.isEmpty()) {
            warnings.add(
                    "Nessun obiettivo disponibile compatibile "
                            + "con rosa e budget"
            );
        }

        if (roleMaximumBid <= 1) {
            warnings.add(
                    "Disponibile soltanto il rilancio minimo "
                            + "per questo ruolo"
            );
        }

        if (allocatedBudget < remainingRoleSlots) {
            warnings.add(
                    "Budget del ruolo insufficiente per coprire "
                            + "tutti gli slot residui"
            );
        }

        if ("D".equals(role)
                && configuration.defenseModifier()
                && remainingRoleSlots > 0) {

            warnings.add(
                    "Con il modificatore attivo privilegiare "
                            + "titolarità e affidabilità difensiva"
            );
        }

        return new LiveAuctionRoleStrategyResponse(
                role,
                roleSummary.current(),
                roleSummary.maximum(),
                remainingRoleSlots,
                allocatedBudget,
                roleMaximumBid,
                false,
                targets,
                List.copyOf(warnings)
        );
    }

    private LiveAuctionTargetResponse toLiveTarget(
            AuctionRankingItemResponse player,
            int roleMaximumBid
    ) {
        int liveMaximumBid =
                Math.min(
                        player.maximumPrice(),
                        roleMaximumBid
                );

        String category =
                liveCategory(
                        player,
                        liveMaximumBid
                );

        return new LiveAuctionTargetResponse(
                player.playerId(),
                player.playerName(),
                player.club(),
                player.fantasyRole(),
                safeDecimal(player.intelligenceScore()),
                safeDecimal(player.reliabilityScore()),
                safeDecimal(player.potentialScore()),
                safeDecimal(player.riskScore()),
                safeDecimal(player.starterProbability()),
                player.minimumPrice(),
                player.recommendedPrice(),
                player.maximumPrice(),
                liveMaximumBid,
                category,
                player.finalRecommendation(),
                player.strengths(),
                player.risks()
        );
    }

    private String liveCategory(
            AuctionRankingItemResponse player,
            int liveMaximumBid
    ) {
        if (liveMaximumBid < player.minimumPrice()) {
            return "FUORI BUDGET";
        }

        if (player.recommendedPrice() <= liveMaximumBid
                && (
                        "OBIETTIVO PRIORITARIO".equals(
                                player.finalRecommendation()
                        )
                        || "ACQUISTO CONSIGLIATO".equals(
                                player.finalRecommendation()
                        )
                )) {

            return "OBIETTIVO LIVE";
        }

        if (player.recommendedPrice() <= liveMaximumBid) {
            return "ALTERNATIVA COMPATIBILE";
        }

        return "SOLO A PREZZO RIDOTTO";
    }

    private Map<String, Integer> allocateRemainingBudget(
            SquadResponse squad,
            boolean defenseModifier
    ) {
        Map<String, BigDecimal> baseWeights =
                roleWeights(defenseModifier);

        Map<String, BigDecimal> effectiveWeights =
                new LinkedHashMap<>();

        BigDecimal totalEffectiveWeight =
                BigDecimal.ZERO;

        int totalRemainingSlots = 0;

        for (String role : ROLE_ORDER) {
            SquadRoleSummaryResponse summary =
                    squad.roles().get(role);

            if (summary == null || summary.remaining() <= 0) {
                effectiveWeights.put(
                        role,
                        BigDecimal.ZERO
                );

                continue;
            }

            totalRemainingSlots += summary.remaining();

            BigDecimal completionFactor =
                    BigDecimal.valueOf(
                            summary.remaining()
                    ).divide(
                            BigDecimal.valueOf(
                                    summary.maximum()
                            ),
                            6,
                            RoundingMode.HALF_UP
                    );

            BigDecimal effectiveWeight =
                    baseWeights.get(role)
                            .multiply(completionFactor);

            effectiveWeights.put(
                    role,
                    effectiveWeight
            );

            totalEffectiveWeight =
                    totalEffectiveWeight.add(
                            effectiveWeight
                    );
        }

        Map<String, Integer> result =
                new LinkedHashMap<>();

        if (totalRemainingSlots == 0) {
            for (String role : ROLE_ORDER) {
                result.put(role, 0);
            }

            return result;
        }

        int mandatoryReserve =
                totalRemainingSlots;

        int discretionaryBudget =
                Math.max(
                        0,
                        squad.remainingBudget()
                                - mandatoryReserve
                );

        int remainingToAllocate =
                squad.remainingBudget();

        List<String> openRoles =
                ROLE_ORDER.stream()
                        .filter(role ->
                                squad.roles().get(role) != null
                                        && squad.roles()
                                                .get(role)
                                                .remaining() > 0
                        )
                        .toList();

        for (int index = 0;
                index < openRoles.size();
                index++) {

            String role =
                    openRoles.get(index);

            SquadRoleSummaryResponse summary =
                    squad.roles().get(role);

            int allocation;

            if (index == openRoles.size() - 1) {
                allocation =
                        remainingToAllocate;
            }
            else {
                int reserve =
                        summary.remaining();

                int discretionaryShare =
                        totalEffectiveWeight.signum() == 0
                                ? 0
                                : effectiveWeights.get(role)
                                        .divide(
                                                totalEffectiveWeight,
                                                8,
                                                RoundingMode.HALF_UP
                                        )
                                        .multiply(
                                                BigDecimal.valueOf(
                                                        discretionaryBudget
                                                )
                                        )
                                        .setScale(
                                                0,
                                                RoundingMode.DOWN
                                        )
                                        .intValue();

                allocation =
                        Math.min(
                                remainingToAllocate,
                                reserve + discretionaryShare
                        );
            }

            result.put(role, allocation);

            remainingToAllocate -= allocation;
        }

        for (String role : ROLE_ORDER) {
            result.putIfAbsent(role, 0);
        }

        return result;
    }

    private Map<String, BigDecimal> roleWeights(
            boolean defenseModifier
    ) {
        Map<String, BigDecimal> weights =
                new LinkedHashMap<>();

        weights.put(
                "P",
                BigDecimal.valueOf(0.08)
        );

        weights.put(
                "D",
                BigDecimal.valueOf(
                        defenseModifier
                                ? 0.22
                                : 0.17
                )
        );

        weights.put(
                "C",
                BigDecimal.valueOf(
                        defenseModifier
                                ? 0.27
                                : 0.30
                )
        );

        weights.put(
                "A",
                BigDecimal.valueOf(
                        defenseModifier
                                ? 0.43
                                : 0.45
                )
        );

        return weights;
    }

    private int calculateMaximumBid(
            int remainingBudget,
            int remainingSlots
    ) {
        if (remainingSlots <= 0) {
            return 0;
        }

        return Math.max(
                1,
                remainingBudget
                        - Math.max(
                                0,
                                remainingSlots - 1
                        )
        );
    }

    private LeagueConfiguration findLeagueConfiguration(
            Long squadId
    ) {
        return jdbcClient.sql("""
                SELECT
                    league.teams_count,
                    league.defense_modifier

                FROM fantasy_squads squad

                JOIN league_configurations league
                    ON league.id =
                        squad.league_configuration_id

                WHERE squad.id = :squadId
                """)
                .param("squadId", squadId)
                .query((rs, rowNumber) ->
                        new LeagueConfiguration(
                                rs.getInt(
                                        "teams_count"
                                ),
                                rs.getBoolean(
                                        "defense_modifier"
                                )
                        )
                )
                .single();
    }

    private String buildSummary(
            SquadResponse squad,
            int remainingSlots,
            int globalMaximumBid,
            boolean squadComplete
    ) {
        if (squadComplete) {
            return "Rosa completa con "
                    + squad.totalPlayers()
                    + " giocatori e "
                    + squad.remainingBudget()
                    + " crediti residui.";
        }

        return "Rosa composta da "
                + squad.totalPlayers()
                + " giocatori su "
                + squad.maximumPlayers()
                + ". Restano "
                + remainingSlots
                + " slot e "
                + squad.remainingBudget()
                + " crediti. Rilancio massimo sostenibile: "
                + globalMaximumBid
                + " crediti.";
    }

    private BigDecimal safeDecimal(
            BigDecimal value
    ) {
        return value == null
                ? BigDecimal.ZERO
                : value;
    }

    private record LeagueConfiguration(
            int teams,
            boolean defenseModifier
    ) {
    }
}