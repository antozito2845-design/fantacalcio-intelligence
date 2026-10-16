package it.fantacalciointelligence.backend.liveauction;

import java.util.List;
import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import it.fantacalciointelligence.backend.squadconstruction.BoughtPlayerRequest;

public record LiveAuctionDecisionRequest(
        String season,
        @Min(6) @Max(12) Integer leagueTeams,
        @Min(150) @Max(1000) Integer totalBudget,
        @Min(0) Integer remainingBudget,
        Boolean defenseModifier,
        @Pattern(regexp = "P|D|C|A") String currentRole,
        @NotNull Long currentPlayerId,
        @Min(1) Integer currentPrice,
        @Min(1) Integer proposedBid,
        @Deprecated
        Integer currentBid,
        Map<String, Integer> rosterSlots,
        List<@Valid BoughtPlayerRequest> playersAlreadyBought,
        @Min(1) @Max(38) Integer currentMatchday,
        @Min(1) @Max(38) Integer calendarHorizon,
        @Min(1) @Max(100) Integer alternativeLimit,
        List<Long> excludedPlayerIds,
        List<Long> unavailablePlayerIds,
        Boolean availabilityComplete
) {
    /**
     * B58 compatibility constructor. The old currentBid is interpreted only as
     * the proposed/evaluated bid; it never becomes currentPrice implicitly.
     */
    @Deprecated
    public LiveAuctionDecisionRequest(String season, Integer leagueTeams,
            Integer totalBudget, Integer remainingBudget, Boolean defenseModifier,
            String currentRole, Long currentPlayerId, Integer currentBid,
            Map<String, Integer> rosterSlots, List<BoughtPlayerRequest> playersAlreadyBought,
            Integer currentMatchday, Integer calendarHorizon, Integer alternativeLimit,
            List<Long> excludedPlayerIds) {
        this(season, leagueTeams, totalBudget, remainingBudget, defenseModifier,
                currentRole, currentPlayerId, null, null, currentBid, rosterSlots,
                playersAlreadyBought, currentMatchday, calendarHorizon, alternativeLimit,
                excludedPlayerIds, List.of(), false);
    }
}
