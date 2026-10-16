package it.fantacalciointelligence.backend.fixture.importer;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/imports/fixtures")
public class FixtureImportController {

    private final FixtureImportService service;

    public FixtureImportController(
            FixtureImportService service
    ) {
        this.service = service;
    }

    @PostMapping(
            value = "/csv",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public FixtureImportResponse importCsv(
            @RequestParam String source,
            @RequestParam String season,
            @RequestPart("file") MultipartFile file
    ) {
        return service.importCsv(
                source,
                season,
                file
        );
    }
}