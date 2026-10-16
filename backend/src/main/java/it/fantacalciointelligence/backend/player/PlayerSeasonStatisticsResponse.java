package it.fantacalciointelligence.backend.player;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record PlayerSeasonStatisticsResponse(
        String season,
        String source,
        String sourceExternalId,
        String club,
        List<String> historicalClubs,
        Integer appearances,
        Integer starts,
        Integer minutesPlayed,
        BigDecimal averageRating,
        BigDecimal fantasyAverage,
        Integer goals,
        Integer assists,
        Integer penaltiesTaken,
        Integer penaltiesScored,
        Integer penaltiesMissed,
        Integer yellowCards,
        Integer redCards,
        Integer cleanSheets,
        Integer goalsConceded,
        Integer saves,
        Integer penaltiesSaved,
        BigDecimal expectedGoals,
        BigDecimal expectedAssists,
        BigDecimal goalsPer90,
        BigDecimal assistsPer90,
        BigDecimal minutesPerAppearance,
        BigDecimal startPercentage,
        OffsetDateTime importedAt,
        OffsetDateTime sourceUpdatedAt,
        Long importBatchId
) {
}
