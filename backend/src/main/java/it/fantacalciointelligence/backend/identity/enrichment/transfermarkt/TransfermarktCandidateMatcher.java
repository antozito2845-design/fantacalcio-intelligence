package it.fantacalciointelligence.backend.identity.enrichment.transfermarkt;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class TransfermarktCandidateMatcher {

        public MatchResult match(
                        String playerName,
                        String club,
                        List<TransfermarktCandidate> candidates) {
                if (candidates == null || candidates.isEmpty()) {
                        return new MatchResult(
                                        "NOT_FOUND",
                                        0,
                                        null,
                                        "Nessun candidato Transfermarkt");
                }

                List<ScoredCandidate> scored = candidates.stream()
                                .map(candidate -> new ScoredCandidate(
                                                candidate,
                                                score(
                                                                playerName,
                                                                club,
                                                                candidate)))
                                .sorted(
                                                Comparator.comparingInt(
                                                                ScoredCandidate::score).reversed())
                                .toList();

                ScoredCandidate best = scored.getFirst();

                if (best.score() < 55) {
                        return new MatchResult(
                                        "NOT_FOUND",
                                        best.score(),
                                        best.candidate(),
                                        "Nessun candidato raggiunge la soglia minima");
                }

                long sameBest = scored.stream()
                                .filter(item -> item.score() == best.score())
                                .count();

                if (sameBest > 1) {
                        return new MatchResult(
                                        "REVIEW_REQUIRED",
                                        best.score(),
                                        best.candidate(),
                                        "Piu candidati con lo stesso punteggio");
                }

                return new MatchResult(
                                "PROFILE_VERIFICATION_REQUIRED",
                                best.score(),
                                best.candidate(),
                                best.score() >= 80
                                                ? "Candidato forte; verificare profilo"
                                                : "Candidato compatibile; verificare profilo");
        }

        int score(
                        String playerName,
                        String club,
                        TransfermarktCandidate candidate) {
                String expectedName = normalize(playerName);

                String candidateName = normalize(candidate.name());

                int score = 0;

                if (!expectedName.isBlank()
                                && expectedName.equals(candidateName)) {
                        score += 80;
                } else if (surnameCompatible(
                                expectedName,
                                candidateName)) {
                        score += 55;
                }

                String normalizedClub = normalize(club);

                String context = normalize(candidate.context());

                if (!normalizedClub.isBlank()
                                && context.contains(normalizedClub)) {
                        score += 20;
                }

                return Math.min(score, 100);
        }

        boolean surnameCompatible(
                        String expected,
                        String candidate) {
                if (expected.isBlank()
                                || candidate.isBlank()) {
                        return false;
                }

                Set<String> expectedTokens = significantTokens(expected);

                Set<String> candidateTokens = significantTokens(candidate);

                if (expectedTokens.isEmpty()
                                || candidateTokens.isEmpty()) {
                        return false;
                }

                return candidateTokens.containsAll(
                                expectedTokens);
        }

        private Set<String> significantTokens(
                        String normalized) {
                return Arrays.stream(
                                normalized.split(" "))
                                .filter(token -> token.length() >= 2)
                                .filter(token -> !token.equals("ds"))
                                .collect(
                                                Collectors.toSet());
        }

        static String normalize(String value) {
                if (value == null) {
                        return "";
                }

                /*
                 * Some Latin letters are not decomposed by Unicode NFD.
                 *
                 * Apostrophes inside names are removed instead of becoming
                 * separators:
                 *
                 * N'Dicka -> ndicka
                 * N’Dicka -> ndicka
                 */
                String transliterated = value
                                .replace("'", "")
                                .replace("\u2019", "")
                                .replace("\u2018", "")
                                .replace('\u0131', 'i') // ı
                                .replace('\u0130', 'I') // İ
                                .replace('\u00F8', 'o') // ø
                                .replace('\u00D8', 'O') // Ø
                                .replace('\u0142', 'l') // ł
                                .replace('\u0141', 'L') // Ł
                                .replace("\u00DF", "ss") // ß
                                .replace("\u00E6", "ae") // æ
                                .replace("\u00C6", "AE") // Æ
                                .replace("\u0153", "oe") // œ
                                .replace("\u0152", "OE"); // Œ

                return Normalizer.normalize(
                                transliterated,
                                Normalizer.Form.NFD)
                                .replaceAll("\\p{M}+", "")
                                .toLowerCase(Locale.ROOT)
                                .replaceAll("[^a-z0-9]+", " ")
                                .replaceAll("\\s+", " ")
                                .trim();
        }

        public record MatchResult(
                        String status,
                        int score,
                        TransfermarktCandidate candidate,
                        String reason) {
        }

        private record ScoredCandidate(
                        TransfermarktCandidate candidate,
                        int score) {
        }
}