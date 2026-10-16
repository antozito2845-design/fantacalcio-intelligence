package it.fantacalciointelligence.backend.player;

import java.math.BigDecimal;

public record PlayerTacticalProfileResponse(
        String realPosition,
        String secondaryPosition,
        String tacticalRole,
        BigDecimal attackingInvolvement,
        BigDecimal defensiveInvolvement,
        boolean setPieceTaker,
        boolean penaltyTaker,
        boolean cornerTaker,
        boolean freeKickTaker,
        boolean projectedStarter,
        BigDecimal starterProbability,
        String competitionLevel,
        String tacticalNotes
) {
}