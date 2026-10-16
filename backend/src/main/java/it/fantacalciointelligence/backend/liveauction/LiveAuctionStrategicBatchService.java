package it.fantacalciointelligence.backend.liveauction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.squadconstruction.SquadTargetResponse;
import it.fantacalciointelligence.backend.strategic.RealPosition;
import it.fantacalciointelligence.backend.strategicintelligence.AerialThreatInput;
import it.fantacalciointelligence.backend.strategicintelligence.BonusOpportunityInput;
import it.fantacalciointelligence.backend.strategicintelligence.RoleAdvantageInput;
import it.fantacalciointelligence.backend.strategicintelligence.StrategicIntelligenceProfile;
import it.fantacalciointelligence.backend.strategicintelligence.StrategicIntelligenceService;
import it.fantacalciointelligence.backend.strategicintelligence.StrategicProfileInput;
import it.fantacalciointelligence.backend.strategicintelligence.UpsideInput;

@Service
public class LiveAuctionStrategicBatchService {
    private final JdbcClient jdbcClient;
    private final StrategicIntelligenceService intelligenceService;

    public LiveAuctionStrategicBatchService(JdbcClient jdbcClient,
            StrategicIntelligenceService intelligenceService) {
        this.jdbcClient = jdbcClient;
        this.intelligenceService = intelligenceService;
    }

