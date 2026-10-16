package it.fantacalciointelligence.backend.identity.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.common.BadRequestException;

@SpringBootTest
@Transactional
class IdentityEnrichmentImportServiceTest {

    @Autowired
    IdentityEnrichmentImportService service;

    @Autowired
    IdentityEnrichmentReadinessService readinessService;

    @Autowired
    JdbcClient jdbcClient;

    @Test
    void verifiedIdentityIsIdempotentPreservesNullsAndDoesNotTouchReconciliation() {

        long playerId =
                player(
                        "Identity Enrichment Test"
                );

        int mappingsBefore =
                count(
                        "player_source_mappings"
                );

        int reconciliationBefore =
                count(
                        "player_reconciliation_results"
                );

        var first =
                service.importCsv(
                        wikidataCsv(
                                playerId,
                                "Ada Example",
                                "Ada",
                                "Example",
                                "1994-04-03",
                                "",
                                "",
                                "",
                                "VERIFIED_PARTIAL",
                                "HIGH",
                                "Q460001"
                        )
                );

        var second =
                service.importCsv(
                        wikidataCsv(
                                playerId,
                                "Ada Example",
                                "Ada",
                                "Example",
                                "1994-04-03",
                                "",
                                "",
                                "",
                                "VERIFIED_PARTIAL",
                                "HIGH",
                                "Q460001"
                        )
                );

        assertThat(
                first.importedRows()
        ).isOne();

        assertThat(
                second.importedRows()
        ).isOne();

        assertThat(
                jdbcClient
                        .sql("""
                                SELECT COUNT(*)
                                FROM player_identity_profiles
                                WHERE player_id = :id
                                """)
                        .param(
                                "id",
                                playerId
                        )
                        .query(Integer.class)
                        .single()
        ).isOne();

        assertThat(
                jdbcClient
                        .sql("""
                                SELECT COUNT(*)
                                FROM player_identity_provenance
                                WHERE player_id = :id
                                """)
                        .param(
                                "id",
                                playerId
                        )
                        .query(Integer.class)
                        .single()
        ).isEqualTo(5);

        var player =
                jdbcClient
                        .sql("""
                                SELECT
                                    first_name,
                                    birth_date,
                                    nationality_code,
                                    height_cm,
                                    preferred_foot
                                FROM players
                                WHERE id = :id
                                """)
                        .param(
                                "id",
                                playerId
                        )
                        .query()
                        .singleRow();

        assertThat(
                player.get("first_name")
        ).isEqualTo("Ada");

        assertThat(
                player.get("birth_date")
                        .toString()
        ).isEqualTo("1994-04-03");

        assertThat(
                player.get(
                        "nationality_code"
                )
        ).isNull();

        assertThat(
                player.get("height_cm")
        ).isNull();

        assertThat(
                player.get(
                        "preferred_foot"
                )
        ).isNull();

        assertThat(
                count(
                        "player_source_mappings"
                )
        ).isEqualTo(
                mappingsBefore
        );

        assertThat(
                count(
                        "player_reconciliation_results"
                )
        ).isEqualTo(
                reconciliationBefore
        );
    }

    @Test
    void conflictingBirthDateIsRejectedAtomically() {

        long playerId =
                player(
                        "Identity Conflict Test"
                );

        jdbcClient
                .sql("""
                        UPDATE players
                        SET birth_date =
                            DATE '1990-01-01'
                        WHERE id = :id
                        """)
                .param(
                        "id",
                        playerId
                )
                .update();

        assertThatThrownBy(() ->
                service.importCsv(
                        wikidataCsv(
                                playerId,
                                "Mario Example",
                                "Mario",
                                "Example",
                                "1991-01-01",
                                "IT",
                                "180",
                                "RIGHT",
                                "VERIFIED_COMPLETE",
                                "HIGH",
                                "Q460002"
                        )
                )
        )
                .isInstanceOf(
                        BadRequestException.class
                )
                .hasMessageContaining(
                        "Conflitto birth_date"
                );

        assertThat(
                jdbcClient
                        .sql("""
                                SELECT COUNT(*)
                                FROM player_identity_profiles
                                WHERE player_id = :id
                                """)
                        .param(
                                "id",
                                playerId
                        )
                        .query(Integer.class)
                        .single()
        ).isZero();
    }

