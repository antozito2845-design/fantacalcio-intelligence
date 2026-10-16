package it.fantacalciointelligence.backend.ingestion;
import java.util.List;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/admin/source-ingestion")
public class SourceIngestionController {
    private final SourceIngestionService service;public SourceIngestionController(SourceIngestionService service){this.service=service;}
    @PostMapping("/run") public List<SourceIngestionResponse> run(@RequestParam(defaultValue="#{@currentSeasonResolver.resolve()}") String season){return service.ingestAll(season);}
    @PostMapping("/feeds/{feedId}/run") public SourceIngestionResponse runOne(@PathVariable String feedId){return service.ingestOne(feedId);}
    @GetMapping("/latest") public List<SourceIngestionResponse> latest(@RequestParam(defaultValue="#{@currentSeasonResolver.resolve()}") String season){return service.latest(season);}
}
