package it.fantacalciointelligence.backend.evaluation;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/evaluations")
public class PlayerEvaluationGenerationController {

    private final PlayerEvaluationGenerationService service;

    public PlayerEvaluationGenerationController(
            PlayerEvaluationGenerationService service
    ) {
        this.service = service;
    }

    @PostMapping("/generate")
    public PlayerEvaluationGenerationResponse generate(
            @RequestParam(
                    defaultValue = "#{@currentSeasonResolver.resolve()}"
            )
            String season
    ) {
        return service.generate(season);
    }
}
