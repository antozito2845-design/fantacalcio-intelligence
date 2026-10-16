package it.fantacalciointelligence.backend.lineup;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/lineups")
public class RecommendedLineupController {

    private final RecommendedLineupService service;

    public RecommendedLineupController(
            RecommendedLineupService service
    ) {
        this.service = service;
    }

    @GetMapping("/recommended")
    public RecommendedLineupResponse recommend(

            @RequestParam(defaultValue = "#{@currentSeasonResolver.resolve()}")
            String season,

            @RequestParam
            int matchday,

            @RequestParam(required = false)
            Long squadId,

            @RequestParam(
                    required = false,
                    defaultValue = ""
            )
            String unavailablePlayerIds,

            Authentication authentication
    ) {
        return service.recommend(
                season,
                matchday,
                squadId,
                unavailablePlayerIds,
                authentication.getName(),
                isAdmin(authentication)
        );
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
