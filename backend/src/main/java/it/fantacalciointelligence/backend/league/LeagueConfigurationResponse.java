package it.fantacalciointelligence.backend.league;

public record LeagueConfigurationResponse(
        Long id,
        String name,
        String season,
        int teamsCount,
        int totalBudget,
        int goalkeepersCount,
        int defendersCount,
        int midfieldersCount,
        int forwardsCount,
        int maxSubstitutions,
        boolean defenseModifier,
        boolean captainBonus,
        String voteSource,
        boolean active
) {
}