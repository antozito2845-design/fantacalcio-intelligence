package it.fantacalciointelligence.backend.liveauction;

import it.fantacalciointelligence.backend.calendarintelligence.CalendarCombinationResult;
import it.fantacalciointelligence.backend.squadconstruction.SquadTargetResponse;
import it.fantacalciointelligence.backend.strategicintelligence.StrategicIntelligenceProfile;

record LiveAuctionCandidateEvaluation(
        SquadTargetResponse target,
        StrategicIntelligenceProfile strategic,
        CalendarCombinationResult calendar
) { }
