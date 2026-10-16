package it.fantacalciointelligence.backend.scouting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class B70NewListoneArrivalIntegrationTest {

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private B70NewListoneArrivalService service;

    @Autowired
    private MockMvc mockMvc;

    private Long seasonId;
    private Long playerId;
    private Long clubId;

    @BeforeEach
    void seed() {

        seasonId = jdbcClient.sql("""
                        SELECT id
                        FROM seasons
                        WHERE code = '2026/2027'
                        """)
                .query(Long.class)
                .single();

        playerId = jdbcClient.sql("""
                        SELECT player.id
                        FROM players player
                        JOIN player_registrations registration
                            ON registration.player_id = player.id
                           AND registration.season_id = :seasonId
                           AND registration.registration_status =
                               'ACTIVE'
                        JOIN player_roles role
                            ON role.player_id = player.id
                           AND role.season_id = :seasonId
                           AND role.source = 'INTERNAL'
                        WHERE player.active = TRUE
                        ORDER BY player.id
                        LIMIT 1
                        """)
                .param(
                        "seasonId",
                        seasonId
                )
                .query(Long.class)
                .single();

        clubId = jdbcClient.sql("""
                        SELECT registration.club_id
                        FROM player_registrations registration
                        WHERE registration.player_id = :playerId
                          AND registration.season_id = :seasonId
                        """)
                .param(
                        "playerId",
                        playerId
                )
                .param(
                        "seasonId",
                        seasonId
                )
                .query(Long.class)
                .single();

        jdbcClient.sql("""
                        INSERT INTO player_roles (
                            player_id,
                            season_id,
                            source,
                            fantasy_role,
                            primary_role
                        )
                        SELECT
                            role.player_id,
                            role.season_id,
                            'FANTACALCIO',
                            role.fantasy_role,
                            TRUE
                        FROM player_roles role
                        WHERE role.player_id = :playerId
                          AND role.season_id = :seasonId
                          AND role.source = 'INTERNAL'
                        ON CONFLICT (
                            player_id,
                            season_id,
                            source
                        )
                        DO UPDATE SET
                            fantasy_role =
                                EXCLUDED.fantasy_role,
                            primary_role = TRUE
                        """)
                .param(
                        "playerId",
                        playerId
                )
                .param(
                        "seasonId",
                        seasonId
                )
                .update();

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
                            'FANTACALCIO',
                            'B70-NEW-PLAYER-900001',
                            'B70 New Player',
                            TRUE
                        )
                        """)
                .param(
                        "playerId",
                        playerId
                )
                .update();

        Long previousBatch =
                createBatch(
                        "2099-01-01 00:00:00+00"
                );

        Long currentBatch =
                createBatch(
                        "2099-01-02 00:00:00+00"
                );

        /*
         * Previous snapshot deliberately does not contain
         * B70-NEW-PLAYER-900001.
         */
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
                            quotation,
                            fantasy_market_value,
                            valid,
                            raw_data
                        )
                        VALUES (
                            :batchId,
                            1,
                            'B70-OLD-PLAYER-900000',
                            'Old Snapshot Player',
                            'old snapshot player',
                            'Test Club',
                            'test club',
                            'D',
                            1,
                            1,
                            TRUE,
                            '{"out_of_game":"false"}'::jsonb
                        )
                        """)
                .param(
                        "batchId",
                        previousBatch
                )
                .update();

        String role =
                jdbcClient.sql("""
                        SELECT fantasy_role
                        FROM player_roles
                        WHERE player_id = :playerId
                          AND season_id = :seasonId
                          AND source = 'FANTACALCIO'
                        """)
                .param(
                        "playerId",
                        playerId
                )
                .param(
                        "seasonId",
                        seasonId
                )
                .query(String.class)
                .single();

        String clubName =
                jdbcClient.sql("""
                        SELECT name
                        FROM clubs
                        WHERE id = :clubId
                        """)
                .param(
                        "clubId",
                        clubId
                )
                .query(String.class)
                .single();

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
                            quotation,
                            fantasy_market_value,
                            valid,
                            raw_data
                        )
                        VALUES (
                            :batchId,
                            1,
                            'B70-NEW-PLAYER-900001',
                            'B70 New Player',
                            'b70 new player',
                            :clubName,
                            LOWER(:clubName),
                            :role,
                            5,
                            20,
                            TRUE,
                            '{"out_of_game":"false"}'::jsonb
                        )
                        """)
                .param(
                        "batchId",
                        currentBatch
                )
                .param(
                        "clubName",
                        clubName
                )
                .param(
                        "role",
                        role
                )
                .update();
    }

    private Long createBatch(
            String observedAt
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
                            completed_at,
                            source_reference,
                            source_observed_at
                        )
                        VALUES (
                            'FANTACALCIO',
                            :seasonId,
                            'b70-test-listone.csv',
                            'COMPLETED',
                            1,
                            1,
                            0,
                            CAST(:observedAt AS TIMESTAMPTZ),
                            'B70_TEST',
                            CAST(:observedAt AS TIMESTAMPTZ)
                        )
                        RETURNING id
                        """)
                .param(
                        "seasonId",
                        seasonId
                )
                .param(
                        "observedAt",
                        observedAt
                )
                .query(Long.class)
                .single();
    }

    @Test
    void shouldDetectExternalIdNewInLatestListone() {

        List<B70NewListoneArrivalResponse> result =
                service.findNewEntries(
                        "2026/2027",
                        null,
                        null
                );

        assertThat(result)
                .extracting(
                        B70NewListoneArrivalResponse
                                ::playerId
                )
                .contains(playerId);

        B70NewListoneArrivalResponse entry =
                result.stream()
                        .filter(item ->
                                item.playerId()
                                        .equals(playerId)
                        )
                        .findFirst()
                        .orElseThrow();

        assertThat(
                entry.fantacalcioExternalId()
        ).isEqualTo(
                "B70-NEW-PLAYER-900001"
        );

        assertThat(
                entry.discoveryStatus()
        ).isEqualTo(
                "NEW_FANTACALCIO_LISTONE_ENTRY"
        );
    }

    @Test
    void shouldNotCallListoneSignalOfficialTransferProof() {

        B70NewListoneArrivalResponse entry =
                service.findNewEntries(
                                "2026/2027",
                                null,
                                null
                        )
                        .stream()
                        .filter(item ->
                                item.playerId()
                                        .equals(playerId)
                        )
                        .findFirst()
                        .orElseThrow();

        assertThat(
                entry.officialTransferEventPresent()
        ).isFalse();
    }

    @Test
    void shouldDisappearOnceScoutingExists() {

        jdbcClient.sql("""
                        INSERT INTO foreign_player_scouting_reports (
                            player_id,
                            season_id,
                            previous_club,
                            previous_league,
                            previous_country_code,
                            transfer_type,
                            adaptation_score,
                            potential_score,
                            risk_score,
                            confidence_score,
                            recommendation,
                            scouting_summary
                        )
                        VALUES (
                            :playerId,
                            :seasonId,
                            'Test Foreign Club',
                            'Test Foreign League',
                            'FR',
                            'PERMANENT',
                            75,
                            80,
                            30,
                            85,
                            'MONITOR',
                            'B70 test completed scouting'
                        )
                        ON CONFLICT (
                            player_id,
                            season_id
                        )
                        DO UPDATE SET
                            scouting_summary =
                                EXCLUDED.scouting_summary
                        """)
                .param(
                        "playerId",
                        playerId
                )
                .param(
                        "seasonId",
                        seasonId
                )
                .update();

        List<B70NewListoneArrivalResponse> result =
                service.findNewEntries(
                        "2026/2027",
                        null,
                        null
                );

        assertThat(result)
                .extracting(
                        B70NewListoneArrivalResponse
                                ::playerId
                )
                .doesNotContain(playerId);
    }

    @Test
    void shouldRejectInvalidRole() {

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() ->
                        service.findNewEntries(
                                "2026/2027",
                                "X",
                                null
                        )
                )
                .isInstanceOf(
                        it.fantacalciointelligence
                                .backend
                                .common
                                .BadRequestException
                                .class
                );
    }

    @Test
    void shouldExposePublicEndpoint()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/v1/scouting/arrivals/listone-new"
                        )
                                .param(
                                        "season",
                                        "2026/2027"
                                )
                )
                .andExpect(
                        status().isOk()
                );
    }
}