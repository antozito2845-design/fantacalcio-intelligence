package it.fantacalciointelligence.backend.squadanalysis;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.common.BadRequestException;
import it.fantacalciointelligence.backend.fixture.FixtureDifficultyResponse;
import it.fantacalciointelligence.backend.fixture.FixtureDifficultyService;
import it.fantacalciointelligence.backend.squad.SquadResponse;
import it.fantacalciointelligence.backend.squad.SquadRoleSummaryResponse;
import it.fantacalciointelligence.backend.squad.SquadService;

@Service
public class SquadAnalysisService {

    private static final List<String> ROLE_ORDER =
            List.of("P", "D", "C", "A");

    private final JdbcClient jdbcClient;
    private final SquadService squadService;
    private final FixtureDifficultyService fixtureDifficultyService;

    public SquadAnalysisService(
            JdbcClient jdbcClient,
            SquadService squadService,
            FixtureDifficultyService fixtureDifficultyService
    ) {
        this.jdbcClient = jdbcClient;
        this.squadService = squadService;
        this.fixtureDifficultyService =
                fixtureDifficultyService;
    }

    @Transactional(readOnly = true)
    public SquadAnalysisResponse analyzeActiveSquad(
            int fromMatchday,
            int toMatchday
    ) {
        SquadResponse squad =
                squadService.findActiveSquad();

        validateRequest(
                squad.id(),
                fromMatchday,
                toMatchday
        );

        return analyzeResolvedSquad(squad, fromMatchday, toMatchday);
    }

    @Transactional(readOnly = true)
    public SquadAnalysisResponse analyzeActiveSquad(
            int fromMatchday,
            int toMatchday,
            String username,
            boolean administrator
    ) {
        SquadResponse squad =
                squadService.findActiveSquad(username, administrator);

        validateRequest(
                squad.id(),
                fromMatchday,
                toMatchday
        );

        return analyzeResolvedSquad(squad, fromMatchday, toMatchday);
    }

    @Transactional(readOnly = true)
    public SquadAnalysisResponse analyzeSquad(
            Long squadId,
            int fromMatchday,
            int toMatchday
    ) {
        validateRequest(
                squadId,
                fromMatchday,
                toMatchday
        );

        SquadResponse squad =
                squadService.findSquad(squadId);

        return analyzeResolvedSquad(squad, fromMatchday, toMatchday);
    }

    @Transactional(readOnly = true)
    public SquadAnalysisResponse analyzeSquad(
            Long squadId,
            int fromMatchday,
            int toMatchday,
            String username,
            boolean administrator
    ) {
        validateRequest(
                squadId,
                fromMatchday,
                toMatchday
        );

        SquadResponse squad =
                squadService.findSquad(squadId, username, administrator);

        return analyzeResolvedSquad(squad, fromMatchday, toMatchday);
    }

    private SquadAnalysisResponse analyzeResolvedSquad(
            SquadResponse squad,
            int fromMatchday,
            int toMatchday
    ) {
        int remainingSlots =
                Math.max(
                        0,
                        squad.maximumPlayers()
                                - squad.totalPlayers()
                );

        List<String> globalWarnings =
                new ArrayList<>();

        List<SquadConstraintRoleResponse> roles =
                buildRoleConstraints(
                        squad,
                        globalWarnings
                );

        boolean budgetValid =
                squad.remainingBudget()
                        >= remainingSlots;

        if (!budgetValid) {
            globalWarnings.add(
                    "Il budget residuo non garantisce almeno "
                            + "un credito per ogni slot libero"
            );
        }

        boolean roleLimitsValid =
                roles.stream()
                        .allMatch(role ->
                                role.currentPlayers()
                                        <= role.maximumPlayers()
                        );

        boolean totalPlayersValid =
                squad.totalPlayers()
                        <= squad.maximumPlayers();

        boolean constraintsValid =
                budgetValid
                        && roleLimitsValid
                        && totalPlayersValid;

        List<SquadCalendarPlayerResponse> players =
                findCalendarPlayers(
                        squad,
                        fromMatchday,
                        toMatchday,
                        globalWarnings
                );

        List<SquadPairingResponse> pairings =
                buildPairings(players);

        addConcentrationWarnings(
                players,
                globalWarnings
        );

        boolean squadComplete =
                remainingSlots == 0
                        && roles.stream()
                                .allMatch(
                                        SquadConstraintRoleResponse
                                                ::complete
                                );

        if (squadComplete) {
            globalWarnings.add(
                    "La rosa rispetta tutti gli slot previsti "
                            + "dalla configurazione"
            );
        }

        if (players.isEmpty()
                && squad.totalPlayers() > 0) {

            globalWarnings.add(
                    "Non è stato possibile costruire l'analisi "
                            + "del calendario dei giocatori in rosa"
            );
        }

        String summary =
                buildSummary(
                        squad,
                        remainingSlots,
                        constraintsValid,
                        squadComplete,
                        pairings.size()
                );

        return new SquadAnalysisResponse(
                squad.id(),
                squad.name(),
                squad.season(),
                fromMatchday,
                toMatchday,
                squad.initialBudget(),
                squad.spentBudget(),
                squad.remainingBudget(),
                squad.totalPlayers(),
                squad.maximumPlayers(),
                remainingSlots,
                constraintsValid,
                squadComplete,
                summary,
                List.copyOf(roles),
                List.copyOf(players),
                List.copyOf(pairings),
                List.copyOf(globalWarnings)
        );
    }

