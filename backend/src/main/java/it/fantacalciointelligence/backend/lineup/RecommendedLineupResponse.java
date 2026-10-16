package it.fantacalciointelligence.backend.lineup;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record RecommendedLineupResponse(
        String season,
        int matchday,
        Long squadId,
        String formation,
        boolean complete,
        int selectedPlayers,
        int missingPlayers,
        BigDecimal formationScore,
        Map<String, LineupRoleSummaryResponse> roles,
        List<LineupPlayerResponse> starters,
        List<LineupPlayerResponse> bench,
        int maxSubstitutions,
        int usedSubstitutions,
        int remainingSubstitutions,
        List<LineupSubstitutionResponse> substitutions,
        List<String> warnings
) {
}