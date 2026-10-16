package it.fantacalciointelligence.backend.identity.enrichment.transfermarkt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(
        named = "B65_TRANSFERMARKT_PERSISTENCE_TEST",
        matches = "true"
)
class TransfermarktProvenancePersistenceServiceTest {

    private static final String TEST_TM_ID = "999999999";

    @Autowired
    private TransfermarktProvenancePersistenceService service;

    @Autowired
    private JdbcClient jdbc;

    private Long existingPlayerId;

    @BeforeEach
    void setUp() {

        existingPlayerId =
                jdbc.sql("""
                        SELECT id
                        FROM players
                        ORDER BY id
                        LIMIT 1
                        """)
                        .query(Long.class)
                        .single();

        jdbc.sql("""
                DELETE FROM player_identity_provenance
                WHERE player_id = :playerId
                  AND source = 'TRANSFERMARKT'
                  AND source_external_id = :externalId
                """)
                .param("playerId", existingPlayerId)
                .param("externalId", TEST_TM_ID)
                .update();
    }

    @Test
    void shouldPersistOnlyMatchedVerifiedPopulatedFields() {

        TransfermarktDiscoveryResponse.PlayerDiscovery verified =
                new TransfermarktDiscoveryResponse.PlayerDiscovery(
                        existingPlayerId,
                        "B65 Test Player",
                        "Roma",
                        "MATCHED_VERIFIED",
                        100,
                        TEST_TM_ID,
                        "B65 Test Player",
                        "https://www.transfermarkt.com/b65-test/profil/spieler/"
                                + TEST_TM_ID,
                        "Roma",
                        LocalDate.of(2000, 1, 2),
                        "Italy",
                        185,
                        "RIGHT",
                        "B65 verified test"
                );

        TransfermarktDiscoveryResponse.PlayerDiscovery notFound =
                new TransfermarktDiscoveryResponse.PlayerDiscovery(
                        existingPlayerId,
                        "Ignored Player",
                        "Roma",
                        "NOT_FOUND",
                        100,
                        "888888888",
                        "Ignored Player",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "Must not be persisted"
                );

        TransfermarktDiscoveryResponse response =
                new TransfermarktDiscoveryResponse(
                        "2026/2027",
                        "Roma",
                        2,
                        1,
                        0,
                        1,
                        0,
                        false,
                        List.of(
                                verified,
                                notFound
                        )
                );

        int persistedFields =
                service.persist(
                        "2026/2027",
                        response
                );

        assertThat(persistedFields)
                .isEqualTo(6);

        List<Map<String, Object>> rows =
                jdbc.sql("""
                        SELECT
                            field_name,
                            source,
                            source_external_id,
                            evidence
                        FROM player_identity_provenance
                        WHERE player_id = :playerId
                          AND source = 'TRANSFERMARKT'
                          AND source_external_id = :externalId
                        ORDER BY field_name
                        """)
                        .param("playerId", existingPlayerId)
                        .param("externalId", TEST_TM_ID)
                        .query()
                        .listOfRows();

        assertThat(rows)
                .hasSize(6);

        assertThat(rows)
                .allSatisfy(row -> {
                    assertThat(row.get("source"))
                            .isEqualTo("TRANSFERMARKT");

                    assertThat(row.get("source_external_id"))
                            .isEqualTo(TEST_TM_ID);
                });

        assertThat(rows)
                .extracting(row -> row.get("field_name"))
                .containsExactlyInAnyOrder(
                        "CANONICAL_FULL_NAME",
                        "BIRTH_DATE",
                        "NATIONALITY",
                        "HEIGHT_CM",
                        "PREFERRED_FOOT",
                        "TRANSFERMARKT_PLAYER_ID"
                );

        Long ignoredRows =
                jdbc.sql("""
                        SELECT COUNT(*)
                        FROM player_identity_provenance
                        WHERE source = 'TRANSFERMARKT'
                          AND source_external_id = '888888888'
                        """)
                        .query(Long.class)
                        .single();

        assertThat(ignoredRows)
                .isZero();
    }

    @Test
    void shouldIgnoreNullFields() {

        TransfermarktDiscoveryResponse.PlayerDiscovery verified =
                new TransfermarktDiscoveryResponse.PlayerDiscovery(
                        existingPlayerId,
                        "B65 Test Player",
                        "Roma",
                        "MATCHED_VERIFIED",
                        100,
                        TEST_TM_ID,
                        "B65 Test Player",
                        "https://www.transfermarkt.com/b65-test/profil/spieler/"
                                + TEST_TM_ID,
                        "Roma",
                        LocalDate.of(2000, 1, 2),
                        "Italy",
                        null,
                        null,
                        "B65 null fields test"
                );

        TransfermarktDiscoveryResponse response =
                new TransfermarktDiscoveryResponse(
                        "2026/2027",
                        "Roma",
                        1,
                        1,
                        0,
                        0,
                        0,
                        false,
                        List.of(verified)
                );

        int persistedFields =
                service.persist(
                        "2026/2027",
                        response
                );

        assertThat(persistedFields)
                .isEqualTo(4);

        List<String> fields =
                jdbc.sql("""
                        SELECT field_name
                        FROM player_identity_provenance
                        WHERE player_id = :playerId
                          AND source = 'TRANSFERMARKT'
                          AND source_external_id = :externalId
                        """)
                        .param("playerId", existingPlayerId)
                        .param("externalId", TEST_TM_ID)
                        .query(String.class)
                        .list();

        assertThat(fields)
                .containsExactlyInAnyOrder(
                        "CANONICAL_FULL_NAME",
                        "BIRTH_DATE",
                        "NATIONALITY",
                        "TRANSFERMARKT_PLAYER_ID"
                );

        assertThat(fields)
                .doesNotContain(
                        "HEIGHT_CM",
                        "PREFERRED_FOOT"
                );
    }

    @Test
    void shouldBeIdempotentAndRefreshEvidence() {

        TransfermarktDiscoveryResponse.PlayerDiscovery first =
                playerDiscovery(
                        90,
                        "First verification"
                );

        TransfermarktDiscoveryResponse firstResponse =
                response(first);

        int firstPersist =
                service.persist(
                        "2026/2027",
                        firstResponse
                );

        TransfermarktDiscoveryResponse.PlayerDiscovery second =
                playerDiscovery(
                        100,
                        "Second verification"
                );

        TransfermarktDiscoveryResponse secondResponse =
                response(second);

        int secondPersist =
                service.persist(
                        "2026/2027",
                        secondResponse
                );

        assertThat(firstPersist)
                .isEqualTo(6);

        assertThat(secondPersist)
                .isEqualTo(6);

        Long count =
                jdbc.sql("""
                        SELECT COUNT(*)
                        FROM player_identity_provenance
                        WHERE player_id = :playerId
                          AND source = 'TRANSFERMARKT'
                          AND source_external_id = :externalId
                        """)
                        .param("playerId", existingPlayerId)
                        .param("externalId", TEST_TM_ID)
                        .query(Long.class)
                        .single();

        assertThat(count)
                .isEqualTo(6L);

        String evidence =
                jdbc.sql("""
                        SELECT evidence::text
                        FROM player_identity_provenance
                        WHERE player_id = :playerId
                          AND field_name = 'PREFERRED_FOOT'
                          AND source = 'TRANSFERMARKT'
                          AND source_external_id = :externalId
                        """)
                        .param("playerId", existingPlayerId)
                        .param("externalId", TEST_TM_ID)
                        .query(String.class)
                        .single();

        assertThat(evidence)
                .contains("Second verification")
                .contains("\"score\": 100");
    }

    private TransfermarktDiscoveryResponse.PlayerDiscovery playerDiscovery(
            int score,
            String reason
    ) {
        return new TransfermarktDiscoveryResponse.PlayerDiscovery(
                existingPlayerId,
                "B65 Test Player",
                "Roma",
                "MATCHED_VERIFIED",
                score,
                TEST_TM_ID,
                "B65 Test Player",
                "https://www.transfermarkt.com/b65-test/profil/spieler/"
                        + TEST_TM_ID,
                "Roma",
                LocalDate.of(2000, 1, 2),
                "Italy",
                185,
                "RIGHT",
                reason
        );
    }

    private TransfermarktDiscoveryResponse response(
            TransfermarktDiscoveryResponse.PlayerDiscovery player
    ) {
        return new TransfermarktDiscoveryResponse(
                "2026/2027",
                "Roma",
                1,
                1,
                0,
                0,
                0,
                false,
                List.of(player)
        );
    }
}