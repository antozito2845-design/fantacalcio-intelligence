package it.fantacalciointelligence.backend.league;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/league")
public class LeagueConfigurationController {

    private final LeagueConfigurationService service;

    public LeagueConfigurationController(
            LeagueConfigurationService service
    ) {
        this.service = service;
    }

    @GetMapping("/configuration")
    public LeagueConfigurationResponse findActive() {
        return service.findActive();
    }

    @PutMapping("/configuration")
    public LeagueConfigurationResponse update(
            @Valid
            @RequestBody
            UpdateLeagueConfigurationRequest request
    ) {
        return service.update(request);
    }
}