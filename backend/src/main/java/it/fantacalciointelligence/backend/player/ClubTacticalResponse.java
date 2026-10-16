package it.fantacalciointelligence.backend.player;

public record ClubTacticalResponse(
        String coach,
        String primaryFormation,
        String secondaryFormation,
        String playingStyle,
        String defensiveLine,
        String pressingLevel
) {
}