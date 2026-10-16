package it.fantacalciointelligence.backend.scouting;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpsertForeignPlayerScoutingRequest(

        @NotBlank
        String season,

        @NotBlank
        @Size(max = 150)
        String previousClub,

        @NotBlank
        @Size(max = 150)
        String previousLeague,

        @NotBlank
        @Pattern(regexp = "(?i)[A-Z]{2}")
        String previousCountryCode,

        @NotBlank
        @Pattern(
                regexp =
                    "(?i)PERMANENT|LOAN|FREE_AGENT|RETURN_FROM_LOAN"
        )
        String transferType,

        @Min(0)
        @Max(100)
        int adaptationScore,

        @Min(0)
        @Max(100)
        int potentialScore,

        @Min(0)
        @Max(100)
        int riskScore,

        @Min(0)
        @Max(100)
        int confidenceScore,

        @NotBlank
        @Size(max = 2000)
        String scoutingSummary
) {
}