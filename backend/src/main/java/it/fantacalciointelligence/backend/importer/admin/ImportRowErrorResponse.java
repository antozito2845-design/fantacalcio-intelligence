package it.fantacalciointelligence.backend.importer.admin;

public record ImportRowErrorResponse(
        Long id,
        int rowNumber,
        String externalId,
        String externalName,
        String clubName,
        String fantasyRole,
        String validationError,
        String rawData
) {
}