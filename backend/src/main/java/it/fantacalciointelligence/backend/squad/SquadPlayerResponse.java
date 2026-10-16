package it.fantacalciointelligence.backend.squad;

public record SquadPlayerResponse(
        Long id,
        Long playerId,
        String playerName,
        String club,
        String role,
        int purchasePrice
) {
}