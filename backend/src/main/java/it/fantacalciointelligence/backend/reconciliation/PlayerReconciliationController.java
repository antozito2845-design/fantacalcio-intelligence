package it.fantacalciointelligence.backend.reconciliation;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reconciliation")
public class PlayerReconciliationController {

    private final PlayerReconciliationService service;

    public PlayerReconciliationController(
            PlayerReconciliationService service
    ) {
        this.service = service;
    }

    @PostMapping("/players")
    public ReconciliationResponse reconcile(
            @RequestParam String season
    ) {
        return service.reconcile(season);
    }
}