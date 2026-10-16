package it.fantacalciointelligence.backend.squadconstruction;

import java.util.Map;

public record RosterProfileContext(
        Map<String, Integer> coveredProfiles,
        int unknownProfiles,
        int slotsRemaining,
        boolean defenseModifier
) {
    public int covered(String profile) {
        return coveredProfiles.getOrDefault(profile, 0);
    }

    public static RosterProfileContext empty(
            int slotsRemaining,
            boolean defenseModifier
    ) {
        return new RosterProfileContext(
                Map.of(),
                0,
                slotsRemaining,
                defenseModifier
        );
    }
}
