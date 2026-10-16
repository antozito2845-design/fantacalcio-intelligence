package it.fantacalciointelligence.backend.club;

public record ClubResponse(
        Long id,
        String name,
        String shortName,
        String slug,
        String countryCode,
        boolean active
) {

    public static ClubResponse from(Club club) {
        return new ClubResponse(
                club.getId(),
                club.getName(),
                club.getShortName(),
                club.getSlug(),
                club.getCountryCode(),
                club.isActive()
        );
    }
}