package it.fantacalciointelligence.backend.squadanalysis;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/squad-analysis")
public class SquadAnalysisController {

    private final SquadAnalysisService service;

    public SquadAnalysisController(
            SquadAnalysisService service
    ) {
        this.service = service;
    }

    @GetMapping
    public SquadAnalysisResponse analyzeActiveSquad(
            @RequestParam(defaultValue = "1")
            int fromMatchday,

            @RequestParam(defaultValue = "5")
            int toMatchday,

            Authentication authentication
    ) {
        return service.analyzeActiveSquad(
                fromMatchday,
                toMatchday,
                authentication.getName(),
                isAdmin(authentication)
        );
    }

    @GetMapping("/squads/{squadId}")
    public SquadAnalysisResponse analyzeSquad(
            @PathVariable
            Long squadId,

            @RequestParam(defaultValue = "1")
            int fromMatchday,

            @RequestParam(defaultValue = "5")
            int toMatchday,

            Authentication authentication
    ) {
        return service.analyzeSquad(
                squadId,
                fromMatchday,
                toMatchday,
                authentication.getName(),
                isAdmin(authentication)
        );
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
