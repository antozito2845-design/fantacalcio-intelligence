package it.fantacalciointelligence.backend.fixture;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fixtures")
public class FixtureController {

    private final FixtureDifficultyService service;

    private final B69FixtureMatchupService
            matchupService;

    public FixtureController(
            FixtureDifficultyService service,
            B69FixtureMatchupService matchupService
    ) {
        this.service = service;
        this.matchupService = matchupService;
    }

    @GetMapping("/club/{club}")
    public List<FixtureDifficultyResponse> findClubFixtures(
            @PathVariable String club,

            @RequestParam(
                    defaultValue =
                            "#{@currentSeasonResolver.resolve()}"
            )
            String season,

            @RequestParam(
                    defaultValue = "1"
            )
            int fromMatchday,

            @RequestParam(
                    defaultValue = "5"
            )
            int toMatchday
    ) {

        return service.findClubFixtures(
                season,
                club,
                fromMatchday,
                toMatchday
        );
    }

    @GetMapping("/matchday/{matchday}")
    public List<MatchdayFixtureResponse> findMatchday(
            @PathVariable int matchday,

            @RequestParam(
                    defaultValue =
                            "#{@currentSeasonResolver.resolve()}"
            )
            String season
    ) {

        return service.findMatchday(
                season,
                matchday
        );
    }

    @GetMapping("/club/{club}/matchups")
    public List<B69FixtureMatchupResponse> findClubMatchups(
            @PathVariable String club,

            @RequestParam(
                    defaultValue =
                            "#{@currentSeasonResolver.resolve()}"
            )
            String season,

            @RequestParam(
                    defaultValue = "1"
            )
            int fromMatchday,

            @RequestParam(
                    defaultValue = "5"
            )
            int toMatchday
    ) {

        return matchupService.findClubMatchups(
                season,
                club,
                fromMatchday,
                toMatchday
        );
    }
}