    @Transactional(readOnly = true)
    public Map<Long, StrategicIntelligenceProfile> load(String season,
            List<SquadTargetResponse> candidates) {
        if (candidates.isEmpty()) return Map.of();
        List<Long> ids = candidates.stream().map(SquadTargetResponse::playerId).distinct().toList();
        List<Row> rows = jdbcClient.sql("""
                SELECT o.player_id, o.source, o.confidence_score, o.real_position_normalized,
                       o.penalty_hierarchy, o.direct_free_kick_hierarchy,
                       o.indirect_free_kick_hierarchy, o.corner_hierarchy, o.set_piece_shared,
                       o.shots, o.expected_goals, o.expected_assists, o.touches_in_box,
                       o.key_passes, o.aerial_duels, o.aerial_duels_won, o.headed_shots,
                       o.headed_goals, o.headed_expected_goals, o.offensive_box_touches,
                       o.set_piece_box_presence, o.corner_target_frequency, o.starts,
                       o.minutes_played, o.starter_probability,
                       evaluation.potential_score, evaluation.reliability_score, evaluation.risk_score
                FROM player_strategic_data_observations o
                JOIN seasons season ON season.id=o.season_id AND season.code=:season
                LEFT JOIN player_evaluations evaluation
                       ON evaluation.player_id=o.player_id AND evaluation.season_id=season.id
                WHERE o.player_id IN (:ids)
                ORDER BY o.player_id, o.source, o.observed_at DESC NULLS LAST, o.id
                """).param("season", season).param("ids", ids).query((rs, n) -> new Row(
                        rs.getLong("player_id"), rs.getString("source"), rs.getBigDecimal("confidence_score"),
                        position(rs.getString("real_position_normalized")), rs.getString("penalty_hierarchy"),
                        rs.getString("direct_free_kick_hierarchy"), rs.getString("indirect_free_kick_hierarchy"),
                        rs.getString("corner_hierarchy"), (Boolean) rs.getObject("set_piece_shared"),
                        integer(rs.getObject("shots")), rs.getBigDecimal("expected_goals"),
                        rs.getBigDecimal("expected_assists"), integer(rs.getObject("touches_in_box")),
                        integer(rs.getObject("key_passes")), integer(rs.getObject("aerial_duels")),
                        integer(rs.getObject("aerial_duels_won")), integer(rs.getObject("headed_shots")),
                        integer(rs.getObject("headed_goals")), rs.getBigDecimal("headed_expected_goals"),
                        integer(rs.getObject("offensive_box_touches")), rs.getBigDecimal("set_piece_box_presence"),
                        rs.getBigDecimal("corner_target_frequency"), integer(rs.getObject("starts")),
                        integer(rs.getObject("minutes_played")), rs.getBigDecimal("starter_probability"),
                        rs.getBigDecimal("potential_score"), rs.getBigDecimal("reliability_score"),
                        rs.getBigDecimal("risk_score"))).list();
        Map<Long, List<Row>> grouped = rows.stream().collect(Collectors.groupingBy(Row::playerId,
                LinkedHashMap::new, Collectors.toList()));
        Map<Long, SquadTargetResponse> targets = candidates.stream().collect(Collectors.toMap(
                SquadTargetResponse::playerId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Map<Long, StrategicIntelligenceProfile> result = new LinkedHashMap<>();
        grouped.forEach((id, observations) -> result.put(id,
                intelligenceService.calculate(input(season, targets.get(id), observations))));
        return Map.copyOf(result);
    }

    private StrategicProfileInput input(String season, SquadTargetResponse target, List<Row> rows) {
        boolean conflict = conflict(rows);
        List<String> sources = rows.stream().map(Row::source).distinct().toList();
        Row r = rows.getFirst();
        Integer minutes = value(rows, Row::minutes);
        BigDecimal shots90 = rate(value(rows, Row::shots), minutes);
        BigDecimal xg90 = rate(value(rows, Row::expectedGoals), minutes);
        BigDecimal xa90 = rate(value(rows, Row::expectedAssists), minutes);
        BigDecimal confidence = average(rows, Row::confidence);
        var bonus = new BonusOpportunityInput(value(rows, Row::penaltyHierarchy),
                value(rows, Row::directFreeKickHierarchy), value(rows, Row::indirectFreeKickHierarchy),
                value(rows, Row::cornerHierarchy), value(rows, Row::setPieceShared), xg90, xa90, shots90,
                value(rows, Row::touchesInBox), value(rows, Row::keyPasses), minutes,
                average(rows, Row::starterProbability), confidence, sources, conflict);
        var role = new RoleAdvantageInput(target.role(), value(rows, Row::position), confidence,
                minutes, value(rows, Row::starts), null, sources, conflict);
        var aerial = new AerialThreatInput(value(rows, Row::headedShots), value(rows, Row::headedGoals),
                value(rows, Row::headedExpectedGoals), value(rows, Row::offensiveBoxTouches),
                average(rows, Row::setPieceBoxPresence), average(rows, Row::cornerTargetFrequency),
                value(rows, Row::aerialDuels), value(rows, Row::aerialDuelsWon), minutes, confidence,
                sources, conflict);
        var upside = new UpsideInput(r.potential(), r.reliability(), r.risk(),
                average(rows, Row::starterProbability),
                BigDecimal.valueOf(target.targetPrice() - target.b51RecommendedPrice()),
                null, null, true, sources);
        return new StrategicProfileInput(target.playerId(), season, target.role(), bonus, role, aerial, upside);
    }

    private boolean conflict(List<Row> rows) {
        return differs(rows, Row::position) || differs(rows, Row::penaltyHierarchy)
                || differs(rows, Row::directFreeKickHierarchy) || differs(rows, Row::cornerHierarchy);
    }

    private <T> boolean differs(List<Row> rows, Function<Row, T> field) {
        return rows.stream().map(field).filter(Objects::nonNull).distinct().limit(2).count() > 1;
    }

    private <T> T value(List<Row> rows, Function<Row, T> field) {
        List<T> values = rows.stream().map(field).filter(Objects::nonNull).distinct().limit(2).toList();
        return values.size() == 1 ? values.getFirst() : null;
    }

    private BigDecimal average(List<Row> rows, Function<Row, BigDecimal> field) {
        List<BigDecimal> values = rows.stream().map(field).filter(Objects::nonNull).toList();
        if (values.isEmpty()) return null;
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 2, java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal rate(Integer total, Integer minutes) {
        return total == null || minutes == null || minutes <= 0 ? null : BigDecimal.valueOf(total).multiply(BigDecimal.valueOf(90))
                .divide(BigDecimal.valueOf(minutes), 4, java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal rate(BigDecimal total, Integer minutes) {
        return total == null || minutes == null || minutes <= 0 ? null : total.multiply(BigDecimal.valueOf(90))
                .divide(BigDecimal.valueOf(minutes), 4, java.math.RoundingMode.HALF_UP);
    }

    private static Integer integer(Object value) { return value instanceof Number n ? n.intValue() : null; }
    private static RealPosition position(String value) { return value == null ? null : RealPosition.valueOf(value); }

    private record Row(Long playerId, String source, BigDecimal confidence, RealPosition position,
            String penaltyHierarchy, String directFreeKickHierarchy, String indirectFreeKickHierarchy,
            String cornerHierarchy, Boolean setPieceShared, Integer shots, BigDecimal expectedGoals,
            BigDecimal expectedAssists, Integer touchesInBox, Integer keyPasses, Integer aerialDuels,
            Integer aerialDuelsWon, Integer headedShots, Integer headedGoals, BigDecimal headedExpectedGoals,
            Integer offensiveBoxTouches, BigDecimal setPieceBoxPresence, BigDecimal cornerTargetFrequency,
            Integer starts, Integer minutes, BigDecimal starterProbability, BigDecimal potential,
            BigDecimal reliability, BigDecimal risk) { }
}
