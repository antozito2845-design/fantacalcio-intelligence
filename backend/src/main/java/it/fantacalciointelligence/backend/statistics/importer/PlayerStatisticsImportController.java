package it.fantacalciointelligence.backend.statistics.importer;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/imports/statistics")
public class PlayerStatisticsImportController {

    private final PlayerStatisticsImportService service;

    public PlayerStatisticsImportController(
            PlayerStatisticsImportService service
    ) {
        this.service = service;
    }

    @PostMapping(
            value = "/csv",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public PlayerStatisticsImportResponse importCsv(

            @RequestParam
            String source,

            @RequestParam
            String season,

            @RequestPart("file")
            MultipartFile file
    ) {
        return service.importCsv(
                source,
                season,
                file
        );
    }
}