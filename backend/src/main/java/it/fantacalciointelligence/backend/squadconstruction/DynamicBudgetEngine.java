package it.fantacalciointelligence.backend.squadconstruction;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import it.fantacalciointelligence.backend.common.BadRequestException;

@Component
public class DynamicBudgetEngine {

    static final List<String> ROLES = List.of("P", "D", "C", "A");

    public DynamicBudgetResponse calculate(SquadConstructionRequest request) {
        validate(request);
        Map<String, Integer> slots = normalizedSlots(request.rosterSlots());
        Map<String, Integer> filled = new LinkedHashMap<>();
        Map<String, Integer> spent = new LinkedHashMap<>();
        ROLES.forEach(role -> { filled.put(role, 0); spent.put(role, 0); });

        for (BoughtPlayerRequest player : safePlayers(request.playersAlreadyBought())) {
            String role = normalizeRole(player.role());
            if (player.pricePaid() < 0) throw new BadRequestException("Il prezzo pagato non può essere negativo");
            filled.put(role, filled.get(role) + 1);
            spent.put(role, spent.get(role) + player.pricePaid());
        }

        Map<String, Integer> remaining = new LinkedHashMap<>();
        int totalRemaining = 0;
        for (String role : ROLES) {
            if (filled.get(role) > slots.get(role)) throw new BadRequestException("Slot occupati superiori agli slot rosa per " + role);
            int count = slots.get(role) - filled.get(role);
            remaining.put(role, count);
            totalRemaining += count;
        }
        int reserve = totalRemaining;
        if (request.remainingBudget() < reserve) throw new BadRequestException("Budget residuo insufficiente per completare la rosa");

        Map<String, Integer> baseline = baseline(request.initialBudget(), request.defenseModifier());
        int completedExpected = ROLES.stream().filter(role -> phaseIndex(role) < phaseIndex(request.currentPhaseRole()))
                .mapToInt(baseline::get).sum();
        int completedSpent = ROLES.stream().filter(role -> phaseIndex(role) < phaseIndex(request.currentPhaseRole()))
                .mapToInt(spent::get).sum();
        int savings = completedExpected - completedSpent;

        int discretionary = request.remainingBudget() - reserve;
        int openWeight = ROLES.stream().filter(role -> remaining.get(role) > 0).mapToInt(baseline::get).sum();
        Map<String, Integer> targets = new LinkedHashMap<>();
        int assigned = 0;
        List<String> open = ROLES.stream().filter(role -> remaining.get(role) > 0).toList();
        for (int i = 0; i < open.size(); i++) {
            String role = open.get(i);
            int target = i == open.size() - 1
                    ? request.remainingBudget() - assigned
                    : remaining.get(role) + (openWeight == 0 ? 0 : discretionary * baseline.get(role) / openWeight);
            targets.put(role, target);
            assigned += target;
        }
        ROLES.forEach(role -> targets.putIfAbsent(role, 0));

        String phase = normalizeRole(request.currentPhaseRole());
        int futureProtected = ROLES.stream().filter(role -> phaseIndex(role) > phaseIndex(phase)).mapToInt(targets::get).sum();
        int currentAvailable = Math.max(0, request.remainingBudget() - futureProtected);
        int currentRoleMinimumReserve =
                Math.max(0, remaining.get(phase) - 1);

        // Future role targets already include their completion reserve.
        // Protect here only the other slots in the current role.
        int maxBid = remaining.get(phase) == 0
                ? 0
                : Math.max(
                        1,
                        currentAvailable
                                - currentRoleMinimumReserve
                );

        return new DynamicBudgetResponse(reserve, futureProtected,
                currentRoleMinimumReserve, currentAvailable, maxBid,
                Map.copyOf(filled), Map.copyOf(remaining), Map.copyOf(targets), savings);
    }

    private Map<String, Integer> baseline(int budget, boolean modifier) {
        int[] weights = modifier ? new int[]{7, 15, 23, 55} : new int[]{6, 10, 25, 59};
        Map<String, Integer> result = new LinkedHashMap<>();
        int used = 0;
        for (int i = 0; i < ROLES.size(); i++) {
            int value = i == ROLES.size() - 1 ? budget - used : budget * weights[i] / 100;
            result.put(ROLES.get(i), value); used += value;
        }
        return result;
    }

    private Map<String, Integer> normalizedSlots(Map<String, Integer> supplied) {
        Map<String, Integer> result = new LinkedHashMap<>(Map.of("P", 3, "D", 8, "C", 8, "A", 6));
        if (supplied != null && !supplied.isEmpty()) {
            for (var entry : supplied.entrySet()) {
                String role = normalizeRole(entry.getKey());
                if (entry.getValue() == null || entry.getValue() < 0) throw new BadRequestException("Gli slot non possono essere negativi");
                result.put(role, entry.getValue());
            }
        }
        return result;
    }

    private void validate(SquadConstructionRequest r) {
        if (r == null || r.season() == null || r.season().isBlank()) throw new BadRequestException("La stagione è obbligatoria");
        if (r.initialBudget() < 150 || r.initialBudget() > 1000) throw new BadRequestException("Il budget iniziale deve essere compreso tra 150 e 1000");
        if (r.remainingBudget() < 0 || r.remainingBudget() > r.initialBudget()) throw new BadRequestException("Budget residuo non coerente");
        if (r.teams() < 6 || r.teams() > 12) throw new BadRequestException("Le squadre devono essere comprese tra 6 e 12");
        normalizeRole(r.currentPhaseRole());
    }

    static String normalizeRole(String role) {
        if (role == null || !ROLES.contains(role.trim().toUpperCase())) throw new BadRequestException("Il ruolo deve essere P, D, C oppure A");
        return role.trim().toUpperCase();
    }
    static int phaseIndex(String role) { return ROLES.indexOf(normalizeRole(role)); }
    private List<BoughtPlayerRequest> safePlayers(List<BoughtPlayerRequest> players) { return players == null ? List.of() : players; }
}
