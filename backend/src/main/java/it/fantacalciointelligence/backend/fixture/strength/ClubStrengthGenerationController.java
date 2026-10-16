package it.fantacalciointelligence.backend.fixture.strength;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/club-strength")
public class ClubStrengthGenerationController {

    private final ClubStrengthGenerationService service;

    public ClubStrengthGenerationController(
            ClubStrengthGenerationService service
    ) {
        this.service = service;
    }

    @PostMapping("/generate")
    public ClubStrengthGenerationResponse generate(
            @RequestParam(
                    defaultValue = "#{@currentSeasonResolver.resolve()}"
            )
            String season
    ) {
        return service.generate(season);
    }
}
