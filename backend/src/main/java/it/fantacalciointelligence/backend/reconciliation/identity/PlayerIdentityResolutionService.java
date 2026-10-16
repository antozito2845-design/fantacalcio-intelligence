package it.fantacalciointelligence.backend.reconciliation.identity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class PlayerIdentityResolutionService {

    private static final String FANTACALCIO = "FANTACALCIO";
    private static final String GAZZETTA = "GAZZETTA";
    private static final String FANTAMARKT = "FANTAMARKT";

    private final JdbcClient jdbcClient;

    public PlayerIdentityResolutionService(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    public List<ResolvedPlayerIdentity> resolve(
            long fantacalcioBatchId,
            long gazzettaBatchId,
            Long fantamarktBatchId
    ) {

        List<SourcePlayerRow> fantacalcioRows =
                loadRows(
                        fantacalcioBatchId,
                        FANTACALCIO
                );

        List<SourcePlayerRow> gazzettaRows =
                loadRows(
                        gazzettaBatchId,
                        GAZZETTA
                );

        List<SourcePlayerRow> fantamarktRows =
                fantamarktBatchId == null
                        ? List.of()
                        : loadRows(
                                fantamarktBatchId,
                                FANTAMARKT
                        );

        Map<String, Long> externalMappings =
                loadExternalMappings();

        Map<String, Set<Long>> verifiedAliases =
                loadVerifiedAliases();

        Map<String, String> verifiedSourceLinks =
                loadVerifiedSourceLinks();

        List<ResolvedPlayerIdentity> resolved =
                new ArrayList<>();

        for (SourcePlayerRow fantacalcio
                : fantacalcioRows) {

            Long fantacalcioPlayerId =
                    mappedPlayerId(
                            externalMappings,
                            FANTACALCIO,
                            fantacalcio.externalId()
                    );

            MatchResult gazzetta =
                    findSecondaryMatch(
                            fantacalcio,
                            fantacalcioPlayerId,
                            GAZZETTA,
                            gazzettaRows,
                            fantacalcioRows,
                            externalMappings,
                            verifiedAliases,
                            verifiedSourceLinks
                    );

            MatchResult fantamarkt =
                    findSecondaryMatch(
                            fantacalcio,
                            fantacalcioPlayerId,
                            FANTAMARKT,
                            fantamarktRows,
                            fantacalcioRows,
                            externalMappings,
                            verifiedAliases,
                            verifiedSourceLinks
                    );

            int sourcesCount = 1;

            if (gazzetta.row() != null) {
                sourcesCount++;
            }

            if (fantamarkt.row() != null) {
                sourcesCount++;
            }

            String resolutionMethod =
                    strongestMethod(
                            fantacalcioPlayerId,
                            gazzetta.method(),
                            fantamarkt.method()
                    );

            resolved.add(
                    new ResolvedPlayerIdentity(
                            fantacalcio.normalizedName(),
                            sourcesCount,
                            resolutionMethod,

                            fantacalcio.externalId(),
                            fantacalcio.externalName(),
                            fantacalcio.clubName(),
                            fantacalcio.normalizedClubName(),
                            fantacalcio.fantasyRole(),
                            fantacalcio.quotation(),
                            fantacalcio.fantasyMarketValue(),

                            value(
                                    gazzetta.row(),
                                    SourcePlayerRow::externalId
                            ),
                            value(
                                    gazzetta.row(),
                                    SourcePlayerRow::externalName
                            ),
                            value(
                                    gazzetta.row(),
                                    SourcePlayerRow::clubName
                            ),
                            value(
                                    gazzetta.row(),
                                    SourcePlayerRow::fantasyRole
                            ),

                            value(
                                    fantamarkt.row(),
                                    SourcePlayerRow::externalId
                            ),
                            value(
                                    fantamarkt.row(),
                                    SourcePlayerRow::externalName
                            ),
                            value(
                                    fantamarkt.row(),
                                    SourcePlayerRow::clubName
                            ),
                            value(
                                    fantamarkt.row(),
                                    SourcePlayerRow::fantasyRole
                            )
                    )
            );
        }

        return resolved;
    }

    private MatchResult findSecondaryMatch(
            SourcePlayerRow fantacalcio,
            Long fantacalcioPlayerId,
            String secondarySource,
            List<SourcePlayerRow> secondaryRows,
            List<SourcePlayerRow> fantacalcioRows,
            Map<String, Long> externalMappings,
            Map<String, Set<Long>> verifiedAliases,
            Map<String, String> verifiedSourceLinks
    ) {

        MatchResult verifiedSourceLink =
                findVerifiedSourceLinkMatch(
                        fantacalcio,
                        secondarySource,
                        secondaryRows,
                        verifiedSourceLinks
                );

        if (verifiedSourceLink.row() != null) {
            return verifiedSourceLink;
        }

        MatchResult historical =
                findHistoricalMappingMatch(
                        fantacalcioPlayerId,
                        secondarySource,
                        secondaryRows,
                        externalMappings
                );

        if (historical.row() != null) {
            return historical;
        }

        MatchResult alias =
                findVerifiedAliasMatch(
                        fantacalcioPlayerId,
                        secondarySource,
                        secondaryRows,
                        verifiedAliases
                );

        if (alias.row() != null) {
            return alias;
        }

        List<SourcePlayerRow> exactNameCandidates =
                secondaryRows.stream()
                        .filter(row ->
                                sameText(
                                        row.normalizedName(),
                                        fantacalcio.normalizedName()
                                )
                        )
                        .filter(row ->
                                mappingDoesNotConflict(
                                        fantacalcioPlayerId,
                                        secondarySource,
                                        row,
                                        externalMappings
                                )
                        )
                        .toList();

        if (exactNameCandidates.isEmpty()) {

            MatchResult normalized =
                    findSafeNormalizedMatch(
                            fantacalcio,
                            secondaryRows,
                            fantacalcioRows
                    );

            if (normalized.row() != null) {
                return normalized;
            }

            MatchResult abbreviation =
                    findSafeUniqueAbbreviationMatch(
                            fantacalcio,
                            secondaryRows,
                            fantacalcioRows
                    );

            if (abbreviation.row() != null) {
                return abbreviation;
            }

            return MatchResult.none();
        }

        List<SourcePlayerRow> sameClubCandidates =
                exactNameCandidates.stream()
                        .filter(row ->
                                sameText(
                                        row.normalizedClubName(),
                                        fantacalcio
                                                .normalizedClubName()
                                )
                        )
                        .toList();

        if (sameClubCandidates.size() == 1) {
            return new MatchResult(
                    sameClubCandidates.getFirst(),
                    "EXACT_NAME_SAME_CLUB"
            );
        }

        if (exactNameCandidates.size() == 1) {
            return new MatchResult(
                    exactNameCandidates.getFirst(),
                    "EXACT_NAME_UNIQUE"
            );
        }

        return MatchResult.none();
    }

    private MatchResult findSafeNormalizedMatch(
            SourcePlayerRow fantacalcio,
            List<SourcePlayerRow> secondaryRows,
            List<SourcePlayerRow> fantacalcioRows
    ) {

        String normalizedWithoutInitials =
                normalizedWithoutTrailingInitials(
                        fantacalcio
                );

        if (normalizedWithoutInitials == null
                || normalizedWithoutInitials.isBlank()) {
            return MatchResult.none();
        }

        List<SourcePlayerRow> exactCandidates =
                secondaryRows.stream()
                        .filter(row ->
                                sameText(
                                        row.normalizedClubName(),
                                        fantacalcio.normalizedClubName()
                                )
                        )
                        .filter(row ->
                                sameText(
                                        row.normalizedName(),
                                        normalizedWithoutInitials
                                )
                        )
                        .toList();

        if (exactCandidates.size() != 1) {
            return MatchResult.none();
        }

        SourcePlayerRow candidate =
                exactCandidates.getFirst();

        long reverseMatches =
                fantacalcioRows.stream()
                        .filter(row ->
                                sameText(
                                        row.normalizedClubName(),
                                        fantacalcio.normalizedClubName()
                                )
                        )
                        .filter(row ->
                                sameText(
                                        normalizedWithoutTrailingInitials(
                                                row
                                        ),
                                        candidate.normalizedName()
                                )
                        )
                        .count();

        if (reverseMatches != 1) {
            return MatchResult.none();
        }

        return new MatchResult(
                candidate,
                "NORMALIZED_WITHOUT_INITIALS"
        );
    }
    private String normalizedWithoutTrailingInitials(
            SourcePlayerRow row
    ) {

        if (row == null
                || row.normalizedName() == null
                || row.normalizedName().isBlank()) {
            return null;
        }

        String normalizedName =
                row.normalizedName()
                        .trim()
                        .toLowerCase();

        int abbreviationTokens =
                trailingAbbreviationTokenCount(
                        row.externalName()
                );

        if (abbreviationTokens == 0) {
            return normalizedName;
        }

        String[] tokens =
                normalizedName.split("\\s+");

        /*
         * Never remove the complete identity.
         * If the source representation is malformed,
         * keep the original normalized name instead.
         */
        if (abbreviationTokens >= tokens.length) {
            return normalizedName;
        }

        return String.join(
                " ",
                java.util.Arrays.copyOf(
                        tokens,
                        tokens.length - abbreviationTokens
                )
        );
    }

    private int trailingAbbreviationTokenCount(
            String externalName
    ) {

        if (externalName == null
                || externalName.isBlank()) {
            return 0;
        }

        String trimmed =
                externalName.trim();

        int lastSpace =
                trimmed.lastIndexOf(' ');

        if (lastSpace < 0
                || lastSpace ==
                   trimmed.length() - 1) {
            return 0;
        }

        String suffix =
                trimmed.substring(
                        lastSpace + 1
                );

        /*
         * Examples:
         *
         * D.S. -> 2
         * F.P. -> 2
         * Mas. -> 1
         * V.   -> 1
         *
         * A normal word without the final dot is
         * never interpreted as an abbreviation.
         */
        if (!suffix.matches(
                "(?iu)[\\p{L}]{1,3}(?:\\.[\\p{L}]{1,3})*\\."
        )) {
            return 0;
        }

        int count = 0;

        for (int i = 0;
             i < suffix.length();
             i++) {

            if (suffix.charAt(i) == '.') {
                count++;
            }
        }

        return count;
    }

    private String lastNormalizedToken(
            String normalizedName
    ) {

        if (normalizedName == null
                || normalizedName.isBlank()) {
            return null;
        }

        String[] tokens =
                normalizedName
                        .trim()
                        .split("\\s+");

        if (tokens.length == 0) {
            return null;
        }

        return tokens[tokens.length - 1];
    }
    private MatchResult findSafeUniqueAbbreviationMatch(
            SourcePlayerRow fantacalcio,
            List<SourcePlayerRow> secondaryRows,
            List<SourcePlayerRow> fantacalcioRows
    ) {

        String fantacalcioBaseName =
                abbreviationBaseName(
                        fantacalcio.externalName()
                );

        if (fantacalcioBaseName == null) {
            return MatchResult.none();
        }

        List<SourcePlayerRow> secondaryCandidates =
                secondaryRows.stream()
                        .filter(row ->
                                sameText(
                                        row.normalizedClubName(),
                                        fantacalcio.normalizedClubName()
                                )
                        )
                        .filter(row ->
                                sameText(
                                        abbreviationBaseName(
                                                row.externalName()
                                        ),
                                        fantacalcioBaseName
                                )
                        )
                        .toList();

        if (secondaryCandidates.size() != 1) {
            return MatchResult.none();
        }

        /*
         * Reverse uniqueness is mandatory.
         *
         * Example rejected automatically:
         *
         * Sulemana I. -> SULEMANA
         * Sulemana K. -> SULEMANA
         */
        long matchingFantacalcioPlayers =
                fantacalcioRows.stream()
                        .filter(row ->
                                sameText(
                                        row.normalizedClubName(),
                                        fantacalcio.normalizedClubName()
                                )
                        )
                        .filter(row ->
                                sameText(
                                        abbreviationBaseName(
                                                row.externalName()
                                        ),
                                        fantacalcioBaseName
                                )
                        )
                        .count();

        if (matchingFantacalcioPlayers != 1) {
            return MatchResult.none();
        }

        return new MatchResult(
                secondaryCandidates.getFirst(),
                "SAFE_UNIQUE_ABBREVIATION"
        );
    }

    private String abbreviationBaseName(
            String value
    ) {

        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmed =
                value.trim();

        /*
         * Only remove an explicit abbreviated suffix ending
         * with a dot:
         *
         * Ederson D.S.   -> Ederson
         * Esposito F.P.  -> Esposito
         * Pessina Mas.   -> Pessina
         *
         * A normal short surname without a dot is untouched.
         */
        String base =
                trimmed.replaceFirst(
                        "(?iu)\\s+[\\p{L}]{1,3}(?:\\.[\\p{L}]{1,3})*\\.$",
                        ""
                );

        return base.trim()
                .toLowerCase();
    }
    private MatchResult findHistoricalMappingMatch(
            Long fantacalcioPlayerId,
            String secondarySource,
            List<SourcePlayerRow> secondaryRows,
            Map<String, Long> externalMappings
    ) {

        if (fantacalcioPlayerId == null) {
            return MatchResult.none();
        }

        List<SourcePlayerRow> matches =
                secondaryRows.stream()
                        .filter(row -> {
                            Long secondaryPlayerId =
                                    mappedPlayerId(
                                            externalMappings,
                                            secondarySource,
                                            row.externalId()
                                    );

                            return fantacalcioPlayerId
                                    .equals(
                                            secondaryPlayerId
                                    );
                        })
                        .toList();

        if (matches.size() == 1) {
            return new MatchResult(
                    matches.getFirst(),
                    "HISTORICAL_MAPPING"
            );
        }

        return MatchResult.none();
    }

    private MatchResult findVerifiedAliasMatch(
            Long fantacalcioPlayerId,
            String secondarySource,
            List<SourcePlayerRow> secondaryRows,
            Map<String, Set<Long>> verifiedAliases
    ) {

        if (fantacalcioPlayerId == null) {
            return MatchResult.none();
        }

        List<SourcePlayerRow> matches =
                secondaryRows.stream()
                        .filter(row -> {
                            Set<Long> playerIds =
                                    verifiedAliases.get(
                                            aliasKey(
                                                    secondarySource,
                                                    row.normalizedName()
                                            )
                                    );

                            return playerIds != null
                                    && playerIds.size() == 1
                                    && playerIds.contains(
                                            fantacalcioPlayerId
                                    );
                        })
                        .toList();

        if (matches.size() == 1) {
            return new MatchResult(
                    matches.getFirst(),
                    "VERIFIED_ALIAS"
            );
        }

        return MatchResult.none();
    }

    private boolean mappingDoesNotConflict(
            Long fantacalcioPlayerId,
            String source,
            SourcePlayerRow secondary,
            Map<String, Long> externalMappings
    ) {

        if (fantacalcioPlayerId == null) {
            return true;
        }

        Long secondaryPlayerId =
                mappedPlayerId(
                        externalMappings,
                        source,
                        secondary.externalId()
                );

        return secondaryPlayerId == null
                || fantacalcioPlayerId.equals(
                        secondaryPlayerId
                );
    }

    private String strongestMethod(
            Long fantacalcioPlayerId,
            String first,
            String second
    ) {

        List<String> methods =
                List.of(
                        safe(first),
                        safe(second)
                );

        if (methods.contains(
                "VERIFIED_SOURCE_LINK"
        )) {
            return "VERIFIED_SOURCE_LINK";
        }

        if (methods.contains(
                "HISTORICAL_MAPPING"
        )) {
            return "HISTORICAL_MAPPING";
        }

        if (methods.contains(
                "VERIFIED_ALIAS"
        )) {
            return "VERIFIED_ALIAS";
        }

        if (methods.contains(
                "EXACT_NAME_SAME_CLUB"
        )) {
            return "EXACT_NAME_SAME_CLUB";
        }

        if (methods.contains(
                "NORMALIZED_WITHOUT_INITIALS"
        )) {
            return "NORMALIZED_WITHOUT_INITIALS";
        }

        if (methods.contains(
                "SAFE_UNIQUE_ABBREVIATION"
        )) {
            return "SAFE_UNIQUE_ABBREVIATION";
        }

        if (methods.contains(
                "UNIQUE_NORMALIZED_FINAL_TOKEN"
        )) {
            return "UNIQUE_NORMALIZED_FINAL_TOKEN";
        }

        if (methods.contains(
                "EXACT_NAME_UNIQUE"
        )) {
            return "EXACT_NAME_UNIQUE";
        }

        if (fantacalcioPlayerId != null) {
            return "EXTERNAL_ID";
        }

        return "FANTACALCIO_ONLY";
    }

    private List<SourcePlayerRow> loadRows(
            long batchId,
            String source
    ) {

        return jdbcClient.sql("""
                SELECT
                    external_id,
                    external_name,
                    normalized_name,
                    club_name,
                    normalized_club_name,
                    fantasy_role,
                    quotation,
                    fantasy_market_value

                FROM player_import_rows

                WHERE batch_id = :batchId
                  AND valid = TRUE

                ORDER BY row_number, id
                """)
                .param("batchId", batchId)
                .query((resultSet, rowNumber) ->
                        new SourcePlayerRow(
                                source,
                                resultSet.getString(
                                        "external_id"
                                ),
                                resultSet.getString(
                                        "external_name"
                                ),
                                resultSet.getString(
                                        "normalized_name"
                                ),
                                resultSet.getString(
                                        "club_name"
                                ),
                                resultSet.getString(
                                        "normalized_club_name"
                                ),
                                resultSet.getString(
                                        "fantasy_role"
                                ),
                                resultSet.getBigDecimal(
                                        "quotation"
                                ),
                                resultSet.getBigDecimal(
                                        "fantasy_market_value"
                                )
                        )
                )
                .list();
    }

    private MatchResult findVerifiedSourceLinkMatch(
            SourcePlayerRow fantacalcio,
            String secondarySource,
            List<SourcePlayerRow> secondaryRows,
            Map<String, String> verifiedSourceLinks
    ) {

        String secondaryExternalId =
                verifiedSourceLinks.get(
                        sourceLinkKey(
                                fantacalcio.externalId(),
                                secondarySource
                        )
                );

        if (secondaryExternalId == null
                || secondaryExternalId.isBlank()) {
            return MatchResult.none();
        }

        List<SourcePlayerRow> matches =
                secondaryRows.stream()
                        .filter(row ->
                                sameText(
                                        row.externalId(),
                                        secondaryExternalId
                                )
                        )
                        .toList();

        if (matches.size() != 1) {
            return MatchResult.none();
        }

        return new MatchResult(
                matches.getFirst(),
                "VERIFIED_SOURCE_LINK"
        );
    }

    private Map<String, String> loadVerifiedSourceLinks() {

        Map<String, String> links =
                new LinkedHashMap<>();

        jdbcClient.sql("""
                SELECT
                    fantacalcio_external_id,
                    secondary_source,
                    secondary_external_id
                FROM verified_source_identity_links
                WHERE verified = TRUE
                ORDER BY id
                """)
                .query()
                .listOfRows()
                .forEach(row ->
                        links.put(
                                sourceLinkKey(
                                        String.valueOf(
                                                row.get(
                                                        "fantacalcio_external_id"
                                                )
                                        ),
                                        String.valueOf(
                                                row.get(
                                                        "secondary_source"
                                                )
                                        )
                                ),
                                String.valueOf(
                                        row.get(
                                                "secondary_external_id"
                                        )
                                )
                        )
                );

        return links;
    }

    private String sourceLinkKey(
            String fantacalcioExternalId,
            String secondarySource
    ) {
        return safe(fantacalcioExternalId)
                + "|"
                + safe(secondarySource).toUpperCase();
    }

    private Map<String, Long> loadExternalMappings() {

        Map<String, Long> mappings =
                new LinkedHashMap<>();

        jdbcClient.sql("""
                SELECT
                    source,
                    external_id,
                    player_id

                FROM player_source_mappings

                WHERE external_id IS NOT NULL
                  AND external_id <> ''
                """)
                .query()
                .listOfRows()
                .forEach(row -> {
                    String source =
                            String.valueOf(
                                    row.get("source")
                            );

                    String externalId =
                            String.valueOf(
                                    row.get("external_id")
                            );

                    Long playerId =
                            ((Number) row.get(
                                    "player_id"
                            )).longValue();

                    mappings.put(
                            mappingKey(
                                    source,
                                    externalId
                            ),
                            playerId
                    );
                });

        return mappings;
    }

    private Map<String, Set<Long>>
            loadVerifiedAliases() {

        Map<String, Set<Long>> aliases =
                new HashMap<>();

        jdbcClient.sql("""
                SELECT
                    source,
                    normalized_alias,
                    player_id

                FROM player_identity_aliases

                WHERE verified = TRUE
                """)
                .query()
                .listOfRows()
                .forEach(row -> {
                    String source =
                            String.valueOf(
                                    row.get("source")
                            );

                    String normalizedAlias =
                            String.valueOf(
                                    row.get(
                                            "normalized_alias"
                                    )
                            );

                    Long playerId =
                            ((Number) row.get(
                                    "player_id"
                            )).longValue();

                    aliases
                            .computeIfAbsent(
                                    aliasKey(
                                            source,
                                            normalizedAlias
                                    ),
                                    ignored ->
                                            new LinkedHashSet<>()
                            )
                            .add(playerId);
                });

        return aliases;
    }

    private Long mappedPlayerId(
            Map<String, Long> mappings,
            String source,
            String externalId
    ) {

        if (externalId == null
                || externalId.isBlank()) {
            return null;
        }

        return mappings.get(
                mappingKey(
                        source,
                        externalId
                )
        );
    }

    private String mappingKey(
            String source,
            String externalId
    ) {
        return safe(source).toUpperCase()
                + "|"
                + safe(externalId);
    }

    private String aliasKey(
            String source,
            String normalizedAlias
    ) {
        return safe(source).toUpperCase()
                + "|"
                + safe(normalizedAlias);
    }

    private boolean sameText(
            String first,
            String second
    ) {

        if (first == null || second == null) {
            return false;
        }

        return first.trim()
                .equalsIgnoreCase(
                        second.trim()
                );
    }

    private String safe(
            String value
    ) {
        return value == null
                ? ""
                : value.trim();
    }

    private <T> String value(
            T object,
            java.util.function.Function<T, String> mapper
    ) {

        if (object == null) {
            return null;
        }

        return mapper.apply(object);
    }

    private record MatchResult(
            SourcePlayerRow row,
            String method
    ) {

        private static MatchResult none() {
            return new MatchResult(
                    null,
                    null
            );
        }
    }

    private record SourcePlayerRow(
            String source,
            String externalId,
            String externalName,
            String normalizedName,
            String clubName,
            String normalizedClubName,
            String fantasyRole,
            BigDecimal quotation,
            BigDecimal fantasyMarketValue
    ) {
    }
}