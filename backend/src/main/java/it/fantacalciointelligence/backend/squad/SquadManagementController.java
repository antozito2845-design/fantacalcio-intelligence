package it.fantacalciointelligence.backend.squad;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/v1/squads")
public class SquadManagementController {

    private final SquadService squadService;

    public SquadManagementController(
            SquadService squadService
    ) {
        this.squadService = squadService;
    }

    @GetMapping
    public List<SquadSummaryResponse> findSquads(
            @RequestParam(
                    defaultValue = "#{@currentSeasonResolver.resolve()}"
            )
            String season,
            Authentication authentication
    ) {
        return squadService.findSquads(season, authentication.getName(), isAdmin(authentication));
    }

    @PostMapping
    public SquadResponse createSquad(
            @Valid @RequestBody CreateSquadRequest request,
            Authentication authentication
    ) {
        return squadService.createSquad(request, authentication.getName());
    }

    @GetMapping("/{squadId}")
    public SquadResponse findSquad(
            @PathVariable
            Long squadId,
            Authentication authentication
    ) {
        return squadService.findSquad(squadId, authentication.getName(), isAdmin(authentication));
    }

    @PostMapping("/{squadId}/players")
    public SquadResponse addPlayer(
            @PathVariable
            Long squadId,

            @Valid
            @RequestBody
            AddSquadPlayerRequest request,
            Authentication authentication
    ) {
        squadService.assertAccess(squadId, authentication.getName(), isAdmin(authentication));
        return squadService.addPlayer(
                squadId,
                request
        );
    }

    @DeleteMapping("/{squadId}/players/{playerId}")
    public SquadResponse removePlayer(
            @PathVariable
            Long squadId,

            @PathVariable
            Long playerId,
            Authentication authentication
    ) {
        squadService.assertAccess(squadId, authentication.getName(), isAdmin(authentication));
        return squadService.removePlayer(
                squadId,
                playerId
        );
    }

    @PatchMapping("/{squadId}/players/{playerId}")
    public SquadResponse updatePlayerPrice(
            @PathVariable Long squadId,
            @PathVariable Long playerId,
            @Valid @RequestBody UpdateSquadPlayerPriceRequest request,
            Authentication authentication
    ) {
        squadService.assertAccess(squadId, authentication.getName(), isAdmin(authentication));
        return squadService.updatePlayerPrice(squadId, playerId, request);
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