    private List<SquadConstraintRoleResponse>
    buildRoleConstraints(
            SquadResponse squad,
            List<String> globalWarnings
    ) {
        List<SquadConstraintRoleResponse> response =
                new ArrayList<>();

        for (String role : ROLE_ORDER) {
            SquadRoleSummaryResponse roleSummary =
                    squad.roles().get(role);

            if (roleSummary == null) {
                globalWarnings.add(
                        "Configurazione assente per il ruolo "
                                + role
                );

                continue;
            }

            int current =
                    roleSummary.current();

            int maximum =
                    roleSummary.maximum();

            int remaining =
                    Math.max(
                            0,
                            roleSummary.remaining()
                    );

            boolean complete =
                    remaining == 0
                            && current == maximum;

            List<String> warnings =
                    new ArrayList<>();

            String coverageStatus;

            if (current > maximum) {
                coverageStatus =
                        "LIMITE SUPERATO";

                warnings.add(
                        "Il numero di giocatori supera "
                                + "il limite del ruolo"
                );
            }
            else if (complete) {
                coverageStatus =
                        "COMPLETO";
            }
            else if (current == 0) {
                coverageStatus =
                        "SCOPERTO";

                warnings.add(
                        "Nessun giocatore acquistato "
                                + "per questo ruolo"
                );
            }
            else if (remaining == 1) {
                coverageStatus =
                        "QUASI COMPLETO";
            }
            else {
                coverageStatus =
                        "IN COSTRUZIONE";
            }

            if ("D".equals(role)
                    && current < 4) {

                warnings.add(
                        "Copertura difensiva ancora insufficiente "
                                + "per gestire rotazioni e modificatore"
                );
            }

            if ("P".equals(role)
                    && current == 1) {

                warnings.add(
                        "È presente un solo portiere: "
                                + "manca una riserva"
                );
            }

            response.add(
                    new SquadConstraintRoleResponse(
                            role,
                            current,
                            maximum,
                            remaining,
                            complete,
                            coverageStatus,
                            List.copyOf(warnings)
                    )
            );
        }

        return response;
    }

