package it.fantacalciointelligence.backend.intelligence;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlayerIntelligenceController {

    private final PlayerIntelligenceService service;

    public PlayerIntelligenceController(
            PlayerIntelligenceService service
    ) {
        this.service = service;
    }

    @GetMapping(
            "/api/v1/players/{playerId}/intelligence"
    )
    public PlayerIntelligenceResponse findByPlayer(
            @PathVariable Long playerId,

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
        return service.findByPlayer(
                playerId,
                season,
                budget,
                teams,
                defenseModifier
        );
    }
}
