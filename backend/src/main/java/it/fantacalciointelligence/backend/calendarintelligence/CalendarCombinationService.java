package it.fantacalciointelligence.backend.calendarintelligence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CalendarCombinationService {
    public static final int MAX_PAIR_POOL = 30;
    public static final int MAX_TRIPLE_POOL = 18;
    private final FixtureWindowService windowService;
    private final CalendarCombinationScorer scorer;

    public CalendarCombinationService(FixtureWindowService windowService, CalendarCombinationScorer scorer) {
        this.windowService=windowService; this.scorer=scorer;
    }
    public List<CalendarCombinationResult> evaluatePairs(String season,List<Long> clubIds,int from,int horizon) {
        guard(clubIds,MAX_PAIR_POOL,"pair"); Map<Long,ClubFixtureProfile> profiles=windowService.loadClubProfiles(season,clubIds,from,horizon);
        List<Long> ids=profiles.keySet().stream().sorted().toList(); List<CalendarCombinationResult> out=new ArrayList<>();
        for(int a=0;a<ids.size();a++)for(int b=a+1;b<ids.size();b++)out.add(scorer.evaluate(List.of(profiles.get(ids.get(a)),profiles.get(ids.get(b))),from,horizon));
        return sorted(out);
    }
    public List<CalendarCombinationResult> evaluateTriples(String season,List<Long> clubIds,int from,int horizon) {
        guard(clubIds,MAX_TRIPLE_POOL,"triple"); Map<Long,ClubFixtureProfile> profiles=windowService.loadClubProfiles(season,clubIds,from,horizon);
        List<Long> ids=profiles.keySet().stream().sorted().toList(); List<CalendarCombinationResult> out=new ArrayList<>();
        for(int a=0;a<ids.size();a++)for(int b=a+1;b<ids.size();b++)for(int c=b+1;c<ids.size();c++)
            out.add(scorer.evaluate(List.of(profiles.get(ids.get(a)),profiles.get(ids.get(b)),profiles.get(ids.get(c))),from,horizon));
        return sorted(out);
    }
    private void guard(List<Long> ids,int max,String type){if(ids==null||ids.size()<2)throw new IllegalArgumentException("At least two clubs required");if(ids.stream().distinct().count()!=ids.size())throw new IllegalArgumentException("Duplicate clubs");if(ids.size()>max)throw new IllegalArgumentException(type+" candidate pool exceeds explicit maximum "+max);}
    private List<CalendarCombinationResult> sorted(List<CalendarCombinationResult> values){return values.stream().sorted(Comparator
            .comparing(CalendarCombinationResult::calendarCombinationScore,Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(CalendarCombinationResult::confidence,Comparator.reverseOrder())
            .thenComparing(v->v.clubIds().toString())).toList();}
}