    @Test
    void conflictingHeightIsRejectedAtomically() {

        long playerId =
                player(
                        "Identity Height Conflict"
                );

        jdbcClient
                .sql("""
                        UPDATE players
                        SET height_cm = 180
                        WHERE id = :id
                        """)
                .param(
                        "id",
                        playerId
                )
                .update();

        assertThatThrownBy(() ->
                service.importCsv(
                        wikidataCsv(
                                playerId,
                                "Height Example",
                                "",
                                "Example",
                                "1995-02-03",
                                "IT",
                                "181",
                                "",
                                "VERIFIED_PARTIAL",
                                "HIGH",
                                "Q460003"
                        )
                )
        )
                .isInstanceOf(
                        BadRequestException.class
                )
                .hasMessageContaining(
                        "Conflitto height_cm"
                );

        assertThat(
                jdbcClient
                        .sql("""
                                SELECT COUNT(*)
                                FROM player_identity_profiles
                                WHERE player_id = :id
                                """)
                        .param(
                                "id",
                                playerId
                        )
                        .query(Integer.class)
                        .single()
        ).isZero();
    }

    @Test
    void conflictingPreferredFootIsRejectedAtomically() {

        long playerId =
                player(
                        "Identity Foot Conflict"
                );

        jdbcClient
                .sql("""
                        UPDATE players
                        SET preferred_foot = 'RIGHT'
                        WHERE id = :id
                        """)
                .param(
                        "id",
                        playerId
                )
                .update();

        assertThatThrownBy(() ->
                service.importCsv(
                        wikidataCsv(
                                playerId,
                                "Foot Example",
                                "",
                                "Example",
                                "1995-02-03",
                                "IT",
                                "",
                                "LEFT",
                                "VERIFIED_PARTIAL",
                                "HIGH",
                                "Q460004"
                        )
                )
        )
                .isInstanceOf(
                        BadRequestException.class
                )
                .hasMessageContaining(
                        "Conflitto preferred_foot"
                );

        assertThat(
                jdbcClient
                        .sql("""
                                SELECT COUNT(*)
                                FROM player_identity_profiles
                                WHERE player_id = :id
                                """)
                        .param(
                                "id",
                                playerId
                        )
                        .query(Integer.class)
                        .single()
        ).isZero();
    }

    @Test
    void transfermarktStableIdentityFillsNullsAndKeepsFootAndReconciliationUntouched() {

        long playerId =
                player(
                        "Stable Transfermarkt Test"
                );

        int mappingsBefore =
                count(
                        "player_source_mappings"
                );

        int reconciliationBefore =
                count(
                        "player_reconciliation_results"
                );

        String content =
                header()
                        + row(
                                playerId,
                                "Mario Example",
                                "Mario",
                                "Example",
                                "1992-03-04",
                                "Italia",
                                "IT",
                                "181",
                                "",
                                "123456",
                                "VERIFIED_COMPLETE",
                                "HIGH",
                                "TRANSFERMARKT",
                                "123456",
                                "https://www.transfermarkt.it/x/profil/spieler/123456"
                        );

        service.importCsv(
                file(content)
        );

        service.importCsv(
                file(content)
        );

        var profile =
                jdbcClient
                        .sql("""
                                SELECT
                                    nationality,
                                    nationality_code,
                                    height_cm,
                                    preferred_foot,
                                    transfermarkt_player_id
                                FROM player_identity_profiles
                                WHERE player_id = :id
                                """)
                        .param(
                                "id",
                                playerId
                        )
                        .query()
                        .singleRow();

        assertThat(profile)
                .containsEntry(
                        "nationality",
                        "Italia"
                )
                .containsEntry(
                        "nationality_code",
                        "IT"
                )
                .containsEntry(
                        "transfermarkt_player_id",
                        "123456"
                );

        assertThat(
                ((Number) profile.get(
                        "height_cm"
                )).intValue()
        ).isEqualTo(181);

        assertThat(
                profile.get(
                        "preferred_foot"
                )
        ).isNull();

        assertThat(
                count(
                        "player_source_mappings"
                )
        ).isEqualTo(
                mappingsBefore
        );

        assertThat(
                count(
                        "player_reconciliation_results"
                )
        ).isEqualTo(
                reconciliationBefore
        );
    }

