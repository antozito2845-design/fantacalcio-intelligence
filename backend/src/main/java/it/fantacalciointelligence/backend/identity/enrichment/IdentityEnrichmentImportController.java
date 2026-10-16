package it.fantacalciointelligence.backend.identity.enrichment;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/imports/identity")
public class IdentityEnrichmentImportController {
    private final IdentityEnrichmentImportService service;
    public IdentityEnrichmentImportController(IdentityEnrichmentImportService service) { this.service = service; }

    @PostMapping(value = "/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public IdentityEnrichmentImportResponse importCsv(@RequestPart("file") MultipartFile file) {
        return service.importCsv(file);
    }
}
