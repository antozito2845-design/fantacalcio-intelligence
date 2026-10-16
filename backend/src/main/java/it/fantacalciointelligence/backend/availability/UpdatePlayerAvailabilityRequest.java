package it.fantacalciointelligence.backend.availability;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdatePlayerAvailabilityRequest(

        @NotBlank
        String season,

        @Min(1)
        @Max(38)
        int matchday,

        @NotBlank
        String status,

        @Min(0)
        @Max(100)
        Integer startingProbability,

        @Min(0)
        @Max(100)
        Integer availabilityProbability,

        @Size(max = 255)
        String injuryDescription,

        LocalDate expectedReturnDate,

        @Pattern(
                regexp =
                    "(?i)MANUAL|FANTACALCIO|GAZZETTA|FANTAMARKT|AVERAGE"
        )
        String source
) {
}