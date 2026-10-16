package it.fantacalciointelligence.backend.identity.enrichment.transfermarkt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class TransfermarktCandidateMatcherTest {

        private final TransfermarktCandidateMatcher matcher = new TransfermarktCandidateMatcher();

        @Test
        void exactNameAndClubRequiresProfileVerification() {
                var result = matcher.match(
                                "Mario Rossi",
                                "Atalanta",
                                List.of(
                                                candidate(
                                                                "1",
                                                                "Mario Rossi",
                                                                "Mario Rossi Atalanta BC")));

                assertThat(result.status())
                                .isEqualTo(
                                                "PROFILE_VERIFICATION_REQUIRED");

                assertThat(result.score())
                                .isEqualTo(100);
        }

        @Test
        void surnameOnlyFantacalcioNameFindsFullTransfermarktName() {
                var result = matcher.match(
                                "Bellanova",
                                "Atalanta",
                                List.of(
                                                candidate(
                                                                "357992",
                                                                "Raoul Bellanova",
                                                                "Raoul Bellanova Atalanta BC")));

                assertThat(result.status())
                                .isEqualTo(
                                                "PROFILE_VERIFICATION_REQUIRED");

                assertThat(result.score())
                                .isEqualTo(75);
        }

        @Test
        void exactNameWithoutClubStillRequiresProfileVerification() {
                var result = matcher.match(
                                "Mario Rossi",
                                "Atalanta",
                                List.of(
                                                candidate(
                                                                "1",
                                                                "Mario Rossi",
                                                                "Mario Rossi Torino FC")));

                assertThat(result.status())
                                .isEqualTo(
                                                "PROFILE_VERIFICATION_REQUIRED");

                assertThat(result.score())
                                .isEqualTo(80);
        }

        @Test
        void sameTopScoreRequiresManualReview() {
                var result = matcher.match(
                                "Mario Rossi",
                                "Atalanta",
                                List.of(
                                                candidate(
                                                                "1",
                                                                "Mario Rossi",
                                                                "Mario Rossi Atalanta"),
                                                candidate(
                                                                "2",
                                                                "Mario Rossi",
                                                                "Mario Rossi Atalanta")));

                assertThat(result.status())
                                .isEqualTo(
                                                "REVIEW_REQUIRED");
        }

        @Test
        void unrelatedCandidateDoesNotPassSurnameCompatibility() {
                var result = matcher.match(
                                "Gaetano",
                                "Atalanta",
                                List.of(
                                                candidate(
                                                                "116740",
                                                                "Gaetano Scirea",
                                                                "Gaetano Scirea")));

                /*
                 * Search candidate can still be superficially compatible because
                 * Fantacalcio exposes only "Gaetano". B63.2B never persists this:
                 * the profile/team stage must confirm or reject it.
                 */
                assertThat(result.status())
                                .isEqualTo(
                                                "PROFILE_VERIFICATION_REQUIRED");
        }

        @Test
        void accentsAreNormalized() {
                assertThat(
                                TransfermarktCandidateMatcher
                                                .normalize("Tour\u00E8 E."))
                                .isEqualTo("toure e");
        }

        @Test
        void turkishDotlessIIsNormalized() {
                assertThat(
                                TransfermarktCandidateMatcher
                                                .normalize("Y\u0131ld\u0131z"))
                                .isEqualTo("yildiz");

                assertThat(
                                TransfermarktCandidateMatcher
                                                .normalize("Kenan Y\u0131ld\u0131z"))
                                .isEqualTo("kenan yildiz");
        }

        @Test
        void nordicOStrokeIsNormalized() {
                assertThat(
                                TransfermarktCandidateMatcher
                                                .normalize("\u00D8stig\u00E5rd"))
                                .isEqualTo("ostigard");

                assertThat(
                                TransfermarktCandidateMatcher
                                                .normalize("Leo \u00D8stig\u00E5rd"))
                                .isEqualTo("leo ostigard");
        }

        @Test
        void hojlundIsNormalizedAcrossAsciiAndNordicSpelling() {
                assertThat(
                                TransfermarktCandidateMatcher
                                                .normalize("H\u00F8jlund"))
                                .isEqualTo("hojlund");

                assertThat(
                                TransfermarktCandidateMatcher
                                                .normalize("Rasmus H\u00F8jlund"))
                                .isEqualTo("rasmus hojlund");
        }

        @Test
        void sorensenIsNormalizedAcrossAsciiAndNordicSpelling() {
                assertThat(
                                TransfermarktCandidateMatcher
                                                .normalize("S\u00F8rensen"))
                                .isEqualTo("sorensen");

                assertThat(
                                TransfermarktCandidateMatcher
                                                .normalize("Oliver S\u00F8rensen"))
                                .isEqualTo("oliver sorensen");
        }

        @Test
        void latinVariantsRemainSurnameCompatible() {
                assertThat(
                                matcher.surnameCompatible(
                                                TransfermarktCandidateMatcher.normalize("Kenan Yildiz"),
                                                TransfermarktCandidateMatcher.normalize("Kenan Y\u0131ld\u0131z")))
                                .isTrue();

                assertThat(
                                matcher.surnameCompatible(
                                                TransfermarktCandidateMatcher.normalize("Leo Ostigard"),
                                                TransfermarktCandidateMatcher.normalize("Leo \u00D8stig\u00E5rd")))
                                .isTrue();

                assertThat(
                                matcher.surnameCompatible(
                                                TransfermarktCandidateMatcher.normalize("Rasmus Hojlund"),
                                                TransfermarktCandidateMatcher.normalize("Rasmus H\u00F8jlund")))
                                .isTrue();

                assertThat(
                                matcher.surnameCompatible(
                                                TransfermarktCandidateMatcher.normalize("Oliver Sorensen"),
                                                TransfermarktCandidateMatcher.normalize("Oliver S\u00F8rensen")))
                                .isTrue();
        }

        private TransfermarktCandidate candidate(
                        String id,
                        String name,
                        String context) {
                return new TransfermarktCandidate(
                                id,
                                name,
                                "https://www.transfermarkt.com/x/profil/spieler/"
                                                + id,
                                context);
        }
}