package it.fantacalciointelligence.backend.season;

public record SeasonResponse(
        Long id,
        String code,
        Short startYear,
        Short endYear,
        boolean current
) {

    public static SeasonResponse from(Season season) {
        return new SeasonResponse(
                season.getId(),
                season.getCode(),
                season.getStartYear(),
                season.getEndYear(),
                season.isCurrentSeason()
        );
    }
}