    @Test
    void transfermarktIdCannotMoveBetweenPlayersOrChangeForSamePlayer() {

        long first =
                player(
                        "First Stable ID Test"
                );

        long second =
                player(
                        "Second Stable ID Test"
                );

        service.importCsv(
                transfermarktCsv(
                        first,
                        "123457"
                )
        );

        assertThatThrownBy(() ->
                service.importCsv(
                        transfermarktCsv(
                                first,
                                "765432"
                        )
                )
        )
                .isInstanceOf(
                        BadRequestException.class
                )
                .hasMessageContaining(
                        "Conflitto Transfermarkt ID"
                );

        assertThatThrownBy(() ->
                service.importCsv(
                        transfermarktCsv(
                                second,
                                "123457"
                        )
                )
        )
                .isInstanceOf(
                        BadRequestException.class
                )
                .hasMessageContaining(
                        "altro player"
                );
    }

    @Test
    void allV46ProvenanceSourcesAreAccepted() {

        String[] sources = {
                "WIKIDATA",
                "FANTACALCIO",
                "TRANSFERMARKT",
                "UEFA",
                "FIGC",
                "LEGA_SERIE_A",
                "OFFICIAL_CLUB",
                "MANUAL_VERIFIED"
        };

        for (int index = 0;
                index < sources.length;
                index++) {

            String source =
                    sources[index];

            long playerId =
                    player(
                            "Approved Source "
                                    + index
                    );

            String transfermarktId =
                    "TRANSFERMARKT".equals(
                            source
                    )
                            ? Integer.toString(
                                    900000 + index
                            )
                            : "";

            String externalId =
                    source + "-"
                            + playerId;

            String content =
                    header()
                            + row(
                                    playerId,
                                    "Approved Source Player "
                                            + index,
                                    "",
                                    "Player",
                                    "1990-01-01",
                                    "",
                                    "",
                                    "",
                                    "",
                                    transfermarktId,
                                    "VERIFIED_PARTIAL",
                                    "HIGH",
                                    source,
                                    externalId,
                                    "https://source.example/"
                                            + source
                            );

            var response =
                    service.importCsv(
                            file(content)
                    );

            assertThat(
                    response.importedRows()
            ).isOne();

            assertThat(
                    jdbcClient
                            .sql("""
                                    SELECT COUNT(*)
                                    FROM player_identity_provenance
                                    WHERE player_id = :playerId
                                      AND source = :source
                                    """)
                            .param(
                                    "playerId",
                                    playerId
                            )
                            .param(
                                    "source",
                                    source
                            )
                            .query(Integer.class)
                            .single()
            ).isPositive();
        }
    }

    @Test
    void verifiedOfficialSourceMayUseUrlWithoutExternalId() {

        long playerId =
                player(
                        "Official URL Source"
                );

        String content =
                header()
                        + row(
                                playerId,
                                "Official URL Player",
                                "",
                                "Player",
                                "1993-05-10",
                                "",
                                "",
                                "",
                                "",
                                "",
                                "VERIFIED_PARTIAL",
                                "HIGH",
                                "UEFA",
                                "",
                                "https://uefa.example/player"
                        );

        var response =
                service.importCsv(
                            file(content)
                    );

        service.importCsv(
                file(content)
        );

        assertThat(
                response.verifiedPartial()
        ).isOne();

        assertThat(
                jdbcClient
                        .sql("""
                                SELECT COUNT(*)
                                FROM player_identity_provenance
                                WHERE player_id = :playerId
                                  AND source = 'UEFA'
                                  AND source_external_id IS NULL
                                  AND field_name = 'CANONICAL_FULL_NAME'
                                """)
                        .param(
                                "playerId",
                                playerId
                        )
                        .query(Integer.class)
                        .single()
        ).isOne();
    }

