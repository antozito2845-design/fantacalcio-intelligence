package it.fantacalciointelligence.backend.auction;

import java.util.List;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auction")
public class AuctionPriceController {

    private final AuctionApiPriceService auctionApiPriceService;
    private final AuctionRankingService auctionRankingService;

    public AuctionPriceController(
            AuctionApiPriceService auctionApiPriceService,
            AuctionRankingService auctionRankingService
    ) {
        this.auctionApiPriceService = auctionApiPriceService;
        this.auctionRankingService = auctionRankingService;
    }

    @GetMapping("/prices/{playerId}")
    public AuctionPriceResponse calculatePrice(
            @PathVariable Long playerId,

            @RequestParam(defaultValue = "#{@currentSeasonResolver.resolve()}")
            String season,

            @RequestParam(defaultValue = "1000")
            int budget,

            @RequestParam(defaultValue = "8")
            int teams,

            @RequestParam(defaultValue = "true")
            boolean defenseModifier
    ) {
        return auctionApiPriceService.calculate(
                playerId,
                season,
                budget,
                teams,
                defenseModifier
        );
    }

    @GetMapping("/rankings")
    public List<AuctionRankingResponse> calculateRanking(
            @RequestParam(defaultValue = "#{@currentSeasonResolver.resolve()}")
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

            @RequestParam(defaultValue = "100")
            int limit
    ) {
        return auctionRankingService.calculateRanking(
                season,
                budget,
                teams,
                defenseModifier,
                role,
                club,
                limit
        );
    }
}
