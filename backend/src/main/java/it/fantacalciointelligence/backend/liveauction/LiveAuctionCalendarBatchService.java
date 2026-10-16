package it.fantacalciointelligence.backend.liveauction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import it.fantacalciointelligence.backend.calendarintelligence.CalendarCandidateService.CalendarPlayerCandidate;
import it.fantacalciointelligence.backend.calendarintelligence.CalendarCombinationResult;
import it.fantacalciointelligence.backend.calendarintelligence.CalendarCombinationScorer;
import it.fantacalciointelligence.backend.calendarintelligence.ClubFixtureProfile;
import it.fantacalciointelligence.backend.calendarintelligence.FixtureWindowService;
import it.fantacalciointelligence.backend.squadconstruction.BoughtPlayerRequest;
import it.fantacalciointelligence.backend.squadconstruction.SquadTargetResponse;

@Service
public class LiveAuctionCalendarBatchService {
    private final FixtureWindowService windowService;
    private final CalendarCombinationScorer scorer;

    public LiveAuctionCalendarBatchService(FixtureWindowService windowService,
            CalendarCombinationScorer scorer) {
        this.windowService = windowService;
        this.scorer = scorer;
    }

    public Map<Long, CalendarCombinationResult> evaluate(String season, int from, int horizon,
            List<SquadTargetResponse> targets, List<CalendarPlayerCandidate> activeCandidates,
            List<BoughtPlayerRequest> boughtPlayers, String role) {
        Map<Long, CalendarPlayerCandidate> active = new LinkedHashMap<>();
        activeCandidates.forEach(candidate -> active.put(candidate.playerId(), candidate));
        List<Long> ownedClubIds = boughtPlayers.stream()
                .filter(player -> role.equalsIgnoreCase(player.role()))
                .map(BoughtPlayerRequest::playerId).map(active::get).filter(java.util.Objects::nonNull)
                .map(CalendarPlayerCandidate::clubId).distinct().sorted().limit(2).toList();
        if (ownedClubIds.isEmpty()) return Map.of();

        List<Long> clubIds = new ArrayList<>(ownedClubIds);
        targets.stream().map(SquadTargetResponse::playerId).map(active::get)
                .filter(java.util.Objects::nonNull).map(CalendarPlayerCandidate::clubId)
                .filter(id -> !clubIds.contains(id)).forEach(clubIds::add);
        Map<Long, ClubFixtureProfile> profiles = windowService.loadClubProfiles(season, clubIds, from, horizon);
        Map<Long, CalendarCombinationResult> result = new LinkedHashMap<>();
        for (SquadTargetResponse target : targets) {
            CalendarPlayerCandidate candidate = active.get(target.playerId());
            if (candidate == null || ownedClubIds.contains(candidate.clubId())) continue;
            List<Long> combination = new ArrayList<>();
            combination.add(candidate.clubId());
            ownedClubIds.stream().filter(id -> !id.equals(candidate.clubId()))
                    .limit(2).forEach(combination::add);
            if (combination.size() < 2) continue;
            List<ClubFixtureProfile> candidateProfiles = combination.stream().map(profiles::get)
                    .filter(java.util.Objects::nonNull).toList();
            if (candidateProfiles.size() == combination.size()) {
                result.put(target.playerId(), scorer.evaluate(candidateProfiles, from, horizon));
            }
        }
        return Map.copyOf(result);
    }
}
