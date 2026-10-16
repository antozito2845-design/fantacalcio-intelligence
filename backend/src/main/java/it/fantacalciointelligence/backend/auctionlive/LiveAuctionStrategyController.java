package it.fantacalciointelligence.backend.auctionlive;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auction/live-strategy")
public class LiveAuctionStrategyController {

    private final LiveAuctionStrategyService service;

    public LiveAuctionStrategyController(
            LiveAuctionStrategyService service
    ) {
        this.service = service;
    }

    @GetMapping
    public LiveAuctionStrategyResponse buildActiveStrategy() {
        return service.buildActiveStrategy();
    }

    @GetMapping("/squads/{squadId}")
    public LiveAuctionStrategyResponse buildStrategy(
            @PathVariable
            Long squadId
    ) {
        return service.buildStrategy(squadId);
    }
}