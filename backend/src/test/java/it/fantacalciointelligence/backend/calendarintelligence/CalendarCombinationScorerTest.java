package it.fantacalciointelligence.backend.calendarintelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CalendarCombinationScorerTest {
    private final CalendarCombinationScorer scorer = new CalendarCombinationScorer();

    @Test void realDifficultyScaleIsNormalized() {
        assertThat(CalendarDifficulty.fromScore(new BigDecimal("49.99"))).isEqualTo(CalendarDifficulty.EASY);
        assertThat(CalendarDifficulty.fromScore(new BigDecimal("50"))).isEqualTo(CalendarDifficulty.MEDIUM);
        assertThat(CalendarDifficulty.fromScore(new BigDecimal("70"))).isEqualTo(CalendarDifficulty.HARD);
    }
    @Test void unknownStaysUnknown() { assertThat(CalendarDifficulty.fromScore(null)).isEqualTo(CalendarDifficulty.UNKNOWN); }
    @Test void hardEasyCreatesPositiveComplementarity() {
        var result=scorer.evaluate(List.of(profile(1,H,E,E),profile(2,E,H,M)),1,3);
        assertThat(result.complementaryMatchdays()).isEqualTo(2); assertThat(result.favorableCoverageCount()).isEqualTo(3);
    }
    @Test void simultaneousHardIsPenalized() {
        var overlap=scorer.evaluate(List.of(profile(1,H,H,E),profile(2,H,H,E)),1,3);
        var alternating=scorer.evaluate(List.of(profile(1,H,E,E),profile(2,E,H,E)),1,3);
        assertThat(overlap.hardOverlapCount()).isEqualTo(2); assertThat(overlap.calendarCombinationScore()).isLessThan(alternating.calendarCombinationScore());
    }
    @Test void complementaryPairBeatsSameDifficultPattern() {
        assertThat(scorer.evaluate(List.of(profile(1,H,E,H),profile(2,E,H,E)),1,3).calendarCombinationScore())
                .isGreaterThan(scorer.evaluate(List.of(profile(1,H,E,H),profile(2,H,E,H)),1,3).calendarCombinationScore());
    }
    @Test void pairOrderDoesNotMatter() {
        var a=profile(1,H,E,M);var b=profile(2,E,H,E);
        assertThat(scorer.evaluate(List.of(a,b),1,3)).isEqualTo(scorer.evaluate(List.of(b,a),1,3));
    }
    @Test void repeatedCalculationIsDeterministic() {
        var input=List.of(profile(1,H,E,M),profile(2,E,H,E)); assertThat(scorer.evaluate(input,1,3)).isEqualTo(scorer.evaluate(input,1,3));
    }
    @Test void tripleIsPermutationInvariant() {
        var a=profile(1,H,E,M);var b=profile(2,E,H,E);var c=profile(3,M,M,E);
        assertThat(scorer.evaluate(List.of(a,b,c),1,3)).isEqualTo(scorer.evaluate(List.of(c,a,b),1,3));
    }
    @Test void tripleCoverageBeatsAllHardOverlap() {
        var covered=scorer.evaluate(List.of(profile(1,E,H,H),profile(2,H,E,H),profile(3,H,H,E)),1,3);
        var hard=scorer.evaluate(List.of(profile(1,H,H,H),profile(2,H,H,H),profile(3,H,H,H)),1,3);
        assertThat(covered.calendarCombinationScore()).isGreaterThan(hard.calendarCombinationScore());
    }
    @Test void duplicateClubCannotCreatePairOrTriple() {
        assertThatThrownBy(()->scorer.evaluate(List.of(profile(1,E,E),profile(1,H,H)),1,2)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void goalkeeperPreferredOptionRequiresUniqueEasyAgainstHard() {
        var result=scorer.evaluate(List.of(profile(1,E,M),profile(2,H,H)),1,2);
        assertThat(result.preferredClubByMatchday()).containsEntry(1,1L).doesNotContainKey(2);
    }
    @Test void unknownNeverCreatesPreferredOption() {
        var result=scorer.evaluate(List.of(profile(1,U,E),profile(2,H,H)),1,2);
        assertThat(result.preferredClubByMatchday()).doesNotContainKey(1);
    }
    @Test void postponedIsSurfacedAsMissing() {
        var a=profile(1,E,E); var mutable=new LinkedHashMap<>(a.matchdays()); mutable.put(1,fixture(1,1,E,"POSTPONED"));
        var result=scorer.evaluate(List.of(new ClubFixtureProfile(1L,"C1",mutable),profile(2,H,H)),1,2);
        assertThat(result.missingMatchdays()).contains(1); assertThat(result.status()).isEqualTo(CalendarCombinationStatus.INSUFFICIENT_EVIDENCE);
    }
    @Test void insufficientWindowHasNullScore() {
        var result=scorer.evaluate(List.of(profile(1,U,E,U),profile(2,H,E,U)),1,3);
        assertThat(result.calendarCombinationScore()).isNull();
    }
    @Test void horizonIsValidatedAndCappedAtTen() {
        assertThatThrownBy(()->scorer.evaluate(List.of(profile(1,E,E),profile(2,H,H)),1,11)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void missingFixtureDoesNotBecomeMedium() {
        var a=new ClubFixtureProfile(1L,"C1",Map.of(1,fixture(1,1,E,"SCHEDULED")));
        var result=scorer.evaluate(List.of(a,profile(2,H,H)),1,2); assertThat(result.missingMatchdays()).contains(2);
    }
    @Test void sameClubCalendarIsReusedByPlayerLayerNotRecomputed() {
        var profile=profile(1,E,H,E); assertThat(profile.matchdays()).hasSize(3);
    }
    @Test void alternatingAttackWindowsAvoidHardStreak() {
        var alternating=scorer.evaluate(List.of(profile(1,H,E,H,E),profile(2,E,H,E,H)),1,4);
        var streak=scorer.evaluate(List.of(profile(1,H,H,E,E),profile(2,H,H,E,E)),1,4);
        assertThat(alternating.calendarCombinationScore()).isGreaterThan(streak.calendarCombinationScore());
    }

    private static final CalendarDifficulty E=CalendarDifficulty.EASY,M=CalendarDifficulty.MEDIUM,H=CalendarDifficulty.HARD,U=CalendarDifficulty.UNKNOWN;
    private ClubFixtureProfile profile(long id,CalendarDifficulty... values){Map<Integer,ClubFixtureDay> days=new LinkedHashMap<>();for(int i=0;i<values.length;i++)days.put(i+1,fixture(id,i+1,values[i],"SCHEDULED"));return new ClubFixtureProfile(id,"C"+id,Map.copyOf(days));}
    private ClubFixtureDay fixture(long id,int day,CalendarDifficulty value,String status){BigDecimal score=switch(value){case EASY->BigDecimal.valueOf(35);case MEDIUM->BigDecimal.valueOf(60);case HARD->BigDecimal.valueOf(80);case UNKNOWN->null;};return new ClubFixtureDay(id,day,(long)(id*100+day),true,status,null,score,value);}
}
