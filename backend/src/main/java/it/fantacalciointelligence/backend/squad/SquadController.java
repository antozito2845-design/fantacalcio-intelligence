package it.fantacalciointelligence.backend.squad;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/v1/squad")
public class SquadController {

    private final SquadService squadService;

    public SquadController(SquadService squadService) {
        this.squadService = squadService;
    }

    @GetMapping
    public SquadResponse findActiveSquad(Authentication authentication) {
        return squadService.findActiveSquad(authentication.getName(), isAdmin(authentication));
    }

    @PostMapping("/players")
    public SquadResponse addPlayer(
            @Valid
            @RequestBody
            AddSquadPlayerRequest request,
            Authentication authentication
    ) {
        SquadResponse squad = squadService.findActiveSquad(authentication.getName(), isAdmin(authentication));
        return squadService.addPlayer(squad.id(), request);
    }

    @DeleteMapping("/players/{playerId}")
    public SquadResponse removePlayer(
            @PathVariable Long playerId,
            Authentication authentication
    ) {
        SquadResponse squad = squadService.findActiveSquad(authentication.getName(), isAdmin(authentication));
        return squadService.removePlayer(squad.id(), playerId);
    }

    @PatchMapping("/players/{playerId}")
    public SquadResponse updatePlayerPrice(
            @PathVariable Long playerId,
            @Valid @RequestBody UpdateSquadPlayerPriceRequest request,
            Authentication authentication
    ) {
        SquadResponse squad = squadService.findActiveSquad(authentication.getName(), isAdmin(authentication));
        return squadService.updatePlayerPrice(squad.id(), playerId, request);
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
