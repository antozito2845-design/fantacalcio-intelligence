package it.fantacalciointelligence.backend.player;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/players/history")
public class HistoricalPlayerDetailController {

    private final HistoricalPlayerDetailService service;

    public HistoricalPlayerDetailController(
            HistoricalPlayerDetailService service
    ) {
        this.service = service;
    }

    @GetMapping("/{playerId}")
    public HistoricalPlayerDetailResponse findById(
            @PathVariable Long playerId,
            @RequestParam String season
    ) {
        return service.findById(playerId, season);
    }
}