    private List<SquadCalendarPlayerResponse>
    findCalendarPlayers(
            SquadResponse squad,
            int fromMatchday,
            int toMatchday,
            List<String> globalWarnings
    ) {
        List<PlayerRow> rows =
                jdbcClient.sql("""
                        SELECT
                            squad_player.player_id,
                            player.display_name,
                            club.name AS club_name,
                            club.slug AS club_slug,
                            squad_player.fantasy_role,
                            squad_player.purchase_price

                        FROM fantasy_squad_players squad_player

                        JOIN players player
                            ON player.id =
                                squad_player.player_id

                        JOIN player_registrations registration
                            ON registration.player_id =
                                squad_player.player_id
                            AND registration.season_id =
                                squad_player.season_id
                            AND registration.registration_status =
                                'ACTIVE'

                        JOIN clubs club
                            ON club.id =
                                registration.club_id

                        WHERE squad_player.squad_id =
                            :squadId

                        ORDER BY
                            CASE squad_player.fantasy_role
                                WHEN 'P' THEN 1
                                WHEN 'D' THEN 2
                                WHEN 'C' THEN 3
                                WHEN 'A' THEN 4
                                ELSE 5
                            END,
                            player.display_name
                        """)
                        .param(
                                "squadId",
                                squad.id()
                        )
                        .query((rs, rowNumber) ->
                                new PlayerRow(
                                        rs.getLong(
                                                "player_id"
                                        ),
                                        rs.getString(
                                                "display_name"
                                        ),
                                        rs.getString(
                                                "club_name"
                                        ),
                                        rs.getString(
                                                "club_slug"
                                        ),
                                        rs.getString(
                                                "fantasy_role"
                                        ),
                                        rs.getInt(
                                                "purchase_price"
                                        )
                                )
                        )
                        .list();

        List<SquadCalendarPlayerResponse> response =
                new ArrayList<>();

        for (PlayerRow row : rows) {
            List<FixtureDifficultyResponse> fixtures =
                    fixtureDifficultyService
                            .findClubFixtures(
                                    squad.season(),
                                    row.clubSlug(),
                                    fromMatchday,
                                    toMatchday
                            );

            double averageDifficulty =
                    fixtures.stream()
                            .mapToDouble(fixture ->
                                    fixture
                                            .difficultyScore()
                                            .doubleValue()
                            )
                            .average()
                            .orElse(60.0);

            int easyFixtures =
                    countDifficulty(
                            fixtures,
                            "FACILE"
                    );

            int mediumFixtures =
                    countDifficulty(
                            fixtures,
                            "MEDIA"
                    );

            int difficultFixtures =
                    countDifficulty(
                            fixtures,
                            "DIFFICILE"
                    );

            if (fixtures.isEmpty()) {
                globalWarnings.add(
                        "Calendario non disponibile per "
                                + row.playerName()
                                + " - "
                                + row.club()
                );
            }

            response.add(
                    new SquadCalendarPlayerResponse(
                            row.playerId(),
                            row.playerName(),
                            row.club(),
                            row.clubSlug(),
                            row.role(),
                            row.purchasePrice(),
                            decimal(averageDifficulty),
                            easyFixtures,
                            mediumFixtures,
                            difficultFixtures,
                            List.copyOf(fixtures)
                    )
            );
        }

        return response;
    }

    private List<SquadPairingResponse> buildPairings(
            List<SquadCalendarPlayerResponse> players
    ) {
        List<PairingCandidate> candidates =
                new ArrayList<>();

        for (String role : ROLE_ORDER) {
            List<SquadCalendarPlayerResponse> rolePlayers =
                    players.stream()
                            .filter(player ->
                                    role.equals(
                                            player.role()
                                    )
                            )
                            .toList();

            for (int first = 0;
                    first < rolePlayers.size();
                    first++) {

                for (int second = first + 1;
                        second < rolePlayers.size();
                        second++) {

                    SquadCalendarPlayerResponse firstPlayer =
                            rolePlayers.get(first);

                    SquadCalendarPlayerResponse secondPlayer =
                            rolePlayers.get(second);

                    candidates.add(
                            createPairingCandidate(
                                    role,
                                    firstPlayer,
                                    secondPlayer
                            )
                    );
                }
            }
        }

        candidates.sort(
                Comparator
                        .comparing(
                                PairingCandidate::score,
                                Comparator.reverseOrder()
                        )
                        .thenComparing(
                                candidate ->
                                        candidate.firstPlayer()
                                                .playerName(),
                                String.CASE_INSENSITIVE_ORDER
                        )
                        .thenComparing(
                                candidate ->
                                        candidate.secondPlayer()
                                                .playerName(),
                                String.CASE_INSENSITIVE_ORDER
                        )
        );

        List<SquadPairingResponse> response =
                new ArrayList<>();

        int position = 1;

        for (PairingCandidate candidate : candidates) {
            response.add(
                    new SquadPairingResponse(
                            position++,
                            candidate.role(),
                            candidate.firstPlayer()
                                    .playerId(),
                            candidate.firstPlayer()
                                    .playerName(),
                            candidate.firstPlayer()
                                    .club(),
                            candidate.secondPlayer()
                                    .playerId(),
                            candidate.secondPlayer()
                                    .playerName(),
                            candidate.secondPlayer()
                                    .club(),
                            candidate.firstPlayer()
                                    .purchasePrice()
                                    + candidate.secondPlayer()
                                            .purchasePrice(),
                            decimal(candidate.score()),
                            pairingRecommendation(
                                    candidate.score()
                            ),
                            pairingExplanation(candidate)
                    )
            );
        }

        return response;
    }

