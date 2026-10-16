package it.fantacalciointelligence.backend.identity.enrichment;

import java.util.Locale;
import java.util.Map;

public final class NationalityNormalizer {

    private static final Map<String, String> CODES = Map.ofEntries(
            Map.entry("albania", "AL"),
            Map.entry("angola", "AO"),
            Map.entry("argentina", "AR"),
            Map.entry("armenia", "AM"),
            Map.entry("australia", "AU"),
            Map.entry("austria", "AT"),
            Map.entry("belgio", "BE"),
            Map.entry("bosnia erzegovina", "BA"),
            Map.entry("brasile", "BR"),
            Map.entry("bulgaria", "BG"),
            Map.entry("camerun", "CM"),
            Map.entry("canada", "CA"),
            Map.entry("colombia", "CO"),
            Map.entry("costa d'avorio", "CI"),
            Map.entry("croazia", "HR"),
            Map.entry("danimarca", "DK"),
            Map.entry("ecuador", "EC"),
            Map.entry("england", "GB"),
            Map.entry("finlandia", "FI"),
            Map.entry("francia", "FR"),
            Map.entry("gabon", "GA"),
            Map.entry("gambia", "GM"),
            Map.entry("georgia", "GE"),
            Map.entry("germania", "DE"),
            Map.entry("ghana", "GH"),
            Map.entry("giappone", "JP"),
            Map.entry("grecia", "GR"),
            Map.entry("guinea-bissau", "GW"),
            Map.entry("guinea equatoriale", "GQ"),
            Map.entry("indonesia", "ID"),
            Map.entry("inghilterra", "GB"),
            Map.entry("irlanda", "IE"),
            Map.entry("islanda", "IS"),
            Map.entry("israele", "IL"),
            Map.entry("italia", "IT"),
            Map.entry("italy", "IT"),
            Map.entry("kosovo", "XK"),
            Map.entry("lituania", "LT"),
            Map.entry("mali", "ML"),
            Map.entry("marocco", "MA"),
            Map.entry("mauritania", "MR"),
            Map.entry("messico", "MX"),
            Map.entry("montenegro", "ME"),
            Map.entry("niger", "NE"),
            Map.entry("nigeria", "NG"),
            Map.entry("norvegia", "NO"),
            Map.entry("olanda", "NL"),
            Map.entry("polonia", "PL"),
            Map.entry("portogallo", "PT"),
            Map.entry("repubblica ceca", "CZ"),
            Map.entry("romania", "RO"),
            Map.entry("scozia", "GB"),
            Map.entry("senegal", "SN"),
            Map.entry("serbia", "RS"),
            Map.entry("slovacchia", "SK"),
            Map.entry("slovenia", "SI"),
            Map.entry("spagna", "ES"),
            Map.entry("spain", "ES"),
            Map.entry("stati uniti", "US"),
            Map.entry("suriname", "SR"),
            Map.entry("svezia", "SE"),
            Map.entry("svizzera", "CH"),
            Map.entry("togo", "TG"),
            Map.entry("turchia", "TR"),
            Map.entry("ucraina", "UA"),
            Map.entry("uruguay", "UY"),
            Map.entry("zambia", "ZM")
    );

    private static final Map<String, String> VERIFIED_NATIONALITY_OVERRIDES =
            Map.of(
                    "1131161", "Francia, Guinea-Bissau",
                    "992569", "Italia"
            );

    private NationalityNormalizer() {
    }

    public static String normalizeNationality(
            String nationality,
            String transfermarktPlayerId
    ) {
        String tmId = clean(transfermarktPlayerId);

        if (tmId != null) {
            String override =
                    VERIFIED_NATIONALITY_OVERRIDES.get(tmId);

            if (override != null) {
                return override;
            }
        }

        return clean(nationality);
    }

    public static String resolveCode(
            String nationality,
            String explicitCode,
            String transfermarktPlayerId
    ) {
        String normalizedNationality =
                normalizeNationality(
                        nationality,
                        transfermarktPlayerId
                );

        String mapped =
                codeForNationality(normalizedNationality);

        if (mapped != null) {
            return mapped;
        }

        String explicit = clean(explicitCode);

        return explicit == null
                ? null
                : explicit.toUpperCase(Locale.ROOT);
    }

    public static String codeForNationality(
            String nationality
    ) {
        String value = clean(nationality);

        if (value == null) {
            return null;
        }

        String primary =
                value.split(",", 2)[0]
                        .trim()
                        .toLowerCase(Locale.ROOT);

        return CODES.get(primary);
    }

    private static String clean(
            String value
    ) {
        if (value == null) {
            return null;
        }

        String cleaned = value.trim();

        return cleaned.isEmpty()
                ? null
                : cleaned;
    }
}