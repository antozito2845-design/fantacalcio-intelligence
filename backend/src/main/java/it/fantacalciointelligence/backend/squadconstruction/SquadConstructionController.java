package it.fantacalciointelligence.backend.squadconstruction;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auction/squad-strategy")
public class SquadConstructionController {
    private final SquadConstructionService service;
    public SquadConstructionController(SquadConstructionService service) { this.service = service; }

    @PostMapping("/evaluate")
    public ResponseEntity<SquadConstructionResponse> evaluate(@Valid @RequestBody SquadConstructionRequest request) {
        return ResponseEntity.ok(service.evaluate(request));
    }
}
