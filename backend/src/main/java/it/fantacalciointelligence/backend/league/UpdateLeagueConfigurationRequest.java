package it.fantacalciointelligence.backend.league;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateLeagueConfigurationRequest(

        @NotBlank
        @Size(max = 100)
        String name,

        @NotBlank
        String season,

        @Min(6)
        @Max(12)
        int teamsCount,

        @Min(150)
        @Max(1000)
        int totalBudget,

        @Min(1)
        @Max(5)
        int goalkeepersCount,

        @Min(5)
        @Max(12)
        int defendersCount,

        @Min(5)
        @Max(12)
        int midfieldersCount,

        @Min(3)
        @Max(10)
        int forwardsCount,

        @Min(3)
        @Max(5)
        int maxSubstitutions,

        boolean defenseModifier,

        boolean captainBonus,

        @Pattern(
                regexp = "FANTACALCIO|GAZZETTA|FANTAMARKT|AVERAGE"
        )
        String voteSource
) {
}