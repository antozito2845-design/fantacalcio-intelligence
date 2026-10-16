package it.fantacalciointelligence.backend.scouting;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ForeignPlayerScoutingController {

    private final ForeignPlayerScoutingService service;

    public ForeignPlayerScoutingController(
            ForeignPlayerScoutingService service
    ) {
        this.service = service;
    }

    @PutMapping(
            "/api/v1/admin/scouting/players/{playerId}"
    )
    public ForeignPlayerScoutingResponse upsert(
            @PathVariable Long playerId,
            @Valid
            @RequestBody
            UpsertForeignPlayerScoutingRequest request
    ) {
        return service.upsert(
                playerId,
                request
        );
    }

    @GetMapping("/api/v1/scouting")
    public List<ForeignPlayerScoutingResponse> findAll(
            @RequestParam(
                    defaultValue = "#{@currentSeasonResolver.resolve()}"
            )
            String season,

            @RequestParam(required = false)
            String recommendation
    ) {
        return service.findAll(
                season,
                recommendation
        );
    }

    @GetMapping(
            "/api/v1/scouting/players/{playerId}"
    )
    public ForeignPlayerScoutingResponse findByPlayer(
            @PathVariable Long playerId,

            @RequestParam(
                    defaultValue = "#{@currentSeasonResolver.resolve()}"
            )
            String season
    ) {
        return service.findByPlayer(
                playerId,
                season
        );
    }
}
