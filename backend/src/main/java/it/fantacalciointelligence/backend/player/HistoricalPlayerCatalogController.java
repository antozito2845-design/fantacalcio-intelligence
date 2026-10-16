package it.fantacalciointelligence.backend.player;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import it.fantacalciointelligence.backend.common.PageResponse;

@RestController
@RequestMapping("/api/v1/players/history")
public class HistoricalPlayerCatalogController {

    private final HistoricalPlayerCatalogService service;

    public HistoricalPlayerCatalogController(
            HistoricalPlayerCatalogService service
    ) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<HistoricalPlayerCatalogResponse> findPlayers(
            @RequestParam String season,

            @RequestParam(required = false)
            String search,

            @RequestParam(required = false)
            String role,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "25")
            int size
    ) {
        return service.findPlayers(
                season,
                search,
                role,
                page,
                size
        );
    }
}
