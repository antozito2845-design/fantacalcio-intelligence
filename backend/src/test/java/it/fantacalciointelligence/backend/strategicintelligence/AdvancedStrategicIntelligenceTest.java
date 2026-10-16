package it.fantacalciointelligence.backend.strategicintelligence;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import it.fantacalciointelligence.backend.strategic.RealPosition;

class AdvancedStrategicIntelligenceTest {
    private final BonusOpportunityService bonus = new BonusOpportunityService();
    private final RoleAdvantageService role = new RoleAdvantageService();
    private final AerialThreatService aerial = new AerialThreatService();
    private final UpsideIntelligenceService upside = new UpsideIntelligenceService();

    @Test void primaryPenaltyScoresAboveSecondary() {
        assertThat(bonus.calculate(bonusInput("PRIMARY", "0.30", "0.20")).score())
                .isGreaterThan(bonus.calculate(bonusInput("SECONDARY", "0.30", "0.20")).score());
    }
    @Test void unknownPenaltyDoesNotBecomeAccess() {
        var input = new BonusOpportunityInput("UNKNOWN",null,null,null,null,null,null,null,null,null,900,null,
                new BigDecimal("80"),List.of("FBREF"),false);
        assertThat(bonus.calculate(input).score()).isNull();
    }
    @Test void insufficientBonusEvidenceIsValidResult() {
        var input = new BonusOpportunityInput(null,null,null,null,null,new BigDecimal("0.20"),null,null,null,null,900,null,
                new BigDecimal("80"),List.of("FBREF"),false);
        assertThat(bonus.calculate(input).status())
                .isEqualTo(StrategicSignalStatus.INSUFFICIENT_EVIDENCE);
    }
    @Test void missingMinutesLeavesPer90EvidenceUnavailable() {
        BonusOpportunityInput input = new BonusOpportunityInput(null, null, null, null, null,
                null, null, null, 4, null, null, null, new BigDecimal("80"), List.of("FBREF"), false);
        assertThat(bonus.calculate(input).missingInputs()).contains("positiveMinutes");
    }
    @Test void conflictsAreSurfacedAndReduceConfidence() {
        StrategicSignal clean = bonus.calculate(bonusInput("PRIMARY", "0.30", "0.20"));
        BonusOpportunityInput conflicted = new BonusOpportunityInput("PRIMARY", null, null, null, null,
                new BigDecimal("0.30"), new BigDecimal("0.20"), null, null, null, 900, null,
                new BigDecimal("80"), List.of("FBREF","GAZZETTA"), true);
        StrategicSignal result = bonus.calculate(conflicted);
        assertThat(result.reasons()).contains("SOURCE_CONFLICT_SURFACED");
        assertThat(result.confidence()).isLessThan(clean.confidence());
    }
    @Test void defenderWingbackHasMoreAdvantageThanCentreBack() {
        assertThat(role.calculate(roleInput("D", RealPosition.RWB, 90)).score())
                .isGreaterThan(role.calculate(roleInput("D", RealPosition.CB, 90)).score());
    }
    @Test void midfielderAttackingPositionCanHaveAdvantage() {
        assertThat(role.calculate(roleInput("C", RealPosition.AM, 90)).category()).contains("ADVANTAGE");
    }
    @Test void unknownPositionNeverInventsAdvantage() {
        assertThat(role.calculate(roleInput("D", RealPosition.UNKNOWN, 90)).score()).isNull();
    }
    @Test void lowPositionConfidenceReducesFinalConfidence() {
        assertThat(role.calculate(roleInput("D", RealPosition.RWB, 30)).confidence())
                .isLessThan(role.calculate(roleInput("D", RealPosition.RWB, 90)).confidence());
    }
    @Test void fantasyRoleIsInputAndNeverMutated() {
        RoleAdvantageInput input = roleInput("D", RealPosition.RWB, 90);
        role.calculate(input); assertThat(input.fantasyRole()).isEqualTo("D");
    }
    @Test void heightAloneCannotCreateAerialThreat() {
        assertThat(aerial.calculate(new AerialThreatInput(null,null,null,null,null,null,null,null,900,null,List.of(),false)).score()).isNull();
    }
    @Test void genericAerialDuelsRemainPartialWithoutScore() {
        StrategicSignal result = aerial.calculate(new AerialThreatInput(null,null,null,null,null,null,40,25,900,null,List.of("FBREF"),false));
        assertThat(result.status()).isEqualTo(StrategicSignalStatus.PARTIAL); assertThat(result.score()).isNull();
    }
    @Test void headingAndSetPiecePresenceProduceStrongerSignal() {
        StrategicSignal result = aerial.calculate(new AerialThreatInput(12,2,new BigDecimal("1.8"),30,
                new BigDecimal("1.5"),new BigDecimal("1.2"),null,null,900,new BigDecimal("85"),List.of("FBREF"),false));
        assertThat(result.status()).isEqualTo(StrategicSignalStatus.AVAILABLE); assertThat(result.score()).isPositive();
    }
    @Test void headingMissingRemainsInsufficient() {
        assertThat(aerial.calculate(new AerialThreatInput(null,null,null,null,null,null,null,null,900,null,List.of(),false)).status())
                .isEqualTo(StrategicSignalStatus.INSUFFICIENT_EVIDENCE);
    }
    @Test void zeroMinutesPreventsAerialPer90Score() {
        assertThat(aerial.calculate(new AerialThreatInput(4,null,null,10,null,null,null,null,0,null,List.of(),false)).score()).isNull();
    }
    @Test void potentialAloneCannotCreateHighUpside() {
        assertThat(upside.calculate(new UpsideInput(new BigDecimal("95"),null,null,null,null,null,null,true,List.of())).score()).isNull();
    }
    @Test void supportedPotentialCanIncreaseUpside() {
        StrategicSignal advantage = role.calculate(roleInput("D", RealPosition.RWB, 90));
        StrategicSignal result = upside.calculate(new UpsideInput(new BigDecimal("90"),new BigDecimal("80"),new BigDecimal("10"),
                new BigDecimal("85"),new BigDecimal("20"),advantage,bonus.calculate(bonusInput("PRIMARY","0.3","0.2")),true,List.of("B50","B51")));
        assertThat(result.category()).isIn("HIGH_UPSIDE","MODERATE_UPSIDE");
    }
    @Test void highRiskReducesUpsideAndConfidence() {
        UpsideInput low = upsideInput(10); UpsideInput high = upsideInput(90);
        assertThat(upside.calculate(high).score()).isLessThan(upside.calculate(low).score());
        assertThat(upside.calculate(high).confidence()).isLessThan(upside.calculate(low).confidence());
    }
    @Test void inactiveRegistrationMakesUpsideInsufficient() {
        UpsideInput input = new UpsideInput(new BigDecimal("90"),new BigDecimal("80"),null,new BigDecimal("90"),new BigDecimal("10"),
                role.calculate(roleInput("D", RealPosition.RWB, 90)),null,false,List.of());
        assertThat(upside.calculate(input).status()).isEqualTo(StrategicSignalStatus.INSUFFICIENT_EVIDENCE);
    }
    @Test void repeatedCalculationIsDeterministic() {
        BonusOpportunityInput input = bonusInput("PRIMARY","0.3","0.2");
        assertThat(bonus.calculate(input)).isEqualTo(bonus.calculate(input));
    }
    @Test void resolverNeverArbitrarilyMergesConflicts() {
        var result = new StrategicEvidenceResolver().resolve(List.of("PRIMARY","SECONDARY"), value -> value);
        assertThat(result.conflict()).isTrue(); assertThat(result.observation()).isNull();
    }

    private BonusOpportunityInput bonusInput(String penalty, String xg, String xa) {
        return new BonusOpportunityInput(penalty,null,null,null,null,decimal(xg),decimal(xa),new BigDecimal("2.0"),null,10,900,
                new BigDecimal("80"),new BigDecimal("80"),List.of("FBREF"),false);
    }
    private RoleAdvantageInput roleInput(String fantasy, RealPosition position, int confidence) {
        return new RoleAdvantageInput(fantasy,position,new BigDecimal(confidence),900,10,null,List.of("MANUAL_VERIFIED"),false);
    }
    private UpsideInput upsideInput(int risk) {
        return new UpsideInput(new BigDecimal("85"),new BigDecimal("80"),new BigDecimal(risk),new BigDecimal("85"),new BigDecimal("15"),
                role.calculate(roleInput("D",RealPosition.RWB,90)),null,true,List.of("B50","B51"));
    }
    private BigDecimal decimal(String value) { return value == null ? null : new BigDecimal(value); }
}
