package it.fantacalciointelligence.backend.strategicintelligence;

public record StrategicProfileInput(Long playerId, String season, String fantasyRole,
        BonusOpportunityInput bonus, RoleAdvantageInput role,
        AerialThreatInput aerial, UpsideInput upside) {}
