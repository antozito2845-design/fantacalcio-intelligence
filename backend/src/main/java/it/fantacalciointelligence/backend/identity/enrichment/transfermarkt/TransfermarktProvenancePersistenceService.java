package it.fantacalciointelligence.backend.identity.enrichment.transfermarkt;

import java.sql.Types;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.identity.enrichment.NationalityNormalizer;

@Service
public class TransfermarktProvenancePersistenceService {

    private static final String SOURCE = "TRANSFERMARKT";
    private static final String CONFIDENCE = "HIGH";

    private final JdbcClient jdbc;

    public TransfermarktProvenancePersistenceService(
            JdbcClient jdbc
    ) {
        this.jdbc = jdbc;
    }

    @Transactional
    public int persist(
            String season,
            TransfermarktDiscoveryResponse response
    ) {
        int persistedFields = 0;

        for (
                TransfermarktDiscoveryResponse.PlayerDiscovery player
                : response.players()
        ) {
            if (!"MATCHED_VERIFIED".equals(player.status())) {
                continue;
            }

            if (isBlank(player.transfermarktPlayerId())) {
                continue;
            }

            ensureIdentityProfile(player);
            fillMissingCanonicalFields(player);

            persistedFields += persistField(
                    player,
                    season,
                    "CANONICAL_FULL_NAME",
                    player.transfermarktName()
            );

            persistedFields += persistField(
                    player,
                    season,
                    "BIRTH_DATE",
                    player.birthDate()
            );

            persistedFields += persistField(
                    player,
                    season,
                    "NATIONALITY",
                    player.nationality()
            );

            persistedFields += persistField(
                    player,
                    season,
                    "HEIGHT_CM",
                    player.heightCm()
            );

            persistedFields += persistField(
                    player,
                    season,
                    "PREFERRED_FOOT",
                    player.preferredFoot()
            );

            persistedFields += persistField(
                    player,
                    season,
                    "TRANSFERMARKT_PLAYER_ID",
                    player.transfermarktPlayerId()
            );
        }

        return persistedFields;
    }

    /**
     * Automatic discovery can legitimately find a newly reconciled player
     * before a player_identity_profiles row has ever been imported.
     *
     * Only MATCHED_VERIFIED players reach this method.
     *
     * A new profile is therefore created as VERIFIED_PARTIAL/HIGH using
     * source-verified name and birth date. Existing profiles are never
     * replaced here.
     */
    private void ensureIdentityProfile(
            TransfermarktDiscoveryResponse.PlayerDiscovery player
    ) {

        if (
                isBlank(player.transfermarktName())
                        || player.birthDate() == null
        ) {
            return;
        }

        String nationality =
                clean(player.nationality());

        String nationalityCode =
                NationalityNormalizer.resolveCode(
                        nationality,
                        null,
                        player.transfermarktPlayerId()
                );

        jdbc.sql("""
                INSERT INTO player_identity_profiles (
                    player_id,
                    canonical_full_name,
                    birth_date,
                    nationality,
                    nationality_code,
                    height_cm,
                    preferred_foot,
                    transfermarkt_player_id,
                    identity_status,
                    confidence
                )
                VALUES (
                    :playerId,
                    :canonicalFullName,
                    :birthDate,
                    :nationality,
                    :nationalityCode,
                    :heightCm,
                    :preferredFoot,
                    :transfermarktPlayerId,
                    'VERIFIED_PARTIAL',
                    'HIGH'
                )
                ON CONFLICT (player_id)
                DO NOTHING
                """)
                .param(
                        "playerId",
                        player.playerId()
                )
                .param(
                        "canonicalFullName",
                        player.transfermarktName(),
                        Types.VARCHAR
                )
                .param(
                        "birthDate",
                        player.birthDate(),
                        Types.DATE
                )
                .param(
                        "nationality",
                        nationality,
                        Types.VARCHAR
                )
                .param(
                        "nationalityCode",
                        nationalityCode,
                        Types.CHAR
                )
                .param(
                        "heightCm",
                        player.heightCm(),
                        Types.SMALLINT
                )
                .param(
                        "preferredFoot",
                        clean(player.preferredFoot()),
                        Types.VARCHAR
                )
                .param(
                        "transfermarktPlayerId",
                        player.transfermarktPlayerId(),
                        Types.VARCHAR
                )
                .update();
    }
    private void fillMissingCanonicalFields(
            TransfermarktDiscoveryResponse.PlayerDiscovery player
    ) {
        String nationality =
                clean(player.nationality());

        String nationalityCode =
                NationalityNormalizer.resolveCode(
                        nationality,
                        null,
                        player.transfermarktPlayerId()
                );

        String preferredFoot =
                clean(player.preferredFoot());

        jdbc.sql("""
                UPDATE player_identity_profiles
                SET
                    birth_date = COALESCE(
                        birth_date,
                        :birthDate
                    ),
                    nationality = COALESCE(
                        nationality,
                        :nationality
                    ),
                    nationality_code = COALESCE(
                        nationality_code,
                        :nationalityCode
                    ),
                    height_cm = COALESCE(
                        height_cm,
                        :heightCm
                    ),
                    preferred_foot = COALESCE(
                        preferred_foot,
                        :preferredFoot
                    ),
                    transfermarkt_player_id = COALESCE(
                        transfermarkt_player_id,
                        :transfermarktPlayerId
                    ),
                    updated_at = CURRENT_TIMESTAMP
                WHERE player_id = :playerId
                """)
                .param(
                        "birthDate",
                        player.birthDate(),
                        Types.DATE
                )
                .param(
                        "nationality",
                        nationality,
                        Types.VARCHAR
                )
                .param(
                        "nationalityCode",
                        nationalityCode,
                        Types.CHAR
                )
                .param(
                        "heightCm",
                        player.heightCm(),
                        Types.SMALLINT
                )
                .param(
                        "preferredFoot",
                        preferredFoot,
                        Types.VARCHAR
                )
                .param(
                        "transfermarktPlayerId",
                        player.transfermarktPlayerId(),
                        Types.VARCHAR
                )
                .param(
                        "playerId",
                        player.playerId()
                )
                .update();

        jdbc.sql("""
                UPDATE players
                SET
                    birth_date = COALESCE(
                        birth_date,
                        :birthDate
                    ),
                    nationality_code = COALESCE(
                        nationality_code,
                        :nationalityCode
                    ),
                    height_cm = COALESCE(
                        height_cm,
                        :heightCm
                    ),
                    preferred_foot = COALESCE(
                        preferred_foot,
                        :preferredFoot
                    ),
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :playerId
                """)
                .param(
                        "birthDate",
                        player.birthDate(),
                        Types.DATE
                )
                .param(
                        "nationalityCode",
                        nationalityCode,
                        Types.CHAR
                )
                .param(
                        "heightCm",
                        player.heightCm(),
                        Types.SMALLINT
                )
                .param(
                        "preferredFoot",
                        preferredFoot,
                        Types.VARCHAR
                )
                .param(
                        "playerId",
                        player.playerId()
                )
                .update();
    }

    private int persistField(
            TransfermarktDiscoveryResponse.PlayerDiscovery player,
            String season,
            String fieldName,
            Object observedValue
    ) {
        if (observedValue == null) {
            return 0;
        }

        String normalizedValue =
                normalizeValue(observedValue);

        if (
                normalizedValue == null
                || normalizedValue.isBlank()
        ) {
            return 0;
        }

        String evidence =
                buildEvidence(
                        normalizedValue,
                        season,
                        player
                );

        jdbc.sql("""
                INSERT INTO player_identity_provenance (
                    player_id,
                    field_name,
                    source,
                    source_external_id,
                    source_url,
                    confidence,
                    evidence
                )
                VALUES (
                    :playerId,
                    :fieldName,
                    :source,
                    :sourceExternalId,
                    :sourceUrl,
                    :confidence,
                    CAST(:evidence AS JSONB)
                )
                ON CONFLICT (
                    player_id,
                    field_name,
                    source,
                    source_external_id
                )
                DO UPDATE SET
                    source_url = EXCLUDED.source_url,
                    confidence = EXCLUDED.confidence,
                    evidence = EXCLUDED.evidence,
                    imported_at = CURRENT_TIMESTAMP
                """)
                .param(
                        "playerId",
                        player.playerId()
                )
                .param(
                        "fieldName",
                        fieldName
                )
                .param(
                        "source",
                        SOURCE
                )
                .param(
                        "sourceExternalId",
                        player.transfermarktPlayerId()
                )
                .param(
                        "sourceUrl",
                        player.transfermarktUrl(),
                        Types.VARCHAR
                )
                .param(
                        "confidence",
                        CONFIDENCE
                )
                .param(
                        "evidence",
                        evidence
                )
                .update();

        return 1;
    }

    private String buildEvidence(
            String observedValue,
            String season,
            TransfermarktDiscoveryResponse.PlayerDiscovery player
    ) {
        Map<String, Object> evidence =
                new LinkedHashMap<>();

        evidence.put(
                "value",
                observedValue
        );

        evidence.put(
                "season",
                season
        );

        evidence.put(
                "verification",
                "MATCHED_VERIFIED"
        );

        evidence.put(
                "score",
                player.score()
        );

        if (
                !isBlank(
                        player.transfermarktCurrentClub()
                )
        ) {
            evidence.put(
                    "transfermarktCurrentClub",
                    player.transfermarktCurrentClub()
            );
        }

        if (!isBlank(player.reason())) {
            evidence.put(
                    "reason",
                    player.reason()
            );
        }

        return toJson(evidence);
    }

    private String normalizeValue(
            Object value
    ) {
        if (value instanceof LocalDate localDate) {
            return localDate.toString();
        }

        if (value instanceof Integer integer) {
            return integer.toString();
        }

        return value.toString().trim();
    }

    private String clean(
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

    private String toJson(
            Map<String, Object> values
    ) {
        StringBuilder json =
                new StringBuilder("{");

        boolean first = true;

        for (
                Map.Entry<String, Object> entry
                : values.entrySet()
        ) {
            if (!first) {
                json.append(',');
            }

            first = false;

            json.append('"')
                    .append(
                            escapeJson(
                                    entry.getKey()
                            )
                    )
                    .append("\":");

            Object value =
                    entry.getValue();

            if (
                    value instanceof Number
                    || value instanceof Boolean
            ) {
                json.append(value);
            } else {
                json.append('"')
                        .append(
                                escapeJson(
                                        String.valueOf(value)
                                )
                        )
                        .append('"');
            }
        }

        json.append('}');

        return json.toString();
    }

    private String escapeJson(
            String value
    ) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    private boolean isBlank(
            String value
    ) {
        return value == null
                || value.isBlank();
    }
}