package it.fantacalciointelligence.backend.pairing;

import java.util.List;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pairings")
public class PlayerPairingController {

    private final PlayerPairingService service;

    public PlayerPairingController(
            PlayerPairingService service
    ) {
        this.service = service;
    }

    @GetMapping
    public List<PlayerPairingResponse> findPairings(

            @RequestParam(defaultValue = "#{@currentSeasonResolver.resolve()}")
            String season,

            @RequestParam
            String role,

            @RequestParam(defaultValue = "1000")
            int budget,

            @RequestParam(defaultValue = "8")
            int teams,

            @RequestParam(defaultValue = "true")
            boolean defenseModifier,

            @RequestParam(defaultValue = "1")
            int fromMatchday,

            @RequestParam(defaultValue = "5")
            int toMatchday,

            @RequestParam(defaultValue = "20")
            int limit
    ) {
        return service.findPairings(
                season,
                role,
                budget,
                teams,
                defenseModifier,
                fromMatchday,
                toMatchday,
                limit
        );
    }
}