    private PairingCandidate createPairingCandidate(
            String role,
            SquadCalendarPlayerResponse firstPlayer,
            SquadCalendarPlayerResponse secondPlayer
    ) {
        Map<Integer, Double> firstDifficulties =
                difficultiesByMatchday(
                        firstPlayer.fixtures()
                );

        Map<Integer, Double> secondDifficulties =
                difficultiesByMatchday(
                        secondPlayer.fixtures()
                );

        int coveredMatchdays = 0;
        int hardTogether = 0;
        double bestOptionTotal = 0;
        double differenceTotal = 0;

        for (Integer matchday :
                unionMatchdays(
                        firstDifficulties,
                        secondDifficulties
                )) {

            double firstDifficulty =
                    firstDifficulties.getOrDefault(
                            matchday,
                            60.0
                    );

            double secondDifficulty =
                    secondDifficulties.getOrDefault(
                            matchday,
                            60.0
                    );

            bestOptionTotal +=
                    Math.min(
                            firstDifficulty,
                            secondDifficulty
                    );

            differenceTotal +=
                    Math.abs(
                            firstDifficulty
                                    - secondDifficulty
                    );

            if (firstDifficulty >= 70
                    && secondDifficulty >= 70) {

                hardTogether++;
            }

            coveredMatchdays++;
        }

        double averageBestOption =
                coveredMatchdays == 0
                        ? 60.0
                        : bestOptionTotal
                                / coveredMatchdays;

        double averageDifference =
                coveredMatchdays == 0
                        ? 0
                        : differenceTotal
                                / coveredMatchdays;

        double score =
                100
                        - averageBestOption * 0.65
                        + Math.min(
                                25,
                                averageDifference * 0.70
                        )
                        - hardTogether * 6;

        if (!firstPlayer.club()
                .equalsIgnoreCase(
                        secondPlayer.club()
                )) {

            score += 5;
        }
        else {
            score -= 15;
        }

        score =
                clamp(score);

        return new PairingCandidate(
                role,
                firstPlayer,
                secondPlayer,
                score,
                averageBestOption,
                averageDifference,
                hardTogether
        );
    }

    private Map<Integer, Double> difficultiesByMatchday(
            List<FixtureDifficultyResponse> fixtures
    ) {
        Map<Integer, Double> response =
                new LinkedHashMap<>();

        for (FixtureDifficultyResponse fixture : fixtures) {
            response.put(
                    fixture.matchday(),
                    fixture.difficultyScore()
                            .doubleValue()
            );
        }

        return response;
    }

    private List<Integer> unionMatchdays(
            Map<Integer, Double> first,
            Map<Integer, Double> second
    ) {
        Map<Integer, Boolean> matchdays =
                new LinkedHashMap<>();

        first.keySet()
                .stream()
                .sorted()
                .forEach(matchday ->
                        matchdays.put(
                                matchday,
                                true
                        )
                );

        second.keySet()
                .stream()
                .sorted()
                .forEach(matchday ->
                        matchdays.put(
                                matchday,
                                true
                        )
                );

        return matchdays.keySet()
                .stream()
                .sorted()
                .toList();
    }

    private String pairingRecommendation(
            double score
    ) {
        if (score >= 70) {
            return "ABBINAMENTO ECCELLENTE";
        }

        if (score >= 55) {
            return "ABBINAMENTO CONSIGLIATO";
        }

        if (score >= 40) {
            return "ABBINAMENTO DISCRETO";
        }

        return "CALENDARI POCO COMPLEMENTARI";
    }

    private String pairingExplanation(
            PairingCandidate candidate
    ) {
        String clubText =
                candidate.firstPlayer()
                        .club()
                        .equalsIgnoreCase(
                                candidate.secondPlayer()
                                        .club()
                        )
                        ? "I giocatori appartengono allo stesso club."
                        : "I giocatori appartengono a club diversi.";

        return "Migliore difficoltà media utilizzabile: "
                + decimal(
                        candidate.averageBestOption()
                )
                + ". Differenza media tra i calendari: "
                + decimal(
                        candidate.averageDifference()
                )
                + ". Giornate difficili contemporanee: "
                + candidate.hardTogether()
                + ". "
                + clubText;
    }

    private void addConcentrationWarnings(
            List<SquadCalendarPlayerResponse> players,
            List<String> globalWarnings
    ) {
        Map<String, Integer> totalByClub =
                new HashMap<>();

        Map<String, Integer> totalByClubAndRole =
                new HashMap<>();

        for (SquadCalendarPlayerResponse player : players) {
            totalByClub.merge(
                    player.club(),
                    1,
                    Integer::sum
            );

            totalByClubAndRole.merge(
                    player.club()
                            + "|"
                            + player.role(),
                    1,
                    Integer::sum
            );
        }

        totalByClub.forEach((club, total) -> {
            if (total >= 5) {
                globalWarnings.add(
                        "Concentrazione elevata: "
                                + total
                                + " giocatori appartengono al "
                                + club
                );
            }
        });

        totalByClubAndRole.forEach((key, total) -> {
            if (total >= 3) {
                String[] parts =
                        key.split(
                                "\\|",
                                2
                        );

                globalWarnings.add(
                        "Scarsa diversificazione nel ruolo "
                                + parts[1]
                                + ": "
                                + total
                                + " giocatori del "
                                + parts[0]
                );
            }
        });
    }

    private int countDifficulty(
            List<FixtureDifficultyResponse> fixtures,
            String difficulty
    ) {
        return (int) fixtures.stream()
                .filter(fixture ->
                        difficulty.equals(
                                fixture.difficultyLevel()
                        )
                )
                .count();
    }

    private String buildSummary(
            SquadResponse squad,
            int remainingSlots,
            boolean constraintsValid,
            boolean squadComplete,
            int pairings
    ) {
        String status =
                constraintsValid
                        ? "I vincoli attuali sono rispettati."
                        : "Sono presenti vincoli non rispettati.";

        if (squadComplete) {
            return "Rosa completa con "
                    + squad.totalPlayers()
                    + " giocatori, "
                    + squad.remainingBudget()
                    + " crediti residui e "
                    + pairings
                    + " abbinamenti analizzati. "
                    + status;
        }

        return "Rosa composta da "
                + squad.totalPlayers()
                + " giocatori su "
                + squad.maximumPlayers()
                + ". Restano "
                + remainingSlots
                + " slot e "
                + squad.remainingBudget()
                + " crediti. Sono stati analizzati "
                + pairings
                + " abbinamenti interni. "
                + status;
    }

    private void validateRequest(
            Long squadId,
            int fromMatchday,
            int toMatchday
    ) {
        if (squadId == null || squadId <= 0) {
            throw new BadRequestException(
                    "L'identificativo della rosa non è valido"
            );
        }

        if (fromMatchday < 1
                || toMatchday > 38
                || fromMatchday > toMatchday) {

            throw new BadRequestException(
                    "L'intervallo delle giornate "
                            + "deve essere compreso tra 1 e 38"
            );
        }

        if (toMatchday - fromMatchday > 14) {
            throw new BadRequestException(
                    "L'analisi può comprendere al massimo "
                            + "15 giornate"
            );
        }
    }

    private BigDecimal decimal(
            double value
    ) {
        return BigDecimal
                .valueOf(value)
                .setScale(
                        2,
                        RoundingMode.HALF_UP
                );
    }

    private double clamp(
            double value
    ) {
        return Math.max(
                0,
                Math.min(
                        100,
                        value
                )
        );
    }

    private record PlayerRow(
            Long playerId,
            String playerName,
            String club,
            String clubSlug,
            String role,
            int purchasePrice
    ) {
    }

    private record PairingCandidate(
            String role,
            SquadCalendarPlayerResponse firstPlayer,
            SquadCalendarPlayerResponse secondPlayer,
            double score,
            double averageBestOption,
            double averageDifference,
            int hardTogether
    ) {
    }
}