    @Test
    void verifiedPartialMayFillNullsAndPromoteToVerifiedComplete() {

        long playerId = player("Verified Partial Promotion");

        service.importCsv(file(header() + row(
                playerId,
                "Promoted Player",
                "",
                "Player",
                "1994-06-07",
                "",
                "",
                "",
                "",
                "",
                "VERIFIED_PARTIAL",
                "HIGH",
                "FANTACALCIO",
                "FANTACALCIO-PROMOTION",
                "https://fantacalcio.example/promotion"
        )));

        service.importCsv(file(header() + row(
                playerId,
                "Promoted Player",
                "Mario",
                "Player",
                "1994-06-07",
                "Italia",
                "IT",
                "182",
                "RIGHT",
                "998877",
                "VERIFIED_COMPLETE",
                "HIGH",
                "TRANSFERMARKT",
                "998877",
                "https://transfermarkt.example/998877"
        )));

        var profile = jdbcClient.sql("""
                SELECT first_name, nationality, nationality_code, height_cm,
                       preferred_foot, transfermarkt_player_id, identity_status, confidence
                FROM player_identity_profiles
                WHERE player_id = :id
                """)
                .param("id", playerId)
                .query()
                .singleRow();

        assertThat(profile)
                .containsEntry("first_name", "Mario")
                .containsEntry("nationality", "Italia")
                .containsEntry("nationality_code", "IT")
                .containsEntry("preferred_foot", "RIGHT")
                .containsEntry("transfermarkt_player_id", "998877")
                .containsEntry("identity_status", "VERIFIED_COMPLETE")
                .containsEntry("confidence", "HIGH");
        assertThat(profile.get("height_cm")).isEqualTo(182);
    }

    @Test
    void weakerLaterRowCannotEraseOrDowngradeVerifiedProfile() {

        long playerId =
                player(
                        "Verified Profile Preservation"
                );

        service.importCsv(
                file(
                        header()
                                + row(
                                        playerId,
                                        "Verified Player",
                                        "Mario",
                                        "Player",
                                        "1992-03-04",
                                        "Italia",
                                        "IT",
                                        "181",
                                        "RIGHT",
                                        "987654",
                                        "VERIFIED_COMPLETE",
                                        "HIGH",
                                        "TRANSFERMARKT",
                                        "987654",
                                        "https://transfermarkt.example/987654"
                                )
                )
        );

        service.importCsv(
                file(
                        header()
                                + row(
                                        playerId,
                                        "",
                                        "",
                                        "",
                                        "",
                                        "",
                                        "",
                                        "",
                                        "",
                                        "",
                                        "DATA_SOURCE_MISSING",
                                        "NONE",
                                        "FANTACALCIO",
                                        "FANTACALCIO-TEST",
                                        "https://fantacalcio.example/test"
                                )
                )
        );

        var profile =
                jdbcClient
                        .sql("""
                                SELECT
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
                                FROM player_identity_profiles
                                WHERE player_id = :id
                                """)
                        .param(
                                "id",
                                playerId
                        )
                        .query()
                        .singleRow();

        assertThat(profile)
                .containsEntry(
                        "canonical_full_name",
                        "Verified Player"
                )
                .containsEntry(
                        "first_name",
                        "Mario"
                )
                .containsEntry(
                        "last_name",
                        "Player"
                )
                .containsEntry(
                        "nationality",
                        "Italia"
                )
                .containsEntry(
                        "nationality_code",
                        "IT"
                )
                .containsEntry(
                        "preferred_foot",
                        "RIGHT"
                )
                .containsEntry(
                        "transfermarkt_player_id",
                        "987654"
                )
                .containsEntry(
                        "identity_status",
                        "VERIFIED_COMPLETE"
                )
                .containsEntry(
                        "confidence",
                        "HIGH"
                );

        assertThat(
                profile.get(
                        "birth_date"
                ).toString()
        ).isEqualTo(
                "1992-03-04"
        );

        assertThat(
                ((Number) profile.get(
                        "height_cm"
                )).intValue()
        ).isEqualTo(181);
    }

