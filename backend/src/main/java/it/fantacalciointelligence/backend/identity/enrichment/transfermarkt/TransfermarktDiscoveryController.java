package it.fantacalciointelligence.backend.identity.enrichment.transfermarkt;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/identity/transfermarkt/discovery")
public class TransfermarktDiscoveryController {

    private final TransfermarktDiscoveryService discoveryService;
    private final TransfermarktProvenancePersistenceService persistenceService;

    public TransfermarktDiscoveryController(
            TransfermarktDiscoveryService discoveryService,
            TransfermarktProvenancePersistenceService persistenceService) {
        this.discoveryService = discoveryService;
        this.persistenceService = persistenceService;
    }

    @PostMapping("/run")
    public TransfermarktDiscoveryResponse run(
            @RequestParam(defaultValue = "#{@currentSeasonResolver.resolve()}") String season,
            @RequestParam(required = false) String club,
            @RequestParam(defaultValue = "30") int limit) {
        return discoveryService.discover(
                season,
                club,
                limit);
    }

    @PostMapping("/persist")
    public TransfermarktPersistenceResponse persist(
            @RequestParam(defaultValue = "#{@currentSeasonResolver.resolve()}") String season,
            @RequestParam(required = false) String club,
            @RequestParam(defaultValue = "30") int limit) {
        TransfermarktDiscoveryResponse discovery = discoveryService.discover(
                season,
                club,
                limit);

        int persistedFields = persistenceService.persist(
                season,
                discovery);

        return new TransfermarktPersistenceResponse(
                discovery.season(),
                discovery.club(),
                discovery.requestedPlayers(),
                discovery.matchedVerified(),
                discovery.reviewRequired(),
                discovery.notFound(),
                discovery.failed(),
                persistedFields);
    }
}