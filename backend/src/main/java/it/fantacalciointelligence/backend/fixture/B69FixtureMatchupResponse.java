package it.fantacalciointelligence.backend.fixture;

import java.time.OffsetDateTime;
import java.util.List;

public record B69FixtureMatchupResponse(
        Long fixtureId,
        int matchday,
        OffsetDateTime kickoffAt,
        Long clubId,
        String club,
        Long opponentClubId,
        String opponent,
        boolean home,
        String status,
        List<B69RoleDifficultyResponse> roles
) {

    public B69FixtureMatchupResponse {

        roles =
                roles == null
                        ? List.of()
                        : List.copyOf(roles);
    }
}