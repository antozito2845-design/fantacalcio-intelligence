package it.fantacalciointelligence.backend.importer;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/imports/players")
public class PlayerImportController {

    private final PlayerImportService playerImportService;

    public PlayerImportController(
            PlayerImportService playerImportService
    ) {
        this.playerImportService = playerImportService;
    }

    @PostMapping(
            value = "/csv",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public PlayerImportResponse importCsv(
            @RequestParam String source,
            @RequestParam String season,
            @RequestPart("file") MultipartFile file
    ) {
        return playerImportService.importCsv(
                source,
                season,
                file
        );
    }
}