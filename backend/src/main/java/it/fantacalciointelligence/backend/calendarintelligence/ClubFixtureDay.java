package it.fantacalciointelligence.backend.calendarintelligence;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ClubFixtureDay(Long clubId, int matchday, Long fixtureId, boolean home,
        String fixtureStatus, OffsetDateTime kickoffAt, BigDecimal difficultyScore,
        CalendarDifficulty difficulty) {
    public boolean usable() {
        return difficulty != CalendarDifficulty.UNKNOWN
                && !"POSTPONED".equals(fixtureStatus) && !"CANCELLED".equals(fixtureStatus);
    }
}
