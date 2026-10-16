package it.fantacalciointelligence.backend.liveauction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.calendarintelligence.CalendarCandidateService;
import it.fantacalciointelligence.backend.calendarintelligence.CalendarCandidateService.CalendarPlayerCandidate;
import it.fantacalciointelligence.backend.calendarintelligence.CalendarCombinationResult;
import it.fantacalciointelligence.backend.common.BadRequestException;
import it.fantacalciointelligence.backend.liveauction.LiveAuctionPlayerContextService.PlayerContext;
import it.fantacalciointelligence.backend.squadconstruction.BoughtPlayerRequest;
import it.fantacalciointelligence.backend.squadconstruction.SquadConstructionRequest;
import it.fantacalciointelligence.backend.squadconstruction.SquadConstructionResponse;
import it.fantacalciointelligence.backend.squadconstruction.SquadConstructionService;
import it.fantacalciointelligence.backend.squadconstruction.SquadTargetResponse;
import it.fantacalciointelligence.backend.strategicintelligence.StrategicIntelligenceProfile;

@Service
public class LiveAuctionDecisionService {
    private static final int MAX_ALTERNATIVES = 20;
    private final LiveAuctionPlayerContextService playerContextService;
    private final SquadConstructionService squadConstructionService;
    private final CalendarCandidateService candidateService;
    private final LiveAuctionStrategicBatchService strategicBatchService;
    private final LiveAuctionCalendarBatchService calendarBatchService;
    private final LiveAuctionDecisionEngine decisionEngine;

    public LiveAuctionDecisionService(LiveAuctionPlayerContextService playerContextService,
            SquadConstructionService squadConstructionService,
            CalendarCandidateService candidateService,
            LiveAuctionStrategicBatchService strategicBatchService,
            LiveAuctionCalendarBatchService calendarBatchService,
            LiveAuctionDecisionEngine decisionEngine) {
        this.playerContextService = playerContextService;
        this.squadConstructionService = squadConstructionService;
        this.candidateService = candidateService;
        this.strategicBatchService = strategicBatchService;
        this.calendarBatchService = calendarBatchService;
        this.decisionEngine = decisionEngine;
    }

    @Transactional(readOnly = true)
    public LiveAuctionDecisionResponse decide(LiveAuctionDecisionRequest request) {
        validate(request);
        request = canonical(request);
        String season = request.season().trim();
        String role = request.currentRole().trim().toUpperCase();
        PlayerContext currentPlayer = playerContextService.load(request.currentPlayerId(), season);
        if (!currentPlayer.active() || !currentPlayer.registrationActive() || !currentPlayer.clubActive()) {
            throw new BadRequestException("Il giocatore corrente non e' attivo nella stagione richiesta");
        }
        if (!role.equals(currentPlayer.role())) {
            throw new BadRequestException("Il ruolo corrente non coincide con il ruolo Fantacalcio del giocatore");
        }

        List<BoughtPlayerRequest> bought = request.playersAlreadyBought() == null
                ? List.of() : List.copyOf(request.playersAlreadyBought());
        SquadConstructionResponse construction = squadConstructionService.evaluate(new SquadConstructionRequest(
                season, request.leagueTeams(), request.totalBudget(), request.remainingBudget(),
                Boolean.TRUE.equals(request.defenseModifier()), role, request.rosterSlots(), bought, 100));
        Map<Long, SquadTargetResponse> targets = allTargets(construction);
        SquadTargetResponse current = targets.get(request.currentPlayerId());
        if (current == null) return insufficient(request, currentPlayer);

        Set<Long> excluded = new HashSet<>(request.excludedPlayerIds() == null
                ? List.of() : request.excludedPlayerIds());
        excluded.addAll(request.unavailablePlayerIds());
        bought.forEach(player -> excluded.add(player.playerId()));
        excluded.add(request.currentPlayerId());
        int limit = request.alternativeLimit() == null ? 10
                : Math.min(MAX_ALTERNATIVES, Math.max(1, request.alternativeLimit()));
        List<SquadTargetResponse> alternatives = targets.values().stream()
                .filter(target -> !excluded.contains(target.playerId()))
                .sorted(java.util.Comparator.comparing(SquadTargetResponse::auctionPriorityScore).reversed()
                        .thenComparing(SquadTargetResponse::playerId))
                .limit(limit).toList();
        List<SquadTargetResponse> evaluatedTargets = new ArrayList<>();
        evaluatedTargets.add(current);
        evaluatedTargets.addAll(alternatives);

        // One active-candidate query, one B56 observation batch and, when requested,
        // one B57 fixture-window batch for current player plus shortlisted alternatives.
        List<CalendarPlayerCandidate> active = candidateService.loadActive(season, role);
        Map<Long, StrategicIntelligenceProfile> strategic = strategicBatchService.load(season, evaluatedTargets);
        Map<Long, CalendarCombinationResult> calendars = request.currentMatchday() == null ? Map.of()
                : calendarBatchService.evaluate(season, request.currentMatchday(), horizon(request),
                        evaluatedTargets, active, bought, role);
        LiveAuctionCandidateEvaluation currentEvaluation = evaluation(current, strategic, calendars);
        List<LiveAuctionCandidateEvaluation> alternativeEvaluations = alternatives.stream()
                .map(target -> evaluation(target, strategic, calendars)).toList();
        return decisionEngine.decide(request, construction.budget(), construction.missingProfileNeeds(),
                currentEvaluation, alternativeEvaluations);
    }

