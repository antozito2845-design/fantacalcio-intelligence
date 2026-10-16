package it.fantacalciointelligence.backend.calendarintelligence;

import java.math.BigDecimal;

public enum CalendarDifficulty {
    EASY, MEDIUM, HARD, UNKNOWN;

    public static CalendarDifficulty fromScore(BigDecimal score) {
        if (score == null) return UNKNOWN;
        if (score.compareTo(BigDecimal.valueOf(50)) < 0) return EASY;
        if (score.compareTo(BigDecimal.valueOf(70)) < 0) return MEDIUM;
        return HARD;
    }
}
