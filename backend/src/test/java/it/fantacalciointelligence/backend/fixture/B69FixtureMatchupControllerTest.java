package it.fantacalciointelligence.backend.fixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

class B69FixtureMatchupControllerTest {

    @Test
    void newEndpointDelegatesWithoutChangingLegacyControllerContract() {

        FixtureDifficultyService legacy =
                mock(
                        FixtureDifficultyService.class
                );

        B69FixtureMatchupService matchup =
                mock(
                        B69FixtureMatchupService.class
                );

        FixtureController controller =
                new FixtureController(
                        legacy,
                        matchup
                );

        List<B69FixtureMatchupResponse> expected =
                List.of();

        when(
                matchup.findClubMatchups(
                        "2026/2027",
                        "inter",
                        1,
                        5
                )
        ).thenReturn(expected);

        assertThat(
                controller.findClubMatchups(
                        "inter",
                        "2026/2027",
                        1,
                        5
                )
        ).isSameAs(expected);

        verify(matchup)
                .findClubMatchups(
                        "2026/2027",
                        "inter",
                        1,
                        5
                );
    }

    @Test
    void legacyClubEndpointStillDelegatesToLegacyService() {

        FixtureDifficultyService legacy =
                mock(
                        FixtureDifficultyService.class
                );

        B69FixtureMatchupService matchup =
                mock(
                        B69FixtureMatchupService.class
                );

        FixtureController controller =
                new FixtureController(
                        legacy,
                        matchup
                );

        List<FixtureDifficultyResponse> expected =
                List.of();

        when(
                legacy.findClubFixtures(
                        "2026/2027",
                        "inter",
                        1,
                        5
                )
        ).thenReturn(expected);

        assertThat(
                controller.findClubFixtures(
                        "inter",
                        "2026/2027",
                        1,
                        5
                )
        ).isSameAs(expected);

        verify(legacy)
                .findClubFixtures(
                        "2026/2027",
                        "inter",
                        1,
                        5
                );
    }
}