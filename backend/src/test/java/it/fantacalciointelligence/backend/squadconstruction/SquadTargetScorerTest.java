package it.fantacalciointelligence.backend.squadconstruction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import it.fantacalciointelligence.backend.squadconstruction.SquadTargetScorer.Candidate;

class SquadTargetScorerTest {
    private final SquadTargetScorer scorer = new SquadTargetScorer();
    private final DynamicBudgetResponse budget = new DynamicBudgetEngine().calculate(
            new SquadConstructionRequest("2026/2027", 8, 1000, 1000, false, "D", null, List.of(), 20));

    @Test
    void qualityValueAndFitRemainSeparate() {
        SquadTargetResponse result = scorer.score(candidate(80, 20), budget, "D");
        assertThat(result.qualityScore()).isEqualByComparingTo("80.00");
        assertThat(result.valueScore()).isNotEqualByComparingTo(result.qualityScore());
        assertThat(result.fitScore()).isEqualByComparingTo("70.00");
    }

    @Test
    void lowerPriceProducesHigherValueAtEqualQuality() {
        assertThat(scorer.score(candidate(80, 10), budget, "D").valueScore())
                .isGreaterThan(scorer.score(candidate(80, 40), budget, "D").valueScore());
    }

    @Test
    void overpayLowersPriorityAndRecommendation() {
        SquadTargetResponse fair = scorer.score(candidate(80, 10), budget, "D");
        SquadTargetResponse overpay = scorer.score(candidate(80, 200), budget, "D");
        assertThat(overpay.auctionPriorityScore()).isLessThan(fair.auctionPriorityScore());
        assertThat(overpay.recommendation()).isEqualTo("EVITARE SOVRAPREZZO");
    }

    @Test
    void unknownBonusAndRoleSignalsStayNull() {
        SquadTargetResponse result = scorer.score(candidate(80, 20), budget, "D");
        assertThat(result.bonusOpportunityScore()).isNull();
        assertThat(result.roleAdvantageScore()).isNull();
        assertThat(result.evidenceFlags().get("BONUS_OPPORTUNITY")).isEqualTo(EvidenceStatus.NOT_AVAILABLE);
    }

    @Test
    void presentBonusSignalsIncreaseScoreButAerialRemainsUnavailable() {
        Candidate candidate = new Candidate(1L, "Player", "Club", "D", 80, 70, 20,
                85d, 90d, true, true, false, true, 80d, "Esterno sinistro");
        SquadTargetResponse result = scorer.score(candidate, budget, "D");
        assertThat(result.bonusOpportunityScore()).isPositive();
        assertThat(result.roleAdvantageScore()).isEqualByComparingTo("90.00");
        assertThat(result.aerialThreatScore()).isNull();
        assertThat(result.evidenceFlags().get("AERIAL_THREAT")).isEqualTo(EvidenceStatus.NOT_AVAILABLE);
    }

    @Test
    void sameInputIsDeterministicAndLowConfidenceVisible() {
        SquadTargetResponse first = scorer.score(candidate(75, 15), budget, "D");
        SquadTargetResponse second = scorer.score(candidate(75, 15), budget, "D");
        assertThat(first).isEqualTo(second);
        assertThat(first.confidence()).isLessThanOrEqualTo(new java.math.BigDecimal("70.00"));
    }

    @Test
    void fitChangesForDifferentProfilesWhenBonusIsMissing() {
        RosterProfileContext roster = new RosterProfileContext(
                Map.of("STARTER", 3), 0, 3, false
        );
        Candidate starter = new Candidate(
                1L, "Starter", "Club", "D", 60, 70, 40,
                null, null, null, null, null, null, null, null
        );
        Candidate specialist = new Candidate(
                2L, "Specialist", "Club", "D", 60, 70, 40,
                null, null, true, false, false, false, null, null
        );

        SquadTargetResponse starterResult = scorer.score(
                starter, budget, "D", roster
        );
        SquadTargetResponse specialistResult = scorer.score(
                specialist, budget, "D", roster
        );

        assertThat(specialistResult.fitScore())
                .isGreaterThan(starterResult.fitScore());
        assertThat(specialistResult.auctionPriorityScore())
                .isGreaterThan(starterResult.auctionPriorityScore());
    }

