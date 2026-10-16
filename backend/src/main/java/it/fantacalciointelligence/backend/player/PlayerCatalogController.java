package it.fantacalciointelligence.backend.player;

import org.springframework.web.bind.annotation.*;

import it.fantacalciointelligence.backend.common.PageResponse;

@RestController
@RequestMapping("/api/v1/players")
public class PlayerCatalogController {

    private final PlayerCatalogService playerCatalogService;
    private final PlayerDetailService playerDetailService;

    public PlayerCatalogController(
            PlayerCatalogService playerCatalogService,
            PlayerDetailService playerDetailService
    ) {
        this.playerCatalogService = playerCatalogService;
        this.playerDetailService = playerDetailService;
    }

    @GetMapping
    public PageResponse<PlayerCatalogResponse> findPlayers(
            @RequestParam(defaultValue = "#{@currentSeasonResolver.resolve()}")
            String season,

            @RequestParam(required = false)
            String search,

            @RequestParam(required = false)
            String club,

            @RequestParam(required = false)
            String role,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "25")
            int size
    ) {
        return playerCatalogService.findPlayers(
                season,
                search,
                club,
                role,
                page,
                size
        );
    }

    @GetMapping("/{playerId}")
    public PlayerDetailResponse findById(
            @PathVariable Long playerId,
            @RequestParam(defaultValue = "#{@currentSeasonResolver.resolve()}")
            String season
    ) {
        return playerDetailService.findById(
                playerId,
                season
        );
    }
}
