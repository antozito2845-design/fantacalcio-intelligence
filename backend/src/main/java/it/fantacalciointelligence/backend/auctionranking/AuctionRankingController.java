package it.fantacalciointelligence.backend.auctionranking;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuctionRankingController {

    private final IntelligentAuctionRankingService service;

    public AuctionRankingController(
            IntelligentAuctionRankingService service
    ) {
        this.service = service;
    }

    @GetMapping("/api/v1/auction/intelligence-rankings")
    public AuctionRankingResponse findRanking(
            @RequestParam(
                    defaultValue = "#{@currentSeasonResolver.resolve()}"
            )
            String season,

            @RequestParam(defaultValue = "1000")
            int budget,

            @RequestParam(defaultValue = "8")
            int teams,

            @RequestParam(defaultValue = "true")
            boolean defenseModifier,

            @RequestParam(required = false)
            String role,

            @RequestParam(required = false)
            String club,

            @RequestParam(required = false)
            Integer maximumPrice,

            @RequestParam(required = false)
            String recommendation,

            @RequestParam(defaultValue = "true")
            boolean excludeUnavailable,

            @RequestParam(defaultValue = "50")
            int limit
    ) {
        return service.findRanking(
                season,
                budget,
                teams,
                defenseModifier,
                role,
                club,
                maximumPrice,
                recommendation,
                excludeUnavailable,
                limit
        );
    }
}