    void validate(LiveAuctionDecisionRequest request) {
        if (request == null) throw new BadRequestException("Il contesto live e' obbligatorio");
        if (request.season() == null || request.season().isBlank())
            throw new BadRequestException("season e' obbligatoria");
        if (request.leagueTeams() == null || request.leagueTeams() < 6 || request.leagueTeams() > 12)
            throw new BadRequestException("leagueTeams deve essere compreso tra 6 e 12");
        if (request.totalBudget() == null || request.totalBudget() < 150 || request.totalBudget() > 1000)
            throw new BadRequestException("totalBudget deve essere compreso tra 150 e 1000");
        if (request.remainingBudget() == null || request.remainingBudget() < 0
                || request.remainingBudget() > request.totalBudget())
            throw new BadRequestException("remainingBudget non coerente");
        if (request.defenseModifier() == null)
            throw new BadRequestException("defenseModifier e' obbligatorio");
        if (request.currentRole() == null || !List.of("P", "D", "C", "A")
                .contains(request.currentRole().trim().toUpperCase()))
            throw new BadRequestException("currentRole deve essere P, D, C oppure A");
        int proposedBid = effectiveBid(request);
        if (request.currentPrice() != null && request.currentPrice() < 1)
            throw new BadRequestException("currentPrice deve essere almeno 1");
        if (request.currentPrice() != null && proposedBid < request.currentPrice())
            throw new BadRequestException("proposedBid non puo' essere inferiore a currentPrice");
        if (request.currentPlayerId() == null || request.currentPlayerId() <= 0)
            throw new BadRequestException("currentPlayerId non valido");
        if (proposedBid > request.remainingBudget())
            throw new BadRequestException("proposedBid supera il budget residuo");
        if (request.calendarHorizon() != null
                && (request.calendarHorizon() < 1 || request.calendarHorizon() > 10))
            throw new BadRequestException("calendarHorizon deve essere compreso tra 1 e 10");
        if (request.currentMatchday() != null
                && (request.currentMatchday() < 1 || request.currentMatchday() > 38))
            throw new BadRequestException("currentMatchday deve essere compreso tra 1 e 38");
        List<BoughtPlayerRequest> bought = request.playersAlreadyBought() == null
                ? List.of() : request.playersAlreadyBought();
        Set<Long> ids = new HashSet<>();
        int spent = 0;
        for (BoughtPlayerRequest player : bought) {
            if (player.playerId() == null || player.playerId() <= 0 || !ids.add(player.playerId()))
                throw new BadRequestException("La rosa contiene playerId nulli o duplicati");
            if (player.pricePaid() < 1) throw new BadRequestException("Ogni prezzo pagato deve essere almeno 1");
            spent += player.pricePaid();
        }
        if (request.totalBudget() - spent != request.remainingBudget())
            throw new BadRequestException("Budget residuo incoerente con i prezzi pagati");
        if (ids.contains(request.currentPlayerId()))
            throw new BadRequestException("Il giocatore corrente e' gia' stato acquistato");
        validateIds("excludedPlayerIds", request.excludedPlayerIds(), request.currentPlayerId(), ids, false);
        validateIds("unavailablePlayerIds", request.unavailablePlayerIds(), request.currentPlayerId(), ids, true);
    }

    int effectiveBid(LiveAuctionDecisionRequest request) {
        if (request.proposedBid() != null && request.currentBid() != null
                && !request.proposedBid().equals(request.currentBid())) {
            throw new BadRequestException("proposedBid e currentBid legacy sono in conflitto");
        }
        Integer bid = request.proposedBid() != null ? request.proposedBid() : request.currentBid();
        if (bid == null || bid < 1) throw new BadRequestException("proposedBid deve essere almeno 1");
        return bid;
    }

    private LiveAuctionDecisionRequest canonical(LiveAuctionDecisionRequest request) {
        return new LiveAuctionDecisionRequest(request.season(), request.leagueTeams(),
                request.totalBudget(), request.remainingBudget(), request.defenseModifier(),
                request.currentRole(), request.currentPlayerId(), request.currentPrice(),
                effectiveBid(request), request.currentBid(), request.rosterSlots(),
                request.playersAlreadyBought(), request.currentMatchday(), request.calendarHorizon(),
                request.alternativeLimit(), safeIds(request.excludedPlayerIds()),
                safeIds(request.unavailablePlayerIds()), Boolean.TRUE.equals(request.availabilityComplete()));
    }

    private void validateIds(String field, List<Long> values, Long currentPlayerId,
            Set<Long> bought, boolean rejectBoughtOverlap) {
        Set<Long> unique = new HashSet<>();
        for (Long id : safeIds(values)) {
            if (id == null || id <= 0 || !unique.add(id))
                throw new BadRequestException(field + " deve contenere ID positivi e unici");
            if (id.equals(currentPlayerId))
                throw new BadRequestException("Il giocatore corrente non puo' essere " + field);
            if (rejectBoughtOverlap && bought.contains(id))
                throw new BadRequestException("Un player comprato dall'utente non puo' essere unavailable");
        }
    }

    private List<Long> safeIds(List<Long> values) { return values == null ? List.of() : values; }

    private Map<Long, SquadTargetResponse> allTargets(SquadConstructionResponse response) {
        Map<Long, SquadTargetResponse> result = new LinkedHashMap<>();
        response.recommendedTargets().forEach(target -> result.put(target.playerId(), target));
        return result;
    }

    private LiveAuctionCandidateEvaluation evaluation(SquadTargetResponse target,
            Map<Long, StrategicIntelligenceProfile> strategic,
            Map<Long, CalendarCombinationResult> calendar) {
        return new LiveAuctionCandidateEvaluation(target, strategic.get(target.playerId()),
                calendar.get(target.playerId()));
    }

    private int horizon(LiveAuctionDecisionRequest request) {
        return request.calendarHorizon() == null ? 5 : request.calendarHorizon();
    }

    private LiveAuctionDecisionResponse insufficient(LiveAuctionDecisionRequest request,
            PlayerContext player) {
        int bid = request.proposedBid();
        boolean availability = Boolean.TRUE.equals(request.availabilityComplete());
        return new LiveAuctionDecisionResponse(LiveAuctionAction.INSUFFICIENT_EVIDENCE,
                request.currentPlayerId(), null, player.role(), request.currentPrice(), bid, bid, bid,
                null, null, null, null,
                request.remainingBudget(), request.remainingBudget() - bid, null, null,
                new LiveAuctionDecisionResponse.StrategicSummary(Map.of(), BigDecimal.ZERO,
                        List.of("B51_OR_B54_CANDIDATE_EVIDENCE")),
                new LiveAuctionDecisionResponse.CalendarSummary(null, "INSUFFICIENT_EVIDENCE",
                        BigDecimal.ZERO, request.currentMatchday(), request.calendarHorizon(), null),
                availability, availability, null, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                LiveAuctionEvidenceStatus.INSUFFICIENT_EVIDENCE,
                List.of("B51_OR_B54_EVIDENCE_REQUIRED_FOR_DECISION"), List.of(),
                List.of("B51_RECOMMENDED_PRICE", "B54_TARGET_CONTEXT"));
    }

}