    @Test
    void premiumNeedAndCoverageChangeFit() {
        Candidate premium = candidate(92, 60);
        Candidate depth = candidate(60, 40);
        RosterProfileContext premiumMissing = new RosterProfileContext(
                Map.of("STARTER", 3), 0, 3, false
        );
        RosterProfileContext premiumCovered = new RosterProfileContext(
                Map.of("PREMIUM", 1), 0, 3, false
        );

        assertThat(scorer.score(
                premium, budget, "D", premiumMissing
        ).fitScore()).isGreaterThan(scorer.score(
                depth, budget, "D", premiumMissing
        ).fitScore());

        assertThat(scorer.score(
                depth, budget, "D", premiumCovered
        ).fitScore()).isGreaterThan(scorer.score(
                premium, budget, "D", premiumCovered
        ).fitScore());
    }

    @Test
    void lastCheapSlotPrefersLowCostAndModifierStrengthensPremiumNeed() {
        Candidate premium = candidate(92, 200);
        Candidate lowCost = candidate(50, 1);
        RosterProfileContext lastSlot = new RosterProfileContext(
                Map.of("STARTER", 7), 0, 1, false
        );
        assertThat(scorer.score(
                lowCost, budget, "D", lastSlot
        ).fitScore()).isGreaterThan(scorer.score(
                premium, budget, "D", lastSlot
        ).fitScore());

        RosterProfileContext normal = new RosterProfileContext(
                Map.of(), 0, 4, false
        );
        RosterProfileContext modifier = new RosterProfileContext(
                Map.of(), 0, 4, true
        );
        assertThat(scorer.score(
                candidate(92, 60), budget, "D", modifier
        ).fitScore()).isGreaterThan(scorer.score(
                candidate(92, 60), budget, "D", normal
        ).fitScore());
    }

    @Test
    void averageSlotBudgetAndIndividualTargetAreSeparateAndB51Unchanged() {
        RosterProfileContext premiumMissing = new RosterProfileContext(
                Map.of(), 0, 3, false
        );
        SquadTargetResponse premium = scorer.score(
                candidate(92, 60), budget, "D", premiumMissing
        );
        assertThat(premium.b51RecommendedPrice()).isEqualTo(60);
        assertThat(premium.targetPrice())
                .isNotEqualTo(premium.averageSlotBudget())
                .isGreaterThan(premium.averageSlotBudget())
                .isLessThanOrEqualTo(premium.contextualMaxPrice());

        SquadTargetResponse value = scorer.score(
                candidate(70, 5), budget, "D", premiumMissing
        );
        assertThat(value.targetPrice()).isBetween(1, 6);
        assertThat(value.b51RecommendedPrice()).isEqualTo(5);
    }

    @Test
    void priorityRespondsIndependentlyToValueAndQuality() {
        SquadTargetResponse higherValue = scorer.score(
                candidate(70, 10), budget, "D"
        );
        SquadTargetResponse lowerValue = scorer.score(
                candidate(70, 12), budget, "D"
        );
        assertThat(higherValue.qualityScore())
                .isEqualByComparingTo(lowerValue.qualityScore());
        assertThat(higherValue.fitScore())
                .isEqualByComparingTo(lowerValue.fitScore());
        assertThat(higherValue.auctionPriorityScore())
                .isGreaterThan(lowerValue.auctionPriorityScore());

        SquadTargetResponse higherQuality = scorer.score(
                candidate(80, 16), budget, "D"
        );
        SquadTargetResponse lowerQuality = scorer.score(
                candidate(60, 12), budget, "D"
        );
        assertThat(higherQuality.valueScore())
                .isEqualByComparingTo(lowerQuality.valueScore());
        assertThat(higherQuality.fitScore())
                .isEqualByComparingTo(lowerQuality.fitScore());
        assertThat(higherQuality.auctionPriorityScore())
                .isGreaterThan(lowerQuality.auctionPriorityScore());
    }

    private Candidate candidate(double quality, int price) {
        return new Candidate(1L, "Player", "Club", "D", quality, 70, price,
                null, null, null, null, null, null, null, null);
    }
}
