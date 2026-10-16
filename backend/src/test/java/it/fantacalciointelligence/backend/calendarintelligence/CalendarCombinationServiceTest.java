package it.fantacalciointelligence.backend.calendarintelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class CalendarCombinationServiceTest {
    @Test void fixtureProfilesAreLoadedOnceNotPerPair() {
        FixtureWindowService loader=mock(FixtureWindowService.class);
        List<Long> ids=List.of(1L,2L,3L); when(loader.loadClubProfiles("2026/2027",ids,1,5)).thenReturn(profiles(ids));
        var result=new CalendarCombinationService(loader,new CalendarCombinationScorer()).evaluatePairs("2026/2027",ids,1,5);
        assertThat(result).hasSize(3); verify(loader,times(1)).loadClubProfiles("2026/2027",ids,1,5);
    }
    @Test void triplesAreUniqueAndGeneratedWithoutPerTripleLoad() {
        FixtureWindowService loader=mock(FixtureWindowService.class);List<Long> ids=List.of(1L,2L,3L,4L);
        when(loader.loadClubProfiles("2026/2027",ids,1,3)).thenReturn(profiles(ids));
        var result=new CalendarCombinationService(loader,new CalendarCombinationScorer()).evaluateTriples("2026/2027",ids,1,3);
        assertThat(result).hasSize(4);assertThat(result).extracting(CalendarCombinationResult::clubIds).doesNotHaveDuplicates();verify(loader).loadClubProfiles("2026/2027",ids,1,3);
    }
    @Test void pairPoolCapIsExplicitAndNeverSilentlyTruncated() {
        var service=new CalendarCombinationService(mock(FixtureWindowService.class),new CalendarCombinationScorer());
        List<Long> ids=LongStream.rangeClosed(1,31).boxed().toList();
        assertThatThrownBy(()->service.evaluatePairs("2026/2027",ids,1,5)).hasMessageContaining("maximum 30");
    }
    @Test void triplePoolCapIsExplicitAndNeverSilentlyTruncated() {
        var service=new CalendarCombinationService(mock(FixtureWindowService.class),new CalendarCombinationScorer());
        List<Long> ids=LongStream.rangeClosed(1,19).boxed().toList();
        assertThatThrownBy(()->service.evaluateTriples("2026/2027",ids,1,5)).hasMessageContaining("maximum 18");
    }
    @Test void duplicateCandidatesAreRejected() {
        var service=new CalendarCombinationService(mock(FixtureWindowService.class),new CalendarCombinationScorer());
        assertThatThrownBy(()->service.evaluatePairs("2026/2027",List.of(1L,1L),1,5)).hasMessageContaining("Duplicate");
    }
    private Map<Long,ClubFixtureProfile> profiles(List<Long> ids){Map<Long,ClubFixtureProfile> result=new LinkedHashMap<>();for(Long id:ids){Map<Integer,ClubFixtureDay> days=new LinkedHashMap<>();for(int d=1;d<=5;d++){var difficulty=(id+d)%2==0?CalendarDifficulty.EASY:CalendarDifficulty.HARD;days.put(d,new ClubFixtureDay(id,d,id*100+d,true,"SCHEDULED",null,BigDecimal.valueOf(difficulty==CalendarDifficulty.EASY?35:80),difficulty));}result.put(id,new ClubFixtureProfile(id,"C"+id,days));}return result;}
}
