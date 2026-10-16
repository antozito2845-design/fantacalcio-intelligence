package it.fantacalciointelligence.backend.reconciliation.identity;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class PlayerIdentityResolutionServiceTest {

    @Autowired
    private PlayerIdentityResolutionService service;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void shouldResolveIdentityIndependentlyFromCurrentClub() {

        long fantacalcioBatch =
                createBatch("FANTACALCIO");

        long gazzettaBatch =
                createBatch("GAZZETTA");

        long fantamarktBatch =
                createBatch("FANTAMARKT");

        insertRow(
                fantacalcioBatch,
                1,
                "FC-TRANSFER-001",
                "Mario Rossi",
                "Roma"
        );

        insertRow(
                gazzettaBatch,
                1,
                "GA-TRANSFER-001",
                "Mario Rossi",
                "Bologna"
        );

        insertRow(
                fantamarktBatch,
                1,
                "FM-TRANSFER-001",
                "Mario Rossi",
                "Bologna"
        );

        List<ResolvedPlayerIdentity> result =
                service.resolve(
                        fantacalcioBatch,
                        gazzettaBatch,
                        fantamarktBatch
                );

        assertThat(result)
                .hasSize(1);

        ResolvedPlayerIdentity identity =
                result.getFirst();

        assertThat(identity.sourcesCount())
                .isEqualTo(3);

        assertThat(
                identity.identityResolutionMethod()
        )
                .isEqualTo(
                        "EXACT_NAME_UNIQUE"
                );

        assertThat(
                identity.fantacalcioClub()
        )
                .isEqualTo("Roma");

        assertThat(
                identity.gazzettaClub()
        )
                .isEqualTo("Bologna");

        assertThat(
                identity.fantamarktClub()
        )
                .isEqualTo("Bologna");
    }

    @Test
    void shouldPreferHistoricalMappingsEvenWhenNamesAndClubsChange() {

        Long playerId =
                createPlayer(
                        "Giocatore Identità Storica"
                );

        createMapping(
                playerId,
                "FANTACALCIO",
                "FC-HISTORY-001",
                "Nome Fantacalcio"
        );

        createMapping(
                playerId,
                "GAZZETTA",
                "GA-HISTORY-001",
                "Nome Gazzetta"
        );

        long fantacalcioBatch =
                createBatch("FANTACALCIO");

        long gazzettaBatch =
                createBatch("GAZZETTA");

        long fantamarktBatch =
                createBatch("FANTAMARKT");

        insertRow(
                fantacalcioBatch,
                1,
                "FC-HISTORY-001",
                "Nome Fantacalcio",
                "Roma"
        );

        insertRow(
                gazzettaBatch,
                1,
                "GA-HISTORY-001",
                "Nome Gazzetta",
                "Bologna"
        );

        List<ResolvedPlayerIdentity> result =
                service.resolve(
                        fantacalcioBatch,
                        gazzettaBatch,
                        fantamarktBatch
                );

        assertThat(result)
                .hasSize(1);

        assertThat(
                result.getFirst()
                        .sourcesCount()
        )
                .isEqualTo(2);

        assertThat(
                result.getFirst()
                        .identityResolutionMethod()
        )
                .isEqualTo(
                        "HISTORICAL_MAPPING"
                );

        assertThat(
                result.getFirst()
                        .gazzettaName()
        )
                .isEqualTo("Nome Gazzetta");
    }

    @Test
    void shouldNotGuessWhenSameNameIsAmbiguous() {

        long fantacalcioBatch =
                createBatch("FANTACALCIO");

        long gazzettaBatch =
                createBatch("GAZZETTA");

        long fantamarktBatch =
                createBatch("FANTAMARKT");

        insertRow(
                fantacalcioBatch,
                1,
                "FC-AMB-001",
                "Carboni",
                "Monza"
        );

        insertRow(
                gazzettaBatch,
                1,
                "GA-AMB-001",
                "Carboni",
                "Inter"
        );

        insertRow(
                gazzettaBatch,
                2,
                "GA-AMB-002",
                "Carboni",
                "Parma"
        );

        List<ResolvedPlayerIdentity> result =
                service.resolve(
                        fantacalcioBatch,
                        gazzettaBatch,
                        fantamarktBatch
                );

        assertThat(result)
                .hasSize(1);

        ResolvedPlayerIdentity identity =
                result.getFirst();

        assertThat(identity.sourcesCount())
                .isEqualTo(1);

        assertThat(identity.gazzettaName())
                .isNull();

        assertThat(
                identity.identityResolutionMethod()
        )
                .isEqualTo(
                        "FANTACALCIO_ONLY"
                );
    }

    @Test
    void shouldUseSameClubToDisambiguateIdenticalNames() {

        long fantacalcioBatch =
                createBatch("FANTACALCIO");

        long gazzettaBatch =
                createBatch("GAZZETTA");

        long fantamarktBatch =
                createBatch("FANTAMARKT");

        insertRow(
                fantacalcioBatch,
                1,
                "FC-DISAMB-001",
                "Carboni",
                "Monza"
        );

        insertRow(
                gazzettaBatch,
                1,
                "GA-DISAMB-001",
                "Carboni",
                "Inter"
        );

        insertRow(
                gazzettaBatch,
                2,
                "GA-DISAMB-002",
                "Carboni",
                "Monza"
        );

        List<ResolvedPlayerIdentity> result =
                service.resolve(
                        fantacalcioBatch,
                        gazzettaBatch,
                        fantamarktBatch
                );

        assertThat(result)
                .hasSize(1);

        ResolvedPlayerIdentity identity =
                result.getFirst();

        assertThat(identity.sourcesCount())
                .isEqualTo(2);

        assertThat(
                identity.identityResolutionMethod()
        )
                .isEqualTo(
                        "EXACT_NAME_SAME_CLUB"
                );

        assertThat(identity.gazzettaClub())
                .isEqualTo("Monza");
    }

    @Test
    void shouldResolveSafeUniqueAbbreviationInsideSameClub() {

        long fantacalcioBatch =
                createBatch("FANTACALCIO");

        long gazzettaBatch =
                createBatch("GAZZETTA");

        long fantamarktBatch =
                createBatch("FANTAMARKT");

        insertRow(
                fantacalcioBatch,
                1,
                "FC-ABBR-001",
                "Ederson D.S.",
                "Atalanta"
        );

        insertRow(
                gazzettaBatch,
                1,
                "GA-ABBR-001",
                "EDERSON",
                "Atalanta"
        );

        List<ResolvedPlayerIdentity> result =
                service.resolve(
                        fantacalcioBatch,
                        gazzettaBatch,
                        fantamarktBatch
                );

        assertThat(result)
                .hasSize(1);

        ResolvedPlayerIdentity identity =
                result.getFirst();

        assertThat(identity.sourcesCount())
                .isEqualTo(2);

        assertThat(identity.gazzettaName())
                .isEqualTo("EDERSON");

        assertThat(
                identity.identityResolutionMethod()
        ).isEqualTo(
                "SAFE_UNIQUE_ABBREVIATION"
        );
    }

    @Test
    void shouldRejectAbbreviationWhenSecondaryRowMatchesTwoFantacalcioPlayers() {

        long fantacalcioBatch =
                createBatch("FANTACALCIO");

        long gazzettaBatch =
                createBatch("GAZZETTA");

        long fantamarktBatch =
                createBatch("FANTAMARKT");

        insertRow(
                fantacalcioBatch,
                1,
                "FC-SUL-001",
                "Sulemana I.",
                "Atalanta"
        );

        insertRow(
                fantacalcioBatch,
                2,
                "FC-SUL-002",
                "Sulemana K.",
                "Atalanta"
        );

        insertRow(
                gazzettaBatch,
                1,
                "GA-SUL-001",
                "SULEMANA",
                "Atalanta"
        );

        List<ResolvedPlayerIdentity> result =
                service.resolve(
                        fantacalcioBatch,
                        gazzettaBatch,
                        fantamarktBatch
                );

        assertThat(result)
                .hasSize(2);

        assertThat(result)
                .allSatisfy(identity -> {
                    assertThat(
                            identity.sourcesCount()
                    ).isEqualTo(1);

                    assertThat(
                            identity.gazzettaName()
                    ).isNull();
                });
    }
    @Test
    void shouldResolveNormalizedNameAfterRemovingTrailingInitial() {

        long fantacalcioBatch =
                createBatch("FANTACALCIO");

        long gazzettaBatch =
                createBatch("GAZZETTA");

        long fantamarktBatch =
                createBatch("FANTAMARKT");

        insertRowWithNormalizedName(
                fantacalcioBatch,
                1,
                "FC-NORM-001",
                "Konè M.",
                "kone m",
                "Roma"
        );

        insertRowWithNormalizedName(
                gazzettaBatch,
                1,
                "GA-NORM-001",
                "KONE",
                "kone",
                "Roma"
        );

        List<ResolvedPlayerIdentity> result =
                service.resolve(
                        fantacalcioBatch,
                        gazzettaBatch,
                        fantamarktBatch
                );

        assertThat(result)
                .hasSize(1);

        ResolvedPlayerIdentity identity =
                result.getFirst();

        assertThat(identity.sourcesCount())
                .isEqualTo(2);

        assertThat(identity.gazzettaName())
                .isEqualTo("KONE");

        assertThat(
                identity.identityResolutionMethod()
        ).isEqualTo(
                "NORMALIZED_WITHOUT_INITIALS"
        );
    }

    @Test
    void shouldKeepNormalizedInitialMatchAmbiguousWhenTwoFantacalcioPlayersCompete() {

        long fantacalcioBatch =
                createBatch("FANTACALCIO");

        long gazzettaBatch =
                createBatch("GAZZETTA");

        long fantamarktBatch =
                createBatch("FANTAMARKT");

        insertRowWithNormalizedName(
                fantacalcioBatch,
                1,
                "FC-NORM-SUL-001",
                "Sulemana I.",
                "sulemana i",
                "Atalanta"
        );

        insertRowWithNormalizedName(
                fantacalcioBatch,
                2,
                "FC-NORM-SUL-002",
                "Sulemana K.",
                "sulemana k",
                "Atalanta"
        );

        insertRowWithNormalizedName(
                gazzettaBatch,
                1,
                "GA-NORM-SUL-001",
                "SULEMANA",
                "sulemana",
                "Atalanta"
        );

        List<ResolvedPlayerIdentity> result =
                service.resolve(
                        fantacalcioBatch,
                        gazzettaBatch,
                        fantamarktBatch
                );

        assertThat(result)
                .hasSize(2);

        assertThat(result)
                .allSatisfy(identity -> {
                    assertThat(
                            identity.sourcesCount()
                    ).isEqualTo(1);

                    assertThat(
                            identity.gazzettaName()
                    ).isNull();
                });
    }

    @Test
    void shouldNotAutomaticallyResolveUsingOnlyFinalNameToken() {

        long fantacalcioBatch =
                createBatch("FANTACALCIO");

        long gazzettaBatch =
                createBatch("GAZZETTA");

        long fantamarktBatch =
                createBatch("FANTAMARKT");

        insertRowWithNormalizedName(
                fantacalcioBatch,
                1,
                "FC-TOKEN-001",
                "Douglas Luiz",
                "douglas luiz",
                "Juventus"
        );

        insertRowWithNormalizedName(
                gazzettaBatch,
                1,
                "GA-TOKEN-001",
                "LUIZ",
                "luiz",
                "Juventus"
        );

        List<ResolvedPlayerIdentity> result =
                service.resolve(
                        fantacalcioBatch,
                        gazzettaBatch,
                        fantamarktBatch
                );

        assertThat(result)
                .hasSize(1);

        ResolvedPlayerIdentity identity =
                result.getFirst();

        assertThat(identity.sourcesCount())
                .isEqualTo(1);

        assertThat(identity.gazzettaName())
                .isNull();

        assertThat(
                identity.identityResolutionMethod()
        ).isEqualTo(
                "FANTACALCIO_ONLY"
        );
    }
    @Test
    void shouldRemoveOnlyExplicitTrailingAbbreviationTokens() {

        long fantacalcioBatch =
                createBatch("FANTACALCIO");

        long gazzettaBatch =
                createBatch("GAZZETTA");

        long fantamarktBatch =
                createBatch("FANTAMARKT");

        insertRowWithNormalizedName(
                fantacalcioBatch,
                1,
                "FC-SAFE-INITIALS-001",
                "De Gea X.",
                "de gea x",
                "Fiorentina"
        );

        insertRowWithNormalizedName(
                gazzettaBatch,
                1,
                "GA-SAFE-INITIALS-001",
                "DE GEA",
                "de gea",
                "Fiorentina"
        );

        List<ResolvedPlayerIdentity> result =
                service.resolve(
                        fantacalcioBatch,
                        gazzettaBatch,
                        fantamarktBatch
                );

        assertThat(result)
                .hasSize(1);

        ResolvedPlayerIdentity identity =
                result.getFirst();

        assertThat(identity.sourcesCount())
                .isEqualTo(2);

        assertThat(identity.gazzettaName())
                .isEqualTo("DE GEA");

        assertThat(
                identity.identityResolutionMethod()
        ).isEqualTo(
                "NORMALIZED_WITHOUT_INITIALS"
        );
    }
    private long createBatch(
            String source
    ) {
        return jdbcClient.sql("""
                INSERT INTO import_batches (
                    source,
                    season_id,
                    original_filename,
                    status,
                    total_rows,
                    valid_rows,
                    invalid_rows,
                    source_reference,
                    source_observed_at,
                    completed_at
                )
                SELECT
                    :source,
                    season.id,
                    :filename,
                    'COMPLETED',
                    10,
                    10,
                    0,
                    'https://source.test',
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                FROM seasons season
                WHERE season.code = '2024/2025'
                RETURNING id
                """)
                .param("source", source)
                .param(
                        "filename",
                        "identity-"
                                + source.toLowerCase()
                                + ".csv"
                )
                .query(Long.class)
                .single();
    }

    private void insertRow(
            long batchId,
            int rowNumber,
            String externalId,
            String name,
            String club
    ) {

        jdbcClient.sql("""
                INSERT INTO player_import_rows (
                    batch_id,
                    row_number,
                    external_id,
                    external_name,
                    normalized_name,
                    club_name,
                    normalized_club_name,
                    fantasy_role,
                    valid,
                    raw_data
                )
                VALUES (
                    :batchId,
                    :rowNumber,
                    :externalId,
                    :name,
                    :normalizedName,
                    :club,
                    :normalizedClub,
                    'C',
                    TRUE,
                    CAST(:rawData AS JSONB)
                )
                """)
                .param("batchId", batchId)
                .param("rowNumber", rowNumber)
                .param("externalId", externalId)
                .param("name", name)
                .param(
                        "normalizedName",
                        name.toLowerCase()
                )
                .param("club", club)
                .param(
                        "normalizedClub",
                        club.toLowerCase()
                )
                .param(
                        "rawData",
                        """
                        {
                          "identityTest": true
                        }
                        """
                )
                .update();
    }

    private void insertRowWithNormalizedName(
            long batchId,
            int rowNumber,
            String externalId,
            String name,
            String normalizedName,
            String club
    ) {

        jdbcClient.sql("""
                INSERT INTO player_import_rows (
                    batch_id,
                    row_number,
                    external_id,
                    external_name,
                    normalized_name,
                    club_name,
                    normalized_club_name,
                    fantasy_role,
                    valid,
                    raw_data
                )
                VALUES (
                    :batchId,
                    :rowNumber,
                    :externalId,
                    :name,
                    :normalizedName,
                    :club,
                    :normalizedClub,
                    'C',
                    TRUE,
                    CAST(:rawData AS JSONB)
                )
                """)
                .param("batchId", batchId)
                .param("rowNumber", rowNumber)
                .param("externalId", externalId)
                .param("name", name)
                .param("normalizedName", normalizedName)
                .param("club", club)
                .param(
                        "normalizedClub",
                        club.toLowerCase()
                )
                .param(
                        "rawData",
                        """
                        {
                          "normalizedIdentityTest": true
                        }
                        """
                )
                .update();
    }
    private Long createPlayer(
            String name
    ) {
        return jdbcClient.sql("""
                INSERT INTO players (
                    last_name,
                    display_name,
                    normalized_name,
                    active
                )
                VALUES (
                    :name,
                    :name,
                    :normalizedName,
                    TRUE
                )
                RETURNING id
                """)
                .param("name", name)
                .param(
                        "normalizedName",
                        name.toLowerCase()
                )
                .query(Long.class)
                .single();
    }

    private void createMapping(
            Long playerId,
            String source,
            String externalId,
            String externalName
    ) {
        jdbcClient.sql("""
                INSERT INTO player_source_mappings (
                    player_id,
                    source,
                    external_id,
                    external_name,
                    verified
                )
                VALUES (
                    :playerId,
                    :source,
                    :externalId,
                    :externalName,
                    TRUE
                )
                """)
                .param("playerId", playerId)
                .param("source", source)
                .param("externalId", externalId)
                .param(
                        "externalName",
                        externalName
                )
                .update();
    }
}