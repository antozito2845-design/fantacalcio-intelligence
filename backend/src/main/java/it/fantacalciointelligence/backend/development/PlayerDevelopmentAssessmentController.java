package it.fantacalciointelligence.backend.development;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlayerDevelopmentAssessmentController {

    private final PlayerDevelopmentAssessmentService service;

    public PlayerDevelopmentAssessmentController(
            PlayerDevelopmentAssessmentService service
    ) {
        this.service = service;
    }

    @PostMapping(
            "/api/v1/admin/player-development/generate"
    )
    public ResponseEntity<PlayerDevelopmentGenerationResponse>
    generate(
            @RequestParam("season") String season
    ) {
        return ResponseEntity.ok(
                service.generate(season)
        );
    }

    @GetMapping(
            "/api/v1/players/{playerId}/development"
    )
    public ResponseEntity<PlayerDevelopmentAssessmentResponse>
    findByPlayer(
            @PathVariable Long playerId,
            @RequestParam("season") String season
    ) {
        return ResponseEntity.ok(
                service.findByPlayer(
                        playerId,
                        season
                )
        );
    }
}