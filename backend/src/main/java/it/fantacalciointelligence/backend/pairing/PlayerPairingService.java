package it.fantacalciointelligence.backend.pairing;

import it.fantacalciointelligence.backend.auction.AuctionPriceResponse;
import it.fantacalciointelligence.backend.auction.AuctionApiPriceService;
import it.fantacalciointelligence.backend.fixture.FixtureDifficultyResponse;
import it.fantacalciointelligence.backend.fixture.FixtureDifficultyService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerPairingService {

    private final JdbcClient jdbcClient;
    private final AuctionApiPriceService auctionPriceService;
    private final FixtureDifficultyService fixtureDifficultyService;

    public PlayerPairingService(
            JdbcClient jdbcClient,
            AuctionApiPriceService auctionPriceService,
            FixtureDifficultyService fixtureDifficultyService
    ) {
        this.jdbcClient = jdbcClient;
        this.auctionPriceService = auctionPriceService;
        this.fixtureDifficultyService = fixtureDifficultyService;
    }

    @Transactional
    public List<PlayerPairingResponse> findPairings(
            String season,
            String role,
            int budget,
            int teams,
            boolean defenseModifier,
            int fromMatchday,
            int toMatchday,
            int limit
    ) {
        validateMatchdays(fromMatchday, toMatchday);

        String normalizedRole =
                role.trim().toUpperCase();

        if (!List.of("P", "D", "C", "A")
                .contains(normalizedRole)) {
            throw new IllegalArgumentException(
                    "Ruolo non valido"
            );
        }

        List<Map<String, Object>> rows = jdbcClient.sql("""
                SELECT DISTINCT
                    player.id,
                    player.display_name,
                    club.name AS club_name,
                    club.slug AS club_slug

                FROM players player

                JOIN player_reconciliation_results reconciliation
                    ON reconciliation.player_id = player.id

                JOIN seasons season
                    ON season.id = reconciliation.season_id

                JOIN player_registrations registration
                    ON registration.player_id = player.id
                    AND registration.season_id = season.id

                JOIN clubs club
                    ON club.id = registration.club_id

                JOIN season_clubs season_club
                    ON season_club.season_id = season.id
                    AND season_club.club_id = club.id

                JOIN player_roles role
                    ON role.player_id = player.id
                    AND role.season_id = season.id
                    AND role.source = 'FANTACALCIO'

                WHERE season.code = :season
                  AND player.active = TRUE
                  AND registration.registration_status = 'ACTIVE'
                  AND role.fantasy_role = :role

                ORDER BY player.display_name
                """)
                .param("season", season)
                .param("role", normalizedRole)
                .query()
                .listOfRows();

        if (rows.isEmpty()) {
            return List.of();
        }

        Map<Long, AuctionPriceResponse> pricesByPlayer =
                auctionPriceService.calculateAll(
                                season,
                                budget,
                                teams,
                                defenseModifier
                        )
                        .stream()
                        .collect(Collectors.toMap(
                                AuctionPriceResponse::playerId,
                                Function.identity()
                        ));

        List<PairingCandidate> candidates =
                new ArrayList<>();

        for (Map<String, Object> row : rows) {
            Long playerId =
                    ((Number) row.get("id")).longValue();

            AuctionPriceResponse auction =
                    pricesByPlayer.get(playerId);

            // B51 does not invent a price when market evidence is missing.
            if (auction == null) {
                continue;
            }

            String clubSlug =
                    row.get("club_slug").toString();

            List<FixtureDifficultyResponse> fixtures =
                    fixtureDifficultyService.findClubFixtures(
                            season,
                            clubSlug,
                            fromMatchday,
                            toMatchday
                    );

            double averageDifficulty = fixtures
                    .stream()
                    .mapToDouble(fixture ->
                            fixture.difficultyScore()
                                    .doubleValue()
                    )
                    .average()
                    .orElse(60);

            candidates.add(
                    new PairingCandidate(
                            playerId,
                            row.get("display_name").toString(),
                            row.get("club_name").toString(),
                            normalizedRole,
                            auction.recommendedPrice(),
                            auction.calculationScore(),
                            averageDifficulty,
                            fixtures
                    )
            );
        }

        List<PlayerPairingResponse> pairings =
                new ArrayList<>();

        for (int first = 0;
             first < candidates.size();
             first++) {

            for (int second = first + 1;
                 second < candidates.size();
                 second++) {

                PairingCandidate firstPlayer =
                        candidates.get(first);

                PairingCandidate secondPlayer =
                        candidates.get(second);

                pairings.add(
                        createPairing(
                                firstPlayer,
                                secondPlayer,
                                fromMatchday,
                                toMatchday
                        )
                );
            }
        }

        pairings.sort(
                Comparator
                        .comparing(
                                PlayerPairingResponse::pairingScore
                        )
                        .reversed()
                        .thenComparing(
                                PlayerPairingResponse::
                                        combinedRecommendedPrice
                        )
        );

        int safeLimit =
                Math.min(Math.max(limit, 1), 100);

        List<PlayerPairingResponse> response =
                new ArrayList<>();

        int position = 1;

        for (PlayerPairingResponse pairing :
                pairings.stream()
                        .limit(safeLimit)
                        .toList()) {

            response.add(
                    new PlayerPairingResponse(
                            position,
                            pairing.firstPlayer(),
                            pairing.secondPlayer(),
                            pairing.combinedRecommendedPrice(),
                            pairing.pairingScore(),
                            pairing.recommendation(),
                            pairing.explanation()
                    )
            );

            position++;
        }

        return response;
    }

    private PlayerPairingResponse createPairing(
            PairingCandidate first,
            PairingCandidate second,
            int fromMatchday,
            int toMatchday
    ) {
        double playerQuality =
                (
                    first.evaluationScore().doubleValue()
                    + second.evaluationScore().doubleValue()
                ) / 2;

        double averageDifficulty =
                (
                    first.averageDifficulty()
                    + second.averageDifficulty()
                ) / 2;

        double fixtureQuality =
                100 - averageDifficulty;

        double alternationScore =
                calculateAlternationScore(
                        first,
                        second,
                        fromMatchday,
                        toMatchday
                );

        double priceEfficiency =
                Math.max(
                        0,
                        100 - (
                            (
                                first.recommendedPrice()
                                + second.recommendedPrice()
                            ) / 4.0
                        )
                );

        double finalScore =
                playerQuality * 0.45
                + fixtureQuality * 0.25
                + alternationScore * 0.20
                + priceEfficiency * 0.10;

        finalScore = Math.max(
                0,
                Math.min(finalScore, 100)
        );

        BigDecimal score = BigDecimal
                .valueOf(finalScore)
                .setScale(2, RoundingMode.HALF_UP);

        String recommendation;

        if (finalScore >= 80) {
            recommendation = "ABBINAMENTO ECCELLENTE";
        } else if (finalScore >= 65) {
            recommendation = "ABBINAMENTO CONSIGLIATO";
        } else if (finalScore >= 50) {
            recommendation = "ABBINAMENTO DISCRETO";
        } else {
            recommendation = "ABBINAMENTO RISCHIOSO";
        }

        String explanation =
                "Valore medio "
                + round(playerQuality)
                + "/100, difficoltà calendario "
                + round(averageDifficulty)
                + "/100 e complementarità "
                + round(alternationScore)
                + "/100.";

        return new PlayerPairingResponse(
                0,
                toResponse(first),
                toResponse(second),
                first.recommendedPrice()
                        + second.recommendedPrice(),
                score,
                recommendation,
                explanation
        );
    }

    private double calculateAlternationScore(
            PairingCandidate first,
            PairingCandidate second,
            int fromMatchday,
            int toMatchday
    ) {
        int analyzedMatchdays = 0;
        double total = 0;

        for (int matchday = fromMatchday;
             matchday <= toMatchday;
             matchday++) {

            Double firstDifficulty =
                    difficultyForMatchday(
                            first.fixtures(),
                            matchday
                    );

            Double secondDifficulty =
                    difficultyForMatchday(
                            second.fixtures(),
                            matchday
                    );

            if (firstDifficulty == null
                    || secondDifficulty == null) {
                continue;
            }

            double bestDifficulty =
                    Math.min(
                            firstDifficulty,
                            secondDifficulty
                    );

            double difference =
                    Math.abs(
                            firstDifficulty
                            - secondDifficulty
                    );

            double matchdayScore =
                    (100 - bestDifficulty) * 0.70
                    + Math.min(difference, 100) * 0.30;

            total += matchdayScore;
            analyzedMatchdays++;
        }

        if (analyzedMatchdays == 0) {
            return 50;
        }

        return total / analyzedMatchdays;
    }

    private Double difficultyForMatchday(
            List<FixtureDifficultyResponse> fixtures,
            int matchday
    ) {
        return fixtures.stream()
                .filter(fixture ->
                        fixture.matchday() == matchday
                )
                .map(fixture ->
                        fixture.difficultyScore()
                                .doubleValue()
                )
                .findFirst()
                .orElse(null);
    }

    private PairingPlayerResponse toResponse(
            PairingCandidate candidate
    ) {
        return new PairingPlayerResponse(
                candidate.playerId(),
                candidate.playerName(),
                candidate.club(),
                candidate.role(),
                candidate.recommendedPrice(),
                candidate.evaluationScore(),
                BigDecimal
                        .valueOf(
                                candidate.averageDifficulty()
                        )
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        )
        );
    }

    private void validateMatchdays(
            int fromMatchday,
            int toMatchday
    ) {
        if (fromMatchday < 1
                || toMatchday > 38
                || fromMatchday > toMatchday) {

            throw new IllegalArgumentException(
                    "Intervallo giornate non valido"
            );
        }
    }

    private String round(double value) {
        return BigDecimal
                .valueOf(value)
                .setScale(1, RoundingMode.HALF_UP)
                .toString();
    }

    private record PairingCandidate(
            Long playerId,
            String playerName,
            String club,
            String role,
            int recommendedPrice,
            BigDecimal evaluationScore,
            double averageDifficulty,
            List<FixtureDifficultyResponse> fixtures
    ) {
    }
}