    @Test
    void missingSourceIsReportedBySeparateNonCatalogGate() {

        long playerId =
                player(
                        "Identity Missing Test"
                );

        long seasonId =
                jdbcClient
                        .sql("""
                                INSERT INTO seasons (
                                    code,
                                    start_year,
                                    end_year,
                                    current_season
                                )
                                VALUES (
                                    '2110/2111',
                                    2110,
                                    2111,
                                    FALSE
                                )
                                RETURNING id
                                """)
                        .query(Long.class)
                        .single();

        long clubId =
                jdbcClient
                        .sql("""
                                INSERT INTO clubs (
                                    name,
                                    short_name,
                                    slug,
                                    country_code,
                                    active
                                )
                                VALUES (
                                    'Identity Club',
                                    'Identity Club',
                                    'identity-club-2110',
                                    'IT',
                                    TRUE
                                )
                                RETURNING id
                                """)
                        .query(Long.class)
                        .single();

        jdbcClient
                .sql("""
                        INSERT INTO player_registrations (
                            player_id,
                            club_id,
                            season_id,
                            registration_status
                        )
                        VALUES (
                            :playerId,
                            :clubId,
                            :seasonId,
                            'ACTIVE'
                        )
                        """)
                .param(
                        "playerId",
                        playerId
                )
                .param(
                        "clubId",
                        clubId
                )
                .param(
                        "seasonId",
                        seasonId
                )
                .update();

        jdbcClient
                .sql("""
                        INSERT INTO player_reconciliation_results (
                            season_id,
                            player_id,
                            normalized_name,
                            normalized_club_name,
                            sources_count,
                            identity_resolution_method,
                            club_resolution_source
                        )
                        VALUES (
                            :seasonId,
                            :playerId,
                            'identity missing test',
                            'identity club',
                            2,
                            'EXTERNAL_ID',
                            'FANTACALCIO'
                        )
                        """)
                .param(
                        "seasonId",
                        seasonId
                )
                .param(
                        "playerId",
                        playerId
                )
                .update();

        service.importCsv(
                wikidataCsv(
                        playerId,
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "DATA_SOURCE_MISSING",
                        "NONE",
                        ""
                )
        );

        IdentityEnrichmentReadinessResponse response =
                readinessService.evaluate(
                        "2110/2111"
                );

        assertThat(
                response.status()
        ).isEqualTo(
                "BLOCKED_DATA_SOURCE"
        );

        assertThat(
                response.dataSourceMissing()
        ).isPositive();
    }

    private long player(
            String name
    ) {

        return jdbcClient
                .sql("""
                        INSERT INTO players (
                            last_name,
                            display_name,
                            normalized_name,
                            active
                        )
                        VALUES (
                            :name,
                            :name,
                            :normalized,
                            TRUE
                        )
                        RETURNING id
                        """)
                .param(
                        "name",
                        name
                )
                .param(
                        "normalized",
                        name.toLowerCase()
                )
                .query(Long.class)
                .single();
    }

    private int count(
            String table
    ) {
        return jdbcClient
                .sql(
                        "SELECT COUNT(*) FROM "
                                + table
                )
                .query(Integer.class)
                .single();
    }

    private MockMultipartFile wikidataCsv(
            long id,
            String canonical,
            String first,
            String last,
            String birth,
            String nationality,
            String height,
            String foot,
            String status,
            String confidence,
            String qid
    ) {

        String sourceUrl =
                qid.isBlank()
                        ? ""
                        : "https://www.wikidata.org/wiki/"
                                + qid;

        return file(
                header()
                        + row(
                                id,
                                canonical,
                                first,
                                last,
                                birth,
                                nationality,
                                nationality,
                                height,
                                foot,
                                "",
                                status,
                                confidence,
                                "WIKIDATA",
                                qid,
                                sourceUrl
                        )
        );
    }

    private MockMultipartFile transfermarktCsv(
            long playerId,
            String externalId
    ) {

        return file(
                header()
                        + row(
                                playerId,
                                "Mario Example",
                                "Mario",
                                "Example",
                                "1992-03-04",
                                "Italia",
                                "IT",
                                "181",
                                "",
                                externalId,
                                "VERIFIED_COMPLETE",
                                "HIGH",
                                "TRANSFERMARKT",
                                externalId,
                                "https://www.transfermarkt.it/x/profil/spieler/"
                                        + externalId
                        )
        );
    }

    private String header() {
        return """
                player_id,canonical_full_name,first_name,last_name,birth_date,nationality,nationality_code,height_cm,preferred_foot,transfermarkt_player_id,identity_status,confidence,source,source_external_id,source_url,evidence
                """;
    }

    private String row(
            long playerId,
            String canonical,
            String first,
            String last,
            String birth,
            String nationality,
            String nationalityCode,
            String height,
            String foot,
            String transfermarktId,
            String status,
            String confidence,
            String source,
            String sourceExternalId,
            String sourceUrl
    ) {

        return String.join(
                ",",
                Long.toString(
                        playerId
                ),
                canonical,
                first,
                last,
                birth,
                nationality,
                nationalityCode,
                height,
                foot,
                transfermarktId,
                status,
                confidence,
                source,
                sourceExternalId,
                sourceUrl,
                "\"{}\""
        ) + "\n";
    }

    private MockMultipartFile file(
            String content
    ) {
        return new MockMultipartFile(
                "file",
                "identity.csv",
                "text/csv",
                content.getBytes(
                        StandardCharsets.UTF_8
                )
        );
    }
}
