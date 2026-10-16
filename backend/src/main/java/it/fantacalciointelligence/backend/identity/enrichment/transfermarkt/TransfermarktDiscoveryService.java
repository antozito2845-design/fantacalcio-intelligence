package it.fantacalciointelligence.backend.identity.enrichment.transfermarkt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.time.LocalDate;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.common.BadRequestException;
import it.fantacalciointelligence.backend.common.ConflictException;

@Service
public class TransfermarktDiscoveryService {

    // Search is a fallback: an existing verified Transfermarkt ID is authoritative identity evidence.
    private static final int MAX_PROFILE_CANDIDATES = 8;

    private final JdbcClient jdbc;
    private final TransfermarktDiscoveryProperties properties;
    private final TransfermarktDiscoveryClient client;
    private final TransfermarktSearchParser searchParser;
    private final TransfermarktProfileParser profileParser;
    private final TransfermarktCandidateMatcher matcher;

    public TransfermarktDiscoveryService(
            JdbcClient jdbc,
            TransfermarktDiscoveryProperties properties,
            TransfermarktDiscoveryClient client,
            TransfermarktSearchParser searchParser,
            TransfermarktProfileParser profileParser,
            TransfermarktCandidateMatcher matcher
    ) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.client = client;
        this.searchParser = searchParser;
        this.profileParser = profileParser;
        this.matcher = matcher;
    }

    @Transactional(readOnly = true)
    public TransfermarktDiscoveryResponse discover(
            String season,
            String club,
            int limit
    ) {
        if (!properties.isEnabled()) {
            throw new ConflictException(
                    "Transfermarkt discovery disabilitata"
            );
        }

        if (season == null || season.isBlank()) {
            throw new BadRequestException(
                    "Season obbligatoria"
            );
        }

        if (limit < 1 || limit > 50) {
            throw new BadRequestException(
                    "limit deve essere compreso tra 1 e 50"
            );
        }

        List<OperationalPlayer> players =
                loadOperationalPlayers(
                        season,
                        club,
                        limit
                );

        List<TransfermarktDiscoveryResponse.PlayerDiscovery> output =
                new ArrayList<>();

        int matched = 0;
        int review = 0;
        int notFound = 0;
        int failed = 0;

        for (OperationalPlayer player : players) {
            try {
                PlayerResolution resolution =
                        resolvePlayer(player);

                switch (resolution.status()) {
                    case "MATCHED_VERIFIED" -> matched++;
                    case "REVIEW_REQUIRED" -> review++;
                    default -> notFound++;
                }

                output.add(
                        result(
                                player,
                                resolution.status(),
                                resolution.score(),
                                resolution.candidate(),
                                resolution.profile(),
                                resolution.reason()
                        )
                );
            }
            catch (RuntimeException exception) {
                failed++;

                output.add(
                        new TransfermarktDiscoveryResponse.PlayerDiscovery(
                                player.id(),
                                player.name(),
                                player.club(),
                                "FAILED",
                                0,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                safeMessage(exception)
                        )
                );
            }
        }

        return new TransfermarktDiscoveryResponse(
                season,
                club,
                players.size(),
                matched,
                review,
                notFound,
                failed,
                false,
                List.copyOf(output)
        );
    }

    /**
     * Production automation path.
     *
     * Unlike the existing manual discover(...) method this method only
     * selects operational players whose identity is still incomplete and
     * whose retry cooldown is eligible.
     *
     * Existing manual discovery semantics remain unchanged.
     */
    @Transactional(readOnly = true)
    public TransfermarktDiscoveryResponse discoverIncomplete(
            String season,
            int limit
    ) {

        if (!properties.isEnabled()) {
            throw new ConflictException(
                    "Transfermarkt discovery disabilitata"
            );
        }

        if (season == null || season.isBlank()) {
            throw new BadRequestException(
                    "Season obbligatoria"
            );
        }

        if (limit < 1 || limit > 50) {
            throw new BadRequestException(
                    "limit deve essere compreso tra 1 e 50"
            );
        }

        List<OperationalPlayer> players =
                loadIncompleteOperationalPlayers(
                        season,
                        limit
                );

        List<TransfermarktDiscoveryResponse.PlayerDiscovery> output =
                new ArrayList<>();

        int matched = 0;
        int review = 0;
        int notFound = 0;
        int failed = 0;

        for (OperationalPlayer player : players) {

            try {

                PlayerResolution resolution =
                        resolvePlayer(player);

                switch (resolution.status()) {
                    case "MATCHED_VERIFIED" -> matched++;
                    case "REVIEW_REQUIRED" -> review++;
                    default -> notFound++;
                }

                output.add(
                        result(
                                player,
                                resolution.status(),
                                resolution.score(),
                                resolution.candidate(),
                                resolution.profile(),
                                resolution.reason()
                        )
                );

            }
            catch (RuntimeException exception) {

                failed++;

                output.add(
                        new TransfermarktDiscoveryResponse.PlayerDiscovery(
                                player.id(),
                                player.name(),
                                player.club(),
                                "FAILED",
                                0,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                safeMessage(exception)
                        )
                );
            }
        }

        return new TransfermarktDiscoveryResponse(
                season,
                null,
                players.size(),
                matched,
                review,
                notFound,
                failed,
                false,
                List.copyOf(output)
        );
    }
    private PlayerResolution resolvePlayer(
            OperationalPlayer player
    ) {
        var knownCandidate =
                knownTransfermarktCandidate(player);

        if (knownCandidate.isPresent()) {
            PlayerResolution direct =
                    resolvePlayerFromCandidates(
                            player,
                            List.of(knownCandidate.get())
                    );

            if ("MATCHED_VERIFIED".equals(
                    direct.status()
            )) {
                return direct;
            }

            List<TransfermarktCandidate> searched =
                    searchKnownCandidateById(player);

            if (!searched.isEmpty()) {
                PlayerResolution fallback =
                        resolvePlayerFromCandidates(
                                player,
                                searched
                        );

                if ("MATCHED_VERIFIED".equals(
                        fallback.status()
                )) {
                    return fallback;
                }
            }

            return direct;
        }

        return resolvePlayerFromCandidates(
                player,
                searchCandidates(
                        firstNonBlank(
                                player.canonicalFullName(),
                                player.name()
                        )
                )
        );
    }

    private PlayerResolution resolvePlayerFromCandidates(
            OperationalPlayer player,
            List<TransfermarktCandidate> candidates
    ) {

        if (candidates.isEmpty()) {
            return new PlayerResolution(
                    "NOT_FOUND",
                    0,
                    null,
                    null,
                    "Nessun candidato Transfermarkt"
            );
        }

        List<ScoredCandidate> ranked =
                candidates.stream()
                        .map(candidate ->
                                new ScoredCandidate(
                                        candidate,
                                        matcher.score(
                                                firstNonBlank(
                                                        player.canonicalFullName(),
                                                        player.name()
                                                ),
                                                player.club(),
                                                candidate
                                        )
                                )
                        )
                        .filter(item ->
                                item.score() >= 55
                        )
                        .sorted(
                                Comparator.comparingInt(
                                        ScoredCandidate::score
                                ).reversed()
                        )
                        .limit(MAX_PROFILE_CANDIDATES)
                        .toList();

        if (ranked.isEmpty()) {
            return new PlayerResolution(
                    "NOT_FOUND",
                    0,
                    null,
                    null,
                    "Nessun candidato raggiunge la soglia minima"
            );
        }

        List<VerifiedCandidate> verified =
                new ArrayList<>();

        List<VerifiedCandidate> reviewable =
                new ArrayList<>();

        for (ScoredCandidate scored : ranked) {
            try {
                TransfermarktDiscoveryClient.ProfilePage page =
                        client.fetchProfile(
                                scored.candidate().url()
                        );

                TransfermarktProfile profile =
                        profileParser.parse(
                                page.html(),
                                page.finalUrl()
                        );

                Verification verification =
                        verifyProfile(
                                player,
                                scored.candidate(),
                                profile
                        );

                VerifiedCandidate checked =
                        new VerifiedCandidate(
                                scored,
                                profile,
                                verification
                        );

                if ("MATCHED_VERIFIED".equals(
                        verification.status()
                )) {
                    verified.add(checked);
                }
                else if ("REVIEW_REQUIRED".equals(
                        verification.status()
                )) {
                    reviewable.add(checked);
                }
            }
            catch (RuntimeException ignored) {
                // A single remote candidate must never abort the player.
            }
        }

        if (verified.size() == 1) {
            VerifiedCandidate winner =
                    verified.getFirst();

            return new PlayerResolution(
                    "MATCHED_VERIFIED",
                    winner.scored().score(),
                    winner.scored().candidate(),
                    winner.profile(),
                    winner.verification().reason()
            );
        }

        if (verified.size() > 1) {
            VerifiedCandidate best =
                    verified.stream()
                            .max(
                                    Comparator.comparingInt(
                                            item ->
                                                    item.scored()
                                                            .score()
                                    )
                            )
                            .orElseThrow();

            return new PlayerResolution(
                    "REVIEW_REQUIRED",
                    best.scored().score(),
                    best.scored().candidate(),
                    best.profile(),
                    "Piu profili Transfermarkt compatibili: review obbligatoria"
            );
        }

        if (!reviewable.isEmpty()) {
            VerifiedCandidate best =
                    reviewable.stream()
                            .max(
                                    Comparator.comparingInt(
                                            item ->
                                                    item.scored()
                                                            .score()
                                    )
                            )
                            .orElseThrow();

            return new PlayerResolution(
                    "REVIEW_REQUIRED",
                    best.scored().score(),
                    best.scored().candidate(),
                    best.profile(),
                    best.verification().reason()
            );
        }

        return new PlayerResolution(
                "NOT_FOUND",
                ranked.getFirst().score(),
                ranked.getFirst().candidate(),
                null,
                "Nessun profilo candidato ha superato la verifica"
        );
    }

    private List<TransfermarktCandidate> searchCandidates(
            String playerName
    ) {
        Map<String, TransfermarktCandidate> unique =
                new LinkedHashMap<>();

        for (String query : searchQueries(playerName)) {
            TransfermarktDiscoveryClient.SearchPage page =
                    client.search(query);

            for (TransfermarktCandidate candidate :
                    searchParser.parse(
                            page.html(),
                            page.finalUrl()
                    )) {
                unique.putIfAbsent(
                        candidate.transfermarktPlayerId(),
                        candidate
                );
            }

            /*
             * If the exact Fantacalcio query already gives useful
             * candidates, avoid an unnecessary second remote request.
             */
            if (!unique.isEmpty()) {
                break;
            }
        }

        return List.copyOf(
                unique.values()
        );
    }

    private List<TransfermarktCandidate> searchKnownCandidateById(
            OperationalPlayer player
    ) {
        Map<String, TransfermarktCandidate> matching =
                new LinkedHashMap<>();

        List<String> queries =
                new ArrayList<>();

        queries.add(
                firstNonBlank(
                        player.canonicalFullName(),
                        player.name()
                )
        );

        for (String query : searchQueries(player.name())) {
            if (!queries.contains(query)) {
                queries.add(query);
            }
        }

        for (String query : queries) {
            TransfermarktDiscoveryClient.SearchPage page =
                    client.search(query);

            searchParser.parse(
                            page.html(),
                            page.finalUrl()
                    )
                    .stream()
                    .filter(candidate ->
                            player.transfermarktPlayerId()
                                    .equals(
                                            candidate.transfermarktPlayerId()
                                    )
                    )
                    .forEach(candidate ->
                            matching.putIfAbsent(
                                    candidate.transfermarktPlayerId(),
                                    candidate
                            )
                    );
        }

        return List.copyOf(
                matching.values()
        );
    }

    List<String> searchQueries(
            String playerName
    ) {
        if (playerName == null || playerName.isBlank()) {
            return List.of();
        }

        String original =
                playerName.trim();

        String simplified =
                original
                        .replaceAll(
                                "(?i)\\s+[A-Z](?:\\.[A-Z])?\\.?$",
                                ""
                        )
                        .replaceAll(
                                "(?i)\\s+[A-Z]\\.[A-Z]\\.$",
                                ""
                        )
                        .trim();

        if (simplified.equalsIgnoreCase(original)
                || simplified.isBlank()) {
            return List.of(original);
        }

        return List.of(
                original,
                simplified
        );
    }

    Verification verifyProfile(
            OperationalPlayer player,
            TransfermarktCandidate candidate,
            TransfermarktProfile profile
    ) {
        if (profile == null) {
            return new Verification(
                    "REVIEW_REQUIRED",
                    "Profilo Transfermarkt non leggibile"
            );
        }

        if (profile.transfermarktPlayerId() != null
                && !profile.transfermarktPlayerId()
                        .equals(
                                candidate.transfermarktPlayerId()
                        )) {
            return new Verification(
                    "REVIEW_REQUIRED",
                    "ID profilo diverso dal candidato"
            );
        }

        String expectedName =
                TransfermarktCandidateMatcher
                        .normalize(
                                firstNonBlank(
                                        player.canonicalFullName(),
                                        player.name()
                                )
                        );

        String profileName =
                TransfermarktCandidateMatcher
                        .normalize(
                                profile.fullName()
                        );

        String displayName =
                TransfermarktCandidateMatcher
                        .normalize(
                                player.name()
                        );

        if (player.birthDate() != null
                && profile.birthDate() != null
                && !player.birthDate().equals(
                        profile.birthDate()
                )) {
            return new Verification(
                    "NOT_FOUND",
                    "Data di nascita Transfermarkt diversa dal profilo identita locale"
            );
        }

        boolean strongIdentity =
                profile.birthDate() != null
                        && player.birthDate() != null
                        && profile.birthDate().equals(
                                player.birthDate()
                        )
                        && player.transfermarktPlayerId() != null
                        && player.transfermarktPlayerId().equals(
                                profile.transfermarktPlayerId()
                        );

        boolean compatibleName =
                matcher.surnameCompatible(
                        expectedName,
                        profileName
                )
                || expectedName.equals(
                        profileName
                )
                || (
                        (player.canonicalFullName() == null
                                || player.canonicalFullName().isBlank())
                                && matcher.surnameCompatible(
                                        displayName,
                                        profileName
                                )
                );

        boolean strongProfessionalNameCompatibility =
                strongIdentity
                        && hasStrongSharedNameToken(
                                expectedName,
                                profileName
                        );

        if (!compatibleName
                && !strongProfessionalNameCompatibility) {
            return new Verification(
                    "NOT_FOUND",
                    "Nome del profilo non coerente con Fantacalcio"
            );
        }

        /*
         * A Fantacalcio suffix such as "O." or "K." is useful
         * when identity evidence is weak.
         *
         * With an already verified Transfermarkt ID and an exact
         * birth-date match, however, that suffix must not reject
         * legitimate professional names such as:
         *
         * Vitinha O. -> Vitinha
         * Gaspar K.  -> Gaspar
         */
        if (!strongIdentity
                && !initialCompatible(
                        player.name(),
                        profile.fullName()
                )) {
            return new Verification(
                    "NOT_FOUND",
                    "Iniziale Fantacalcio non coerente con il nome Transfermarkt"
            );
        }

        String expectedClub =
                TransfermarktCandidateMatcher
                        .normalize(
                                player.club()
                        );

        String profileClub =
                TransfermarktCandidateMatcher
                        .normalize(
                                profile.currentClub()
                        );

        if (profileClub.isBlank()) {
            if (strongIdentity) {
                return new Verification(
                        "MATCHED_VERIFIED",
                        "Identita verificata con ID, nome canonico e data di nascita; roster Transfermarkt non leggibile"
                );
            }

            return new Verification(
                    "REVIEW_REQUIRED",
                    "Squadra corrente non leggibile dal profilo"
            );
        }

        if (!clubCompatible(
                expectedClub,
                profileClub
        )) {
            if (strongIdentity) {
                return new Verification(
                        "MATCHED_VERIFIED",
                        "Identita verificata con ID, nome canonico e data di nascita; roster Transfermarkt temporalmente diverso: "
                                + profile.currentClub()
                );
            }

            return new Verification(
                    "NOT_FOUND",
                    "Squadra Transfermarkt diversa da Fantacalcio: "
                            + profile.currentClub()
            );
        }

        if (profile.birthDate() == null) {
            return new Verification(
                    "REVIEW_REQUIRED",
                    "Data di nascita non leggibile dal profilo"
            );
        }

        return new Verification(
                "MATCHED_VERIFIED",
                "Nome, squadra e data di nascita verificati sul profilo Transfermarkt"
        );
    }

    boolean initialCompatible(
            String fantacalcioName,
            String transfermarktName
    ) {
        if (fantacalcioName == null
                || transfermarktName == null) {
            return true;
        }

        String trimmed =
                fantacalcioName.trim();

        /*
         * Handles catalog forms such as:
         * Rossi F.       -> Francesco Rossi
         * Kristensen T.  -> Thomas Kristensen
         * Sulemana I.    -> Ibrahim Sulemana
         *
         * Do not interpret compound abbreviations such as D.S.
         * as a first-name initial.
         */
        var matcher =
                java.util.regex.Pattern
                        .compile(
                                "^(.+?)\\s+([A-Za-z])\\.$"
                        )
                        .matcher(trimmed);

        if (!matcher.matches()) {
            return true;
        }

        String surname =
                TransfermarktCandidateMatcher
                        .normalize(
                                matcher.group(1)
                        );

        char initial =
                Character.toLowerCase(
                        matcher.group(2)
                                .charAt(0)
                );

        String normalizedProfile =
                TransfermarktCandidateMatcher
                        .normalize(
                                transfermarktName
                        );

        if (!normalizedProfile.contains(surname)) {
            return false;
        }

        String[] tokens =
                normalizedProfile.split(" ");

        if (tokens.length < 2
                || tokens[0].isBlank()) {
            return false;
        }

        return tokens[0]
                .toLowerCase(Locale.ROOT)
                .charAt(0) == initial;
    }

    private boolean clubCompatible(
            String expected,
            String actual
    ) {
        if (expected.isBlank()
                || actual.isBlank()) {
            return false;
        }

        return actual.contains(expected)
                || expected.contains(actual);
    }

    private boolean hasStrongSharedNameToken(
            String expected,
            String actual
    ) {
        if (expected == null
                || expected.isBlank()
                || actual == null
                || actual.isBlank()) {
            return false;
        }

        Set<String> expectedTokens =
                Set.of(expected.split(" "));

        return Arrays.stream(actual.split(" "))
                .filter(token ->
                        token.length() >= 6
                )
                .anyMatch(expectedTokens::contains);
    }

    private long sharedNameTokens(
            String expected,
            String actual
    ) {
        Set<String> expectedTokens =
                Set.of(expected.split(" "));

        return Arrays.stream(actual.split(" "))
                .filter(token -> token.length() >= 2)
                .filter(expectedTokens::contains)
                .distinct()
                .count();
    }

    private TransfermarktDiscoveryResponse.PlayerDiscovery result(
            OperationalPlayer player,
            String status,
            int score,
            TransfermarktCandidate candidate,
            TransfermarktProfile profile,
            String reason
    ) {
        return new TransfermarktDiscoveryResponse.PlayerDiscovery(
                player.id(),
                player.name(),
                player.club(),
                status,
                score,
                candidate == null
                        ? null
                        : candidate.transfermarktPlayerId(),
                profile != null
                        && profile.fullName() != null
                                ? profile.fullName()
                                : candidate == null
                                        ? null
                                        : candidate.name(),
                profile != null
                        && profile.profileUrl() != null
                                ? profile.profileUrl()
                                : candidate == null
                                        ? null
                                        : candidate.url(),
                profile == null
                        ? null
                        : profile.currentClub(),
                profile == null
                        ? null
                        : profile.birthDate(),
                profile == null
                        ? null
                        : profile.nationality(),
                profile == null
                        ? null
                        : profile.heightCm(),
                profile == null
                        ? null
                        : profile.preferredFoot(),
                reason
        );
    }

    private List<OperationalPlayer> loadOperationalPlayers(
            String season,
            String club,
            int limit
    ) {
        String clubFilter =
                club == null || club.isBlank()
                        ? ""
                        : " AND LOWER(c.name) = LOWER(:club) ";

        var query =
                jdbc.sql("""
                        SELECT DISTINCT
                            p.id,
                            p.display_name,
                            c.name AS club_name,
                            COALESCE(
                                identity.canonical_full_name,
                                identity_override.canonical_full_name
                            ) AS canonical_full_name,
                            COALESCE(
                                identity.birth_date,
                                identity_override.birth_date
                            ) AS birth_date,
                            COALESCE(
                                identity.transfermarkt_player_id,
                                identity_override.transfermarkt_player_id
                            ) AS transfermarkt_player_id
                        FROM player_reconciliation_results result
                        JOIN seasons s
                          ON s.id = result.season_id
                         AND s.code = :season
                        JOIN players p
                          ON p.id = result.player_id
                         AND p.active = TRUE
                        JOIN player_registrations registration
                          ON registration.player_id = p.id
                         AND registration.season_id = s.id
                         AND registration.registration_status = 'ACTIVE'
                        JOIN clubs c
                          ON c.id = registration.club_id
                        LEFT JOIN player_identity_profiles identity
                          ON identity.player_id = p.id

                        LEFT JOIN verified_identity_overrides identity_override
                          ON identity_override.season_code = s.code
                         AND LOWER(identity_override.club_name) = LOWER(c.name)
                         AND LOWER(identity_override.player_display_name) =
                             LOWER(p.display_name)

                        WHERE 1 = 1
                        """
                        + clubFilter
                        + """
                        ORDER BY c.name, p.display_name, p.id
                        LIMIT :limit
                        """)
                        .param(
                                "season",
                                season
                        )
                        .param(
                                "limit",
                                limit
                        );

        if (club != null && !club.isBlank()) {
            query =
                    query.param(
                            "club",
                            club.trim()
                    );
        }

        return query
                .query((rs, rowNumber) ->
                        new OperationalPlayer(
                                rs.getLong("id"),
                                rs.getString("display_name"),
                                rs.getString("club_name"),
                                rs.getString("canonical_full_name"),
                                rs.getObject(
                                        "birth_date",
                                        LocalDate.class
                                ),
                                rs.getString("transfermarkt_player_id")
                        )
                )
                .list();
    }

    private List<OperationalPlayer> loadIncompleteOperationalPlayers(
            String season,
            int limit
    ) {

        return jdbc.sql("""
                SELECT DISTINCT
                    p.id,
                    p.display_name,
                    c.name AS club_name,

                    COALESCE(
                        identity.canonical_full_name,
                        identity_override.canonical_full_name
                    ) AS canonical_full_name,

                    COALESCE(
                        identity.birth_date,
                        identity_override.birth_date
                    ) AS birth_date,

                    COALESCE(
                        identity.transfermarkt_player_id,
                        identity_override.transfermarkt_player_id
                    ) AS transfermarkt_player_id,

                    attempt.id AS previous_attempt_id,
                    attempt.next_retry_at

                FROM player_reconciliation_results result

                JOIN seasons s
                  ON s.id = result.season_id
                 AND s.code = :season

                JOIN players p
                  ON p.id = result.player_id
                 AND p.active = TRUE

                JOIN player_registrations registration
                  ON registration.player_id = p.id
                 AND registration.season_id = s.id
                 AND registration.registration_status = 'ACTIVE'

                JOIN clubs c
                  ON c.id = registration.club_id

                LEFT JOIN player_identity_profiles identity
                  ON identity.player_id = p.id

                LEFT JOIN verified_identity_overrides identity_override
                  ON identity_override.season_code = s.code
                 AND LOWER(identity_override.club_name) =
                     LOWER(c.name)
                 AND LOWER(identity_override.player_display_name) =
                     LOWER(p.display_name)

                LEFT JOIN identity_enrichment_automation_attempts attempt
                  ON attempt.season_id = s.id
                 AND attempt.player_id = p.id

                WHERE
                    (
                        COALESCE(
                            identity.canonical_full_name,
                            identity_override.canonical_full_name
                        ) IS NULL

                        OR COALESCE(
                            identity.birth_date,
                            identity_override.birth_date
                        ) IS NULL

                        OR identity.nationality IS NULL
                        OR identity.nationality_code IS NULL
                        OR identity.height_cm IS NULL
                        OR identity.preferred_foot IS NULL

                        OR COALESCE(
                            identity.transfermarkt_player_id,
                            identity_override.transfermarkt_player_id
                        ) IS NULL
                    )

                  AND (
                        attempt.id IS NULL
                        OR attempt.next_retry_at <= CURRENT_TIMESTAMP
                  )

                ORDER BY
                    CASE
                        WHEN attempt.id IS NULL
                            THEN 0
                        ELSE 1
                    END,
                    attempt.next_retry_at NULLS FIRST,
                    c.name,
                    p.display_name,
                    p.id

                LIMIT :limit
                """)
                .param(
                        "season",
                        season
                )
                .param(
                        "limit",
                        limit
                )
                .query((rs, rowNumber) ->
                        new OperationalPlayer(
                                rs.getLong("id"),
                                rs.getString("display_name"),
                                rs.getString("club_name"),
                                rs.getString("canonical_full_name"),
                                rs.getObject(
                                        "birth_date",
                                        LocalDate.class
                                ),
                                rs.getString(
                                        "transfermarkt_player_id"
                                )
                        )
                )
                .list();
    }
    private String safeMessage(
            RuntimeException exception
    ) {
        String message =
                exception.getMessage();

        if (message == null || message.isBlank()) {
            return exception
                    .getClass()
                    .getSimpleName();
        }

        return message.length() > 300
                ? message.substring(0, 300)
                : message;
    }

    private String firstNonBlank(
            String first,
            String second
    ) {
        return first != null && !first.isBlank()
                ? first
                : second;
    }

    private java.util.Optional<TransfermarktCandidate> knownTransfermarktCandidate(
            OperationalPlayer player
    ) {
        if (player.transfermarktPlayerId() == null
                || player.transfermarktPlayerId().isBlank()) {
            return java.util.Optional.empty();
        }

        String id = player.transfermarktPlayerId().trim();
        String normalizedSlug =
                TransfermarktCandidateMatcher
                        .normalize(
                                firstNonBlank(
                                        player.canonicalFullName(),
                                        player.name()
                                )
                        );

        String[] slugTokens =
                normalizedSlug.split(" ");

        String slug =
                slugTokens.length >= 5
                        ? slugTokens[0]
                        : normalizedSlug.replace(' ', '-');

        return java.util.Optional.of(
                new TransfermarktCandidate(
                        id,
                        firstNonBlank(
                                player.canonicalFullName(),
                                player.name()
                        ),
                        "https://www.transfermarkt.com/"
                                + slug
                                + "/profil/spieler/"
                                + id,
                        player.club()
                )
        );
    }

    static record OperationalPlayer(
            Long id,
            String name,
            String club,
            String canonicalFullName,
            LocalDate birthDate,
            String transfermarktPlayerId
    ) {
    }

    static record Verification(
            String status,
            String reason
    ) {
    }

    private record ScoredCandidate(
            TransfermarktCandidate candidate,
            int score
    ) {
    }

    private record VerifiedCandidate(
            ScoredCandidate scored,
            TransfermarktProfile profile,
            Verification verification
    ) {
    }

    private record PlayerResolution(
            String status,
            int score,
            TransfermarktCandidate candidate,
            TransfermarktProfile profile,
            String reason
    ) {
    }
}
