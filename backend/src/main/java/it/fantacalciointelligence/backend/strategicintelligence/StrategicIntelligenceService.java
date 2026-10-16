package it.fantacalciointelligence.backend.strategicintelligence;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class StrategicIntelligenceService {
    private final BonusOpportunityService bonusService;
    private final RoleAdvantageService roleService;
    private final AerialThreatService aerialService;
    private final UpsideIntelligenceService upsideService;

    public StrategicIntelligenceService(BonusOpportunityService bonusService,
            RoleAdvantageService roleService, AerialThreatService aerialService,
            UpsideIntelligenceService upsideService) {
        this.bonusService = bonusService;
        this.roleService = roleService;
        this.aerialService = aerialService;
        this.upsideService = upsideService;
    }

    public StrategicIntelligenceProfile calculate(StrategicProfileInput input) {
        StrategicSignal bonus = bonusService.calculate(input.bonus());
        RoleAdvantageInput roleInput = input.role();
        StrategicSignal role = roleService.calculate(new RoleAdvantageInput(roleInput.fantasyRole(),
                roleInput.realPosition(), roleInput.positionConfidence(), roleInput.minutes(), roleInput.starts(),
                bonus, roleInput.sources(), roleInput.sourceConflict()));
        StrategicSignal aerial = aerialService.calculate(input.aerial());
        UpsideInput upsideInput = input.upside();
        StrategicSignal upside = upsideService.calculate(new UpsideInput(upsideInput.potentialScore(),
                upsideInput.reliabilityScore(), upsideInput.riskScore(), upsideInput.starterProbability(),
                upsideInput.marketGap(), role, bonus, upsideInput.activeRegistration(), upsideInput.sources()));
        List<StrategicSignal> signals = List.of(bonus, role, aerial, upside);
        long available = signals.stream().filter(signal -> signal.score() != null).count();
        LinkedHashSet<String> missing = new LinkedHashSet<>();
        LinkedHashSet<String> sources = new LinkedHashSet<>();
        LinkedHashSet<String> conflicts = new LinkedHashSet<>();
        for (StrategicSignal signal : signals) {
            missing.addAll(signal.missingInputs()); sources.addAll(signal.sourceReferences());
            signal.reasons().stream().filter("SOURCE_CONFLICT_SURFACED"::equals).forEach(conflicts::add);
        }
        return new StrategicIntelligenceProfile(input.playerId(), input.season(), input.fantasyRole(),
                bonus, role, aerial, upside,
                BigDecimal.valueOf(available * 25).setScale(2, RoundingMode.HALF_UP),
                new ArrayList<>(missing), new ArrayList<>(sources), new ArrayList<>(conflicts));
    }
}
