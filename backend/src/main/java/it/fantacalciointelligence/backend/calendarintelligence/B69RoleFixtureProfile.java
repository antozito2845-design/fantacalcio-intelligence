package it.fantacalciointelligence.backend.calendarintelligence;

import java.util.Map;

public record B69RoleFixtureProfile(
        Long clubId,
        String clubName,
        Map<Integer, B69RoleFixtureDay> matchdays
) {

    public B69RoleFixtureProfile {

        matchdays =
                matchdays == null
                        ? Map.of()
                        : Map.copyOf(matchdays);
    }
}