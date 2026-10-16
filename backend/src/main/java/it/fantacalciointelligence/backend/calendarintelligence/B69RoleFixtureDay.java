package it.fantacalciointelligence.backend.calendarintelligence;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

public record B69RoleFixtureDay(
        Long clubId,
        String clubName,
        Long opponentClubId,
        String opponentClubName,
        int matchday,
        Long fixtureId,
        boolean home,
        String fixtureStatus,
        OffsetDateTime kickoffAt,
        Map<String, B69RoleMatchupScorer.MatchupResult> roleMatchups
) {

    public B69RoleFixtureDay {

        roleMatchups =
                roleMatchups == null
                        ? Map.of()
                        : Map.copyOf(roleMatchups);
    }

    public B69RoleMatchupScorer.MatchupResult matchup(
            String role
    ) {

        if (role == null) {
            return null;
        }

        return roleMatchups.get(
                role.trim().toUpperCase()
        );
    }

    public BigDecimal difficultyScore(
            String role
    ) {

        B69RoleMatchupScorer.MatchupResult result =
                matchup(role);

        return result == null
                ? null
                : result.difficultyScore();
    }

    public CalendarDifficulty difficulty(
            String role
    ) {

        B69RoleMatchupScorer.MatchupResult result =
                matchup(role);

        return result == null
                ? CalendarDifficulty.UNKNOWN
                : result.difficulty();
    }

    public boolean usable(
            String role
    ) {

        if (
                "POSTPONED".equals(fixtureStatus)
                || "CANCELLED".equals(fixtureStatus)
        ) {
            return false;
        }

        return difficulty(role)
                != CalendarDifficulty.UNKNOWN;
    }
}