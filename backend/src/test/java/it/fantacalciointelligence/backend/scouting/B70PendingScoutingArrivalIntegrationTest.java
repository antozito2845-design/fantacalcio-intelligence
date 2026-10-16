package it.fantacalciointelligence.backend.scouting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
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
class B70PendingScoutingArrivalIntegrationTest {

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private B70PendingScoutingArrivalService service;

    @Autowired
    private MockMvc mockMvc;

    private Long eligiblePlayerId;
    private Long completedPlayerId;
    private Long seasonId;
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

        clubId = jdbcClient.sql("""
                        SELECT club_id
                        FROM season_clubs
                        WHERE season_id = :seasonId
                        ORDER BY club_id
                        LIMIT 1
                        """)
                .param("seasonId", seasonId)
                .query(Long.class)
                .single();

        List<Long> players = jdbcClient.sql("""
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
                        LIMIT 2
                        """)
                .param("seasonId", seasonId)
                .query(Long.class)
                .list();

        assertThat(players)
                .hasSize(2);

        eligiblePlayerId = players.get(0);
        completedPlayerId = players.get(1);

        preparePlayer(
                eligiblePlayerId
        );

        preparePlayer(
                completedPlayerId
        );

        Long eligibleClaimId =
                createOfficialTransferClaim(
                        eligiblePlayerId,
                        "https://official.test/eligible"
                );

        Long completedClaimId =
                createOfficialTransferClaim(
                        completedPlayerId,
                        "https://official.test/completed"
                );

        createArrivalEvent(
                eligiblePlayerId,
                eligibleClaimId,
                "https://official.test/eligible"
        );

        createArrivalEvent(
                completedPlayerId,
                completedClaimId,
                "https://official.test/completed"
        );

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
                            'Completed Foreign Club',
                            'Completed Foreign League',
                            'FR',
                            'PERMANENT',
                            80,
                            85,
                            30,
                            90,
                            'RECOMMENDED',
                            'Already completed'
                        )
                        ON CONFLICT (
                            player_id,
                            season_id
                        )
                        DO UPDATE SET
                            scouting_summary =
                                'Already completed'
                        """)
                .param(
                        "playerId",
                        completedPlayerId
                )
                .param(
                        "seasonId",
                        seasonId
                )
                .update();
    }

    private void preparePlayer(
            Long playerId
    ) {

        jdbcClient.sql("""
                        UPDATE player_registrations
                        SET club_id = :clubId,
                            registration_status = 'ACTIVE'
                        WHERE player_id = :playerId
                          AND season_id = :seasonId
                        """)
                .param("clubId", clubId)
                .param("playerId", playerId)
                .param("seasonId", seasonId)
                .update();

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
                .param("playerId", playerId)
                .param("seasonId", seasonId)
                .update();
    }

    private Long createOfficialTransferClaim(
            Long playerId,
            String reference
    ) {

        return jdbcClient.sql("""
                        INSERT INTO player_transfer_claims (
                            player_id,
                            season_id,
                            from_club_id,
                            to_club_id,
                            destination_club_name,
                            source,
                            source_reference,
                            source_published_at,
                            effective_at,
                            verification_status,
                            verified_at,
                            verification_source,
                            verification_reference,
                            verification_published_at
                        )
                        VALUES (
                            :playerId,
                            :seasonId,
                            :clubId,
                            :clubId,
                            NULL,
                            'OFFICIAL_CLUB',
                            :reference,
                            CURRENT_TIMESTAMP,
                            CURRENT_DATE,
                            'OFFICIAL',
                            CURRENT_TIMESTAMP,
                            'OFFICIAL_CLUB',
                            :reference,
                            CURRENT_TIMESTAMP
                        )
                        RETURNING id
                        """)
                .param("playerId", playerId)
                .param("seasonId", seasonId)
                .param("clubId", clubId)
                .param("reference", reference)
                .query(Long.class)
                .single();
    }

    private void createArrivalEvent(
            Long playerId,
            Long claimId,
            String reference
    ) {

        jdbcClient.sql("""
                        INSERT INTO player_registration_events (
                            player_id,
                            season_id,
                            from_club_id,
                            to_club_id,
                            destination_club_name,
                            event_type,
                            effective_at,
                            transfer_claim_id,
                            source,
                            source_reference
                        )
                        VALUES (
                            :playerId,
                            :seasonId,
                            :clubId,
                            :clubId,
                            NULL,
                            'TRANSFER_IN_SERIE_A',
                            :effectiveAt,
                            :claimId,
                            'OFFICIAL_CLUB',
                            :reference
                        )
                        """)
                .param("playerId", playerId)
                .param("seasonId", seasonId)
                .param("clubId", clubId)
                .param(
                        "effectiveAt",
                        LocalDate.of(
                                2026,
                                8,
                                20
                        )
                )
                .param("claimId", claimId)
                .param("reference", reference)
                .update();
    }

    @Test
    void shouldExposeOnlyArrivalsStillRequiringScouting() {

        List<B70PendingScoutingArrivalResponse> result =
                service.findPending(
                        "2026/2027",
                        null,
                        null
                );

        assertThat(result)
                .extracting(
                        B70PendingScoutingArrivalResponse
                                ::playerId
                )
                .contains(
                        eligiblePlayerId
                )
                .doesNotContain(
                        completedPlayerId
                );

        B70PendingScoutingArrivalResponse candidate =
                result.stream()
                        .filter(item ->
                                item.playerId()
                                        .equals(
                                                eligiblePlayerId
                                        )
                        )
                        .findFirst()
                        .orElseThrow();

        assertThat(
                candidate.scoutingStatus()
        ).isEqualTo(
                "SCOUTING_REQUIRED"
        );

        assertThat(
                candidate.transferSource()
        ).isEqualTo(
                "OFFICIAL_CLUB"
        );

        assertThat(
                candidate.transferReference()
        ).isNotBlank();
    }

    @Test
    void shouldRejectInvalidRole() {

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() ->
                        service.findPending(
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
    void shouldExposePublicPendingEndpoint()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/v1/scouting/arrivals/pending"
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