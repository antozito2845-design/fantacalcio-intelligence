package it.fantacalciointelligence.backend.identity.enrichment;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import it.fantacalciointelligence.backend.common.BadRequestException;

@Service
public class IdentityEnrichmentImportService {

    private static final Set<String> STATUSES = Set.of(
            "VERIFIED_COMPLETE",
            "VERIFIED_PARTIAL",
            "NO_SERIE_A_HISTORY_EXPECTED",
            "REVIEW_REQUIRED",
            "DATA_SOURCE_MISSING"
    );

    private static final Set<String> CONFIDENCES = Set.of(
            "HIGH",
            "MEDIUM",
            "LOW",
            "NONE"
    );

    private static final Set<String> SOURCES = Set.of(
            "WIKIDATA",
            "FANTACALCIO",
            "TRANSFERMARKT",
            "UEFA",
            "FIGC",
            "LEGA_SERIE_A",
            "OFFICIAL_CLUB",
            "MANUAL_VERIFIED"
    );

    private static final Set<String> FEET = Set.of(
            "RIGHT",
            "LEFT",
            "BOTH"
    );

    private final JdbcClient jdbcClient;

    public IdentityEnrichmentImportService(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional
    public IdentityEnrichmentImportResponse importCsv(
            MultipartFile file
    ) {

        if (file == null || file.isEmpty()) {
            throw new BadRequestException(
                    "File identity CSV obbligatorio"
            );
        }

        int total = 0;
        int imported = 0;
        int complete = 0;
        int partial = 0;
        int noHistory = 0;
        int review = 0;
        int missing = 0;

        try (
                var reader = new BufferedReader(
                        new InputStreamReader(
                                file.getInputStream(),
                                StandardCharsets.UTF_8
                        )
                );

                var parser = CSVFormat.DEFAULT
                        .builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .setTrim(true)
                        .setIgnoreEmptyLines(true)
                        .get()
                        .parse(reader)
        ) {

            requireHeaders(
                    parser.getHeaderNames()
            );

            for (CSVRecord record : parser) {

                total++;

                Row row = parse(record);

                validate(row);

                upsert(row);

                imported++;

                switch (row.status()) {
                    case "VERIFIED_COMPLETE" ->
                            complete++;
                    case "VERIFIED_PARTIAL" ->
                            partial++;
                    case "NO_SERIE_A_HISTORY_EXPECTED" ->
                            noHistory++;
                    case "REVIEW_REQUIRED" ->
                            review++;
                    default ->
                            missing++;
                }
            }

        }
        catch (BadRequestException exception) {
            throw exception;
        }
        catch (Exception exception) {
            throw new BadRequestException(
                    "Import identity enrichment fallito: "
                            + exception.getMessage(),
                    exception
            );
        }

        return new IdentityEnrichmentImportResponse(
                total,
                imported,
                complete,
                partial,
                noHistory,
                review,
                missing
        );
    }

    private void requireHeaders(
            List<String> headers
    ) {

        Set<String> required = Set.of(
                "player_id",
                "canonical_full_name",
                "first_name",
                "last_name",
                "birth_date",
                "nationality",
                "nationality_code",
                "height_cm",
                "preferred_foot",
                "transfermarkt_player_id",
                "identity_status",
                "confidence",
                "source",
                "source_external_id",
                "source_url",
                "evidence"
        );

        List<String> missing = required
                .stream()
                .filter(column ->
                        !headers.contains(column)
                )
                .sorted()
                .toList();

        if (!missing.isEmpty()) {
            throw new BadRequestException(
                    "Colonne identity mancanti: "
                            + String.join(", ", missing)
            );
        }
    }

    private Row parse(
            CSVRecord record
    ) {

        try {
            return new Row(
                    Long.valueOf(
                            value(
                                    record,
                                    "player_id"
                            )
                    ),
                    value(
                            record,
                            "canonical_full_name"
                    ),
                    value(
                            record,
                            "first_name"
                    ),
                    value(
                            record,
                            "last_name"
                    ),
                    date(
                            value(
                                    record,
                                    "birth_date"
                            )
                    ),
                    NationalityNormalizer.normalizeNationality(
                            value(
                                    record,
                                    "nationality"
                            ),
                            value(
                                    record,
                                    "transfermarkt_player_id"
                            )
                    ),
                    NationalityNormalizer.resolveCode(
                            value(
                                    record,
                                    "nationality"
                            ),
                            value(
                                    record,
                                    "nationality_code"
                            ),
                            value(
                                    record,
                                    "transfermarkt_player_id"
                            )
                    ),
                    integer(
                            value(
                                    record,
                                    "height_cm"
                            )
                    ),
                    upper(
                            value(
                                    record,
                                    "preferred_foot"
                            )
                    ),
                    upper(
                            value(
                                    record,
                                    "identity_status"
                            )
                    ),
                    upper(
                            value(
                                    record,
                                    "confidence"
                            )
                    ),
                    upper(
                            value(
                                    record,
                                    "source"
                            )
                    ),
                    value(
                            record,
                            "transfermarkt_player_id"
                    ),
                    value(
                            record,
                            "source_external_id"
                    ),
                    value(
                            record,
                            "source_url"
                    ),
                    value(
                            record,
                            "evidence"
                    )
            );
        }
        catch (RuntimeException exception) {
            throw new BadRequestException(
                    "Riga identity non valida "
                            + record.getRecordNumber()
                            + ": "
                            + exception.getMessage()
            );
        }
    }

    private void validate(
            Row row
    ) {

        if (
                !STATUSES.contains(row.status())
                || !CONFIDENCES.contains(
                        row.confidence()
                )
        ) {
            throw new BadRequestException(
                    "Stato/confidence identity non valido "
                            + "per player "
                            + row.playerId()
            );
        }

        if (!SOURCES.contains(row.source())) {
            throw new BadRequestException(
                    "Fonte enrichment non approvata: "
                            + row.source()
            );
        }

        boolean verified =
                isVerified(row);

        if (
                verified
                && (
                        !"HIGH".equals(
                                row.confidence()
                        )
                        || row.canonicalName() == null
                        || row.birthDate() == null
                        || (
                                row.sourceExternalId() == null
                                && row.sourceUrl() == null
                        )
                )
        ) {
            throw new BadRequestException(
                    "Profilo verified incompleto "
                            + "per player "
                            + row.playerId()
            );
        }

        if (
                row.nationalityCode() != null
                && row.nationalityCode()
                        .length() != 2
        ) {
            throw new BadRequestException(
                    "Country code non ISO-2 "
                            + "per player "
                            + row.playerId()
            );
        }

        if (
                row.heightCm() != null
                && (
                        row.heightCm() < 140
                        || row.heightCm() > 220
                )
        ) {
            throw new BadRequestException(
                    "Altezza non valida "
                            + "per player "
                            + row.playerId()
            );
        }

        if (
                row.preferredFoot() != null
                && !FEET.contains(
                        row.preferredFoot()
                )
        ) {
            throw new BadRequestException(
                    "Piede preferito non valido "
                            + "per player "
                            + row.playerId()
            );
        }

        if (
                row.transfermarktPlayerId() != null
                && !row.transfermarktPlayerId()
                        .matches("[1-9][0-9]*")
        ) {
            throw new BadRequestException(
                    "Transfermarkt ID non valido "
                            + "per player "
                            + row.playerId()
            );
        }

        if (
                row.transfermarktPlayerId() != null
                && !"TRANSFERMARKT".equals(
                        row.source()
                )
        ) {
            throw new BadRequestException(
                    "Transfermarkt ID privo "
                            + "di provenance Transfermarkt"
            );
        }

        Map<String, Object> existingPlayer =
                jdbcClient
                        .sql("""
                                SELECT
                                    first_name,
                                    birth_date,
                                    nationality_code,
                                    height_cm,
                                    preferred_foot
                                FROM players
                                WHERE id = :playerId
                                """)
                        .param(
                                "playerId",
                                row.playerId()
                        )
                        .query()
                        .listOfRows()
                        .stream()
                        .findFirst()
                        .orElseThrow(() ->
                                new BadRequestException(
                                        "Player inesistente: "
                                                + row.playerId()
                                )
                        );

        if (verified) {

            rejectConflict(
                    existingPlayer,
                    "first_name",
                    row.firstName(),
                    row.playerId()
            );

            rejectConflict(
                    existingPlayer,
                    "birth_date",
                    row.birthDate(),
                    row.playerId()
            );

            rejectConflict(
                    existingPlayer,
                    "nationality_code",
                    row.nationalityCode(),
                    row.playerId()
            );

            rejectConflict(
                    existingPlayer,
                    "height_cm",
                    row.heightCm(),
                    row.playerId()
            );

            rejectConflict(
                    existingPlayer,
                    "preferred_foot",
                    row.preferredFoot(),
                    row.playerId()
            );

            validateExistingProfileConflicts(row);
        }

        validateTransfermarktIdentity(row);
    }

    private void validateExistingProfileConflicts(
            Row row
    ) {

        Map<String, Object> existingProfile =
                jdbcClient
                        .sql("""
                                SELECT
                                    birth_date,
                                    nationality_code,
                                    height_cm,
                                    preferred_foot,
                                    transfermarkt_player_id
                                FROM player_identity_profiles
                                WHERE player_id = :playerId
                                """)
                        .param(
                                "playerId",
                                row.playerId()
                        )
                        .query()
                        .listOfRows()
                        .stream()
                        .findFirst()
                        .orElse(null);

        if (existingProfile == null) {
            return;
        }

        rejectConflict(
                existingProfile,
                "birth_date",
                row.birthDate(),
                row.playerId()
        );

        rejectConflict(
                existingProfile,
                "nationality_code",
                row.nationalityCode(),
                row.playerId()
        );

        rejectConflict(
                existingProfile,
                "height_cm",
                row.heightCm(),
                row.playerId()
        );

        rejectConflict(
                existingProfile,
                "preferred_foot",
                row.preferredFoot(),
                row.playerId()
        );


    }

    private void validateTransfermarktIdentity(
            Row row
    ) {

        if (row.transfermarktPlayerId() == null) {
            return;
        }

        String currentId =
                jdbcClient
                        .sql("""
                                SELECT transfermarkt_player_id
                                FROM player_identity_profiles
                                WHERE player_id = :playerId
                                """)
                        .param(
                                "playerId",
                                row.playerId()
                        )
                        .query(String.class)
                        .optional()
                        .orElse(null);

        if (
                currentId != null
                && !currentId.equals(
                        row.transfermarktPlayerId()
                )
        ) {
            throw new BadRequestException(
                    "Conflitto Transfermarkt ID "
                            + "per player "
                            + row.playerId()
            );
        }

        boolean usedElsewhere =
                jdbcClient
                        .sql("""
                                SELECT EXISTS (
                                    SELECT 1
                                    FROM player_identity_profiles
                                    WHERE transfermarkt_player_id =
                                          :externalId
                                      AND player_id <> :playerId
                                )
                                """)
                        .param(
                                "externalId",
                                row.transfermarktPlayerId()
                        )
                        .param(
                                "playerId",
                                row.playerId()
                        )
                        .query(Boolean.class)
                        .single();

        if (usedElsewhere) {
            throw new BadRequestException(
                    "Transfermarkt ID già collegato "
                            + "a un altro player"
            );
        }
    }

    private void rejectConflict(
            Map<String, Object> existing,
            String field,
            Object incoming,
            Long playerId
    ) {

        if (incoming == null) {
            return;
        }

        Object current = existing.get(field);

        if (current == null) {
            return;
        }

        if (
                !normalizedValue(current)
                        .equals(
                                normalizedValue(incoming)
                        )
        ) {
            throw new BadRequestException(
                    "Conflitto "
                            + field
                            + " per player "
                            + playerId
            );
        }
    }

    private String normalizedValue(
            Object value
    ) {

        if (value == null) {
            return "";
        }

        return Objects
                .toString(value)
                .trim();
    }

    private void upsert(
            Row row
    ) {

        jdbcClient
                .sql("""
                        INSERT INTO player_identity_profiles (
                            player_id,
                            canonical_full_name,
                            first_name,
                            last_name,
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
                            :canonicalName,
                            :firstName,
                            :lastName,
                            :birthDate,
                            :nationality,
                            :nationalityCode,
                            :heightCm,
                            :preferredFoot,
                            :transfermarktPlayerId,
                            :status,
                            :confidence
                        )

                        ON CONFLICT (player_id)
                        DO UPDATE SET

                            canonical_full_name =
                                COALESCE(
                                    player_identity_profiles.canonical_full_name,
                                    EXCLUDED.canonical_full_name
                                ),

                            first_name =
                                COALESCE(
                                    player_identity_profiles.first_name,
                                    EXCLUDED.first_name
                                ),

                            last_name =
                                COALESCE(
                                    player_identity_profiles.last_name,
                                    EXCLUDED.last_name
                                ),

                            birth_date =
                                COALESCE(
                                    player_identity_profiles.birth_date,
                                    EXCLUDED.birth_date
                                ),

                            nationality =
                                COALESCE(
                                    player_identity_profiles.nationality,
                                    EXCLUDED.nationality
                                ),

                            nationality_code =
                                COALESCE(
                                    player_identity_profiles.nationality_code,
                                    EXCLUDED.nationality_code
                                ),

                            height_cm =
                                COALESCE(
                                    player_identity_profiles.height_cm,
                                    EXCLUDED.height_cm
                                ),

                            preferred_foot =
                                COALESCE(
                                    player_identity_profiles.preferred_foot,
                                    EXCLUDED.preferred_foot
                                ),

                            transfermarkt_player_id =
                                COALESCE(
                                    player_identity_profiles.transfermarkt_player_id,
                                    EXCLUDED.transfermarkt_player_id
                                ),

                            identity_status =
                                CASE
                                    WHEN
                                        player_identity_profiles.identity_status
                                            = 'VERIFIED_COMPLETE'
                                        AND player_identity_profiles.confidence =
                                            'HIGH'
                                    THEN
                                        player_identity_profiles.identity_status

                                    WHEN
                                        player_identity_profiles.identity_status
                                            = 'VERIFIED_PARTIAL'
                                        AND player_identity_profiles.confidence =
                                            'HIGH'
                                        AND EXCLUDED.identity_status
                                            = 'VERIFIED_COMPLETE'
                                        AND EXCLUDED.confidence = 'HIGH'
                                    THEN
                                        EXCLUDED.identity_status

                                    WHEN
                                        player_identity_profiles.identity_status
                                            = 'VERIFIED_PARTIAL'
                                        AND player_identity_profiles.confidence =
                                            'HIGH'
                                    THEN
                                        player_identity_profiles.identity_status

                                    WHEN
                                        EXCLUDED.identity_status
                                            IN (
                                                'VERIFIED_COMPLETE',
                                                'VERIFIED_PARTIAL'
                                            )
                                        AND EXCLUDED.confidence = 'HIGH'
                                    THEN
                                        EXCLUDED.identity_status

                                    ELSE
                                        EXCLUDED.identity_status
                                END,

                            confidence =
                                CASE
                                    WHEN
                                        player_identity_profiles.identity_status
                                            IN (
                                                'VERIFIED_COMPLETE',
                                                'VERIFIED_PARTIAL'
                                            )
                                        AND player_identity_profiles.confidence =
                                            'HIGH'
                                    THEN
                                        player_identity_profiles.confidence

                                    WHEN
                                        EXCLUDED.identity_status
                                            IN (
                                                'VERIFIED_COMPLETE',
                                                'VERIFIED_PARTIAL'
                                            )
                                        AND EXCLUDED.confidence = 'HIGH'
                                    THEN
                                        EXCLUDED.confidence

                                    ELSE
                                        EXCLUDED.confidence
                                END,

                            updated_at =
                                CURRENT_TIMESTAMP
                        """)
                .param(
                        "playerId",
                        row.playerId()
                )
                .param(
                        "canonicalName",
                        row.canonicalName(),
                        Types.VARCHAR
                )
                .param(
                        "firstName",
                        row.firstName(),
                        Types.VARCHAR
                )
                .param(
                        "lastName",
                        row.lastName(),
                        Types.VARCHAR
                )
                .param(
                        "birthDate",
                        row.birthDate(),
                        Types.DATE
                )
                .param(
                        "nationality",
                        row.nationality(),
                        Types.VARCHAR
                )
                .param(
                        "nationalityCode",
                        row.nationalityCode(),
                        Types.CHAR
                )
                .param(
                        "heightCm",
                        row.heightCm(),
                        Types.SMALLINT
                )
                .param(
                        "preferredFoot",
                        row.preferredFoot(),
                        Types.VARCHAR
                )
                .param(
                        "transfermarktPlayerId",
                        row.transfermarktPlayerId(),
                        Types.VARCHAR
                )
                .param(
                        "status",
                        row.status()
                )
                .param(
                        "confidence",
                        row.confidence()
                )
                .update();

        if (isVerified(row)) {

            jdbcClient
                    .sql("""
                            UPDATE players
                            SET
                                first_name =
                                    COALESCE(
                                        first_name,
                                        :firstName
                                    ),
                                birth_date =
                                    COALESCE(
                                        birth_date,
                                        :birthDate
                                    ),
                                nationality_code =
                                    COALESCE(
                                        nationality_code,
                                        :nationalityCode
                                    ),
                                height_cm =
                                    COALESCE(
                                        height_cm,
                                        :heightCm
                                    ),
                                preferred_foot =
                                    COALESCE(
                                        preferred_foot,
                                        :preferredFoot
                                    ),
                                updated_at =
                                    CURRENT_TIMESTAMP
                            WHERE id = :playerId
                            """)
                    .param(
                            "firstName",
                            row.firstName(),
                            Types.VARCHAR
                    )
                    .param(
                            "birthDate",
                            row.birthDate(),
                            Types.DATE
                    )
                    .param(
                            "nationalityCode",
                            row.nationalityCode(),
                            Types.CHAR
                    )
                    .param(
                            "heightCm",
                            row.heightCm(),
                            Types.SMALLINT
                    )
                    .param(
                            "preferredFoot",
                            row.preferredFoot(),
                            Types.VARCHAR
                    )
                    .param(
                            "playerId",
                            row.playerId()
                    )
                    .update();

            savePopulatedVerifiedProvenance(row);
        }

        if (
                row.sourceExternalId() != null
                || row.sourceUrl() != null
        ) {
            saveProvenance(
                    row,
                    "IDENTITY_STATUS"
            );
        }
    }

    private void savePopulatedVerifiedProvenance(
            Row row
    ) {

        if (row.canonicalName() != null) {
            saveProvenance(
                    row,
                    "CANONICAL_FULL_NAME"
            );
        }

        if (row.firstName() != null) {
            saveProvenance(
                    row,
                    "FIRST_NAME"
            );
        }

        if (row.lastName() != null) {
            saveProvenance(
                    row,
                    "LAST_NAME"
            );
        }

        if (row.birthDate() != null) {
            saveProvenance(
                    row,
                    "BIRTH_DATE"
            );
        }

        if (row.nationality() != null) {
            saveProvenance(
                    row,
                    "NATIONALITY"
            );
        }

        if (row.nationalityCode() != null) {
            saveProvenance(
                    row,
                    "NATIONALITY_CODE"
            );
        }

        if (row.heightCm() != null) {
            saveProvenance(
                    row,
                    "HEIGHT_CM"
            );
        }

        if (row.preferredFoot() != null) {
            saveProvenance(
                    row,
                    "PREFERRED_FOOT"
            );
        }

        if (row.transfermarktPlayerId() != null) {
            saveProvenance(
                    row,
                    "TRANSFERMARKT_PLAYER_ID"
            );
        }
    }

    private void saveProvenance(
            Row row,
            String field
    ) {

        jdbcClient
                .sql("""
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
                            :field,
                            :source,
                            :externalId,
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
                            source_url =
                                EXCLUDED.source_url,
                            confidence =
                                EXCLUDED.confidence,
                            evidence =
                                EXCLUDED.evidence,
                            imported_at =
                                CURRENT_TIMESTAMP
                        """)
                .param(
                        "playerId",
                        row.playerId()
                )
                .param(
                        "field",
                        field
                )
                .param(
                        "source",
                        row.source()
                )
                .param(
                        "externalId",
                        row.sourceExternalId(),
                        Types.VARCHAR
                )
                .param(
                        "sourceUrl",
                        row.sourceUrl(),
                        Types.VARCHAR
                )
                .param(
                        "confidence",
                        "NONE".equals(
                                row.confidence()
                        )
                                ? "LOW"
                                : row.confidence()
                )
                .param(
                        "evidence",
                        row.evidence() == null
                                ? "{}"
                                : row.evidence()
                )
                .update();
    }

    private boolean isVerified(
            Row row
    ) {
        return row.status()
                .startsWith("VERIFIED_");
    }

    private String value(
            CSVRecord record,
            String field
    ) {

        String value =
                record.get(field);

        return value == null
                || value.isBlank()
                        ? null
                        : value.trim();
    }

    private String upper(
            String value
    ) {
        return value == null
                ? null
                : value.toUpperCase();
    }

    private LocalDate date(
            String value
    ) {
        return value == null
                ? null
                : LocalDate.parse(value);
    }

    private Integer integer(
            String value
    ) {
        return value == null
                ? null
                : Integer.valueOf(value);
    }

    private record Row(
            Long playerId,
            String canonicalName,
            String firstName,
            String lastName,
            LocalDate birthDate,
            String nationality,
            String nationalityCode,
            Integer heightCm,
            String preferredFoot,
            String status,
            String confidence,
            String source,
            String transfermarktPlayerId,
            String sourceExternalId,
            String sourceUrl,
            String evidence
    ) {
    }
}
