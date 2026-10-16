package it.fantacalciointelligence.backend.fixture;

import it.fantacalciointelligence.backend.calendarintelligence.B69RoleFixtureDay;
import it.fantacalciointelligence.backend.calendarintelligence.B69RoleFixtureProfile;
import it.fantacalciointelligence.backend.calendarintelligence.B69RoleMatchupScorer;
import it.fantacalciointelligence.backend.calendarintelligence.CalendarDifficulty;
import it.fantacalciointelligence.backend.calendarintelligence.FixtureWindowService;
import it.fantacalciointelligence.backend.common.ResourceNotFoundException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class B69FixtureMatchupService {

    private static final List<String> ROLES =
            List.of(
                    "P",
                    "D",
                    "C",
                    "A"
            );

    private final JdbcClient jdbcClient;

    private final FixtureWindowService fixtureWindowService;

    public B69FixtureMatchupService(
            JdbcClient jdbcClient,
            FixtureWindowService fixtureWindowService
    ) {
        this.jdbcClient = jdbcClient;
        this.fixtureWindowService =
                fixtureWindowService;
    }

    @Transactional(readOnly = true)
    public List<B69FixtureMatchupResponse> findClubMatchups(
            String season,
            String club,
            int fromMatchday,
            int toMatchday
    ) {

        validate(
                season,
                club,
                fromMatchday,
                toMatchday
        );

        ClubIdentity clubIdentity =
                findClub(
                        season,
                        club
                );

        int horizon =
                toMatchday
                        - fromMatchday
                        + 1;

        Map<Long, B69RoleFixtureProfile> profiles =
                fixtureWindowService
                        .loadRoleAwareClubProfiles(
                                season,
                                List.of(
                                        clubIdentity.id()
                                ),
                                fromMatchday,
                                horizon
                        );

        B69RoleFixtureProfile profile =
                profiles.get(
                        clubIdentity.id()
                );

        if (profile == null) {
            return List.of();
        }

        List<B69FixtureMatchupResponse> response =
                new ArrayList<>();

        profile.matchdays()
                .values()
                .stream()
                .sorted(
                        Comparator.comparingInt(
                                B69RoleFixtureDay::matchday
                        )
                )
                .forEach(day ->
                        response.add(
                                toResponse(day)
                        )
                );

        return List.copyOf(response);
    }

    private B69FixtureMatchupResponse toResponse(
            B69RoleFixtureDay day
    ) {

        List<B69RoleDifficultyResponse> roles =
                new ArrayList<>();

        for (String role : ROLES) {

            B69RoleMatchupScorer.MatchupResult matchup =
                    day.matchup(role);

            if (matchup == null) {

                roles.add(
                        new B69RoleDifficultyResponse(
                                role,
                                null,
                                "SCONOSCIUTA",
                                "NO_EVIDENCE",
                                java.math.BigDecimal.ZERO
                                        .setScale(2)
                        )
                );

                continue;
            }

            roles.add(
                    new B69RoleDifficultyResponse(
                            role,
                            matchup.difficultyScore(),
                            level(
                                    matchup.difficulty()
                            ),
                            matchup.evidenceTier(),
                            matchup.evidenceCoverage()
                    )
            );
        }

        return new B69FixtureMatchupResponse(
                day.fixtureId(),
                day.matchday(),
                day.kickoffAt(),
                day.clubId(),
                day.clubName(),
                day.opponentClubId(),
                day.opponentClubName(),
                day.home(),
                day.fixtureStatus(),
                roles
        );
    }

    private ClubIdentity findClub(
            String season,
            String club
    ) {

        return jdbcClient.sql("""
                SELECT
                    club.id,
                    club.name,
                    club.slug

                FROM clubs club

                JOIN season_clubs membership
                    ON membership.club_id =
                        club.id

                JOIN seasons season
                    ON season.id =
                        membership.season_id

                WHERE season.code =
                    :season

                  AND (
                      LOWER(club.slug) =
                        LOWER(:club)

                      OR LOWER(club.name) =
                        LOWER(:club)
                  )

                ORDER BY club.id

                LIMIT 1
                """)
                .param(
                        "season",
                        season
                )
                .param(
                        "club",
                        club
                )
                .query(
                        (rs, rowNumber) ->
                                new ClubIdentity(
                                        rs.getLong(
                                                "id"
                                        ),
                                        rs.getString(
                                                "name"
                                        ),
                                        rs.getString(
                                                "slug"
                                        )
                                )
                )
                .optional()
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Club non trovato nella stagione "
                                        + season
                                        + ": "
                                        + club
                        )
                );
    }

    private String level(
            CalendarDifficulty difficulty
    ) {

        if (
                difficulty == null
                || difficulty
                    == CalendarDifficulty.UNKNOWN
        ) {
            return "SCONOSCIUTA";
        }

        return switch (difficulty) {

            case EASY ->
                    "FACILE";

            case MEDIUM ->
                    "MEDIA";

            case HARD ->
                    "DIFFICILE";

            case UNKNOWN ->
                    "SCONOSCIUTA";
        };
    }

    private void validate(
            String season,
            String club,
            int fromMatchday,
            int toMatchday
    ) {

        if (
                season == null
                || season.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "Season is required"
            );
        }

        if (
                club == null
                || club.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "Club is required"
            );
        }

        if (
                fromMatchday < 1
                || fromMatchday > 38
                || toMatchday < 1
                || toMatchday > 38
                || toMatchday < fromMatchday
        ) {
            throw new IllegalArgumentException(
                    "Invalid matchday range"
            );
        }

        if (
                toMatchday
                        - fromMatchday
                        + 1
                > 10
        ) {
            throw new IllegalArgumentException(
                    "Matchup window cannot exceed 10 matchdays"
            );
        }
    }

    private record ClubIdentity(
            Long id,
            String name,
            String slug
    ) {
    }
}