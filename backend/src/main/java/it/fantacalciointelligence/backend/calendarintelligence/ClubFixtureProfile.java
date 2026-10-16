package it.fantacalciointelligence.backend.calendarintelligence;

import java.util.Map;

public record ClubFixtureProfile(Long clubId, String clubName, Map<Integer, ClubFixtureDay> matchdays) {}
