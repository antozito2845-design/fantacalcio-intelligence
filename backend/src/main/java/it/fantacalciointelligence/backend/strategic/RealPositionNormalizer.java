package it.fantacalciointelligence.backend.strategic;

import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RealPositionNormalizer {
    private static final Map<String, RealPosition> EXACT = Map.ofEntries(
            Map.entry("GK", RealPosition.GK), Map.entry("GOALKEEPER", RealPosition.GK),
            Map.entry("CB", RealPosition.CB), Map.entry("CENTRE-BACK", RealPosition.CB), Map.entry("CENTER-BACK", RealPosition.CB),
            Map.entry("RB", RealPosition.RB), Map.entry("RIGHT-BACK", RealPosition.RB),
            Map.entry("LB", RealPosition.LB), Map.entry("LEFT-BACK", RealPosition.LB),
            Map.entry("RWB", RealPosition.RWB), Map.entry("RIGHT WING-BACK", RealPosition.RWB),
            Map.entry("LWB", RealPosition.LWB), Map.entry("LEFT WING-BACK", RealPosition.LWB),
            Map.entry("DM", RealPosition.DM), Map.entry("DEFENSIVE MIDFIELD", RealPosition.DM),
            Map.entry("CM", RealPosition.CM), Map.entry("CENTRAL MIDFIELD", RealPosition.CM),
            Map.entry("AM", RealPosition.AM), Map.entry("ATTACKING MIDFIELD", RealPosition.AM),
            Map.entry("RW", RealPosition.RW), Map.entry("RIGHT WINGER", RealPosition.RW),
            Map.entry("LW", RealPosition.LW), Map.entry("LEFT WINGER", RealPosition.LW),
            Map.entry("SS", RealPosition.SS), Map.entry("SECOND STRIKER", RealPosition.SS),
            Map.entry("CF", RealPosition.CF), Map.entry("CENTRE-FORWARD", RealPosition.CF),
            Map.entry("ST", RealPosition.ST), Map.entry("STRIKER", RealPosition.ST),
            Map.entry("UTILITY", RealPosition.UTILITY)
    );

    public RealPosition normalize(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) return RealPosition.UNKNOWN;
        return EXACT.getOrDefault(rawValue.trim().toUpperCase(Locale.ROOT), RealPosition.UNKNOWN);
    }
}
