package it.fantacalciointelligence.backend.intelligence;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlayerIntelligenceProfileController {
    private final PlayerIntelligenceProfileService service;

    public PlayerIntelligenceProfileController(PlayerIntelligenceProfileService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/players/{playerId}/intelligence-profile")
    public PlayerIntelligenceProfileResponse find(@PathVariable Long playerId, @RequestParam String season) {
        return service.find(playerId, season);
    }
}
