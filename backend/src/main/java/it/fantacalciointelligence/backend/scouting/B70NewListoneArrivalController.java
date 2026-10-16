package it.fantacalciointelligence.backend.scouting;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class B70NewListoneArrivalController {

    private final B70NewListoneArrivalService service;

    public B70NewListoneArrivalController(
            B70NewListoneArrivalService service
    ) {
        this.service = service;
    }

    @GetMapping("/api/v1/scouting/arrivals/listone-new")
    public List<B70NewListoneArrivalResponse> findNewEntries(
            @RequestParam String season,
            @RequestParam(required = false)
            String role,
            @RequestParam(required = false)
            String club
    ) {

        return service.findNewEntries(
                season,
                role,
                club
        );
    }
}