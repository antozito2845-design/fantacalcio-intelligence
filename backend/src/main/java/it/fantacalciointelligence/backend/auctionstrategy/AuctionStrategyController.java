package it.fantacalciointelligence.backend.auctionstrategy;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuctionStrategyController {

    private final IntelligentAuctionStrategyService service;

    public AuctionStrategyController(
            IntelligentAuctionStrategyService service
    ) {
        this.service = service;
    }

    @GetMapping("/api/v1/auction/strategy")
    public AuctionStrategyResponse buildStrategy(
            @RequestParam(
                    defaultValue = "#{@currentSeasonResolver.resolve()}"
            )
            String season,

            @RequestParam(defaultValue = "1000")
            int budget,

            @RequestParam(defaultValue = "8")
            int teams,

            @RequestParam(defaultValue = "true")
            boolean defenseModifier
    ) {
        return service.buildStrategy(
                season,
                budget,
                teams,
                defenseModifier
        );
    }
}
