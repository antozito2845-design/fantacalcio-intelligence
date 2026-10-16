package it.fantacalciointelligence.backend.lineup;

public record LineupSubstitutionResponse(
        LineupPlayerResponse outgoingPlayer,
        LineupPlayerResponse incomingPlayer,
        String reason
) {
}