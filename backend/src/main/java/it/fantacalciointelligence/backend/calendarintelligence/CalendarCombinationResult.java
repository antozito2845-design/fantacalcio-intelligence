package it.fantacalciointelligence.backend.calendarintelligence;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record CalendarCombinationResult(List<Long> clubIds, BigDecimal calendarCombinationScore,
        CalendarCombinationStatus status, BigDecimal confidence, int favorableCoverageCount,
        int hardOverlapCount, BigDecimal simultaneousHardRate, int complementaryMatchdays,
        List<Integer> missingMatchdays, int fromMatchday, int toMatchday,
        Map<Integer, Long> preferredClubByMatchday, List<String> reasons) {}
