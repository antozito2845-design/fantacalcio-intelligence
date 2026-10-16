package it.fantacalciointelligence.backend.calendarintelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class B69RoleAwareFixtureWindowServiceTest {

    private static final int MATCHDAY = 1;

    @Autowired
    private FixtureWindowService service;

    @Autowired
    private JdbcClient jdbcClient;

    private FixtureIdentity fixture;

    @BeforeEach
    void setUpRealisticFixtureEvidence() {

        Long seasonId =
                jdbcClient.sql("""
                        SELECT id
                        FROM seasons
                        WHERE code = '2026/2027'
                        """)
                        .query(Long.class)
                        .single();

        List<Long> clubs =
                jdbcClient.sql("""
                        SELECT club.id

                        FROM clubs club

                        JOIN season_clubs membership
                            ON membership.club_id =
                                club.id

                        WHERE membership.season_id =
                                :seasonId

                          AND club.active = TRUE

                        ORDER BY club.id

                        LIMIT 2
                        """)
                        .param(
                                "seasonId",
                                seasonId
                        )
                        .query(Long.class)
                        .list();

        assertThat(clubs)
                .hasSize(2);

        Long homeClubId =
                clubs.get(0);

        Long awayClubId =
                clubs.get(1);

        upsertStrength(
                seasonId,
                homeClubId,
                65,
                70,
                68,
                72,
                61
        );

        upsertStrength(
                seasonId,
                awayClubId,
                58,
                55,
                52,
                63,
                51
        );

        jdbcClient.sql("""
                INSERT INTO fixtures (
                    season_id,
                    matchday,
                    home_club_id,
                    away_club_id,
                    status
                )
                VALUES (
                    :seasonId,
                    :matchday,
                    :homeClubId,
                    :awayClubId,
                    'SCHEDULED'
                )

                ON CONFLICT (
                    season_id,
                    matchday,
                    home_club_id,
                    away_club_id
                )

                DO UPDATE SET
                    status = 'SCHEDULED'
                """)
                .param(
                        "seasonId",
                        seasonId
                )
                .param(
                        "matchday",
                        MATCHDAY
                )
                .param(
                        "homeClubId",
                        homeClubId
                )
                .param(
                        "awayClubId",
                        awayClubId
                )
                .update();

        fixture =
                jdbcClient.sql("""
                        SELECT
                            fixture.matchday,
                            fixture.home_club_id,
                            fixture.away_club_id

                        FROM fixtures fixture

                        WHERE fixture.season_id =
                            :seasonId

                          AND fixture.matchday =
                            :matchday

                          AND fixture.home_club_id =
                            :homeClubId

                          AND fixture.away_club_id =
                            :awayClubId
                        """)
                        .param(
                                "seasonId",
                                seasonId
                        )
                        .param(
                                "matchday",
                                MATCHDAY
                        )
                        .param(
                                "homeClubId",
                                homeClubId
                        )
                        .param(
                                "awayClubId",
                                awayClubId
                        )
                        .query(
                                (rs, rowNumber) ->
                                        new FixtureIdentity(
                                                rs.getInt(
                                                        "matchday"
                                                ),
                                                rs.getLong(
                                                        "home_club_id"
                                                ),
                                                rs.getLong(
                                                        "away_club_id"
                                                )
                                        )
                        )
                        .single();
    }

    @Test
    void realFixtureWindowContainsAllFantasyRoles() {

        Map<Long, B69RoleFixtureProfile> profiles =
                service.loadRoleAwareClubProfiles(
                        "2026/2027",
                        List.of(
                                fixture.homeClubId(),
                                fixture.awayClubId()
                        ),
                        fixture.matchday(),
                        1
                );

        assertThat(profiles)
                .containsKeys(
                        fixture.homeClubId(),
                        fixture.awayClubId()
                );

        B69RoleFixtureDay home =
                profiles.get(
                                fixture.homeClubId()
                        )
                        .matchdays()
                        .get(
                                fixture.matchday()
                        );

        B69RoleFixtureDay away =
                profiles.get(
                                fixture.awayClubId()
                        )
                        .matchdays()
                        .get(
                                fixture.matchday()
                        );

        assertThat(home)
                .isNotNull();

        assertThat(away)
                .isNotNull();

        assertThat(home.home())
                .isTrue();

        assertThat(away.home())
                .isFalse();

        assertThat(home.opponentClubId())
                .isEqualTo(
                        fixture.awayClubId()
                );

        assertThat(away.opponentClubId())
                .isEqualTo(
                        fixture.homeClubId()
                );

        assertThat(home.roleMatchups())
                .containsOnlyKeys(
                        "P",
                        "D",
                        "C",
                        "A"
                );

        assertThat(away.roleMatchups())
                .containsOnlyKeys(
                        "P",
                        "D",
                        "C",
                        "A"
                );

        for (
                String role :
                List.of(
                        "P",
                        "D",
                        "C",
                        "A"
                )
        ) {

            assertThat(
                    home.matchup(role)
            ).isNotNull();

            assertThat(
                    away.matchup(role)
            ).isNotNull();

            assertThat(
                    home.difficultyScore(role)
            ).isNotNull();

            assertThat(
                    away.difficultyScore(role)
            ).isNotNull();

            assertThat(
                    home.difficultyScore(role)
            )
                    .isBetween(
                            java.math.BigDecimal.ZERO,
                            new java.math.BigDecimal("100")
                    );

            assertThat(
                    away.difficultyScore(role)
            )
                    .isBetween(
                            java.math.BigDecimal.ZERO,
                            new java.math.BigDecimal("100")
                    );

            assertThat(
                    home.usable(role)
            ).isTrue();

            assertThat(
                    away.usable(role)
            ).isTrue();
        }
    }

    @Test
    void roleAwareWindowIsDeterministic() {

        List<Long> clubs =
                List.of(
                        fixture.homeClubId(),
                        fixture.awayClubId()
                );

        var first =
                service.loadRoleAwareClubProfiles(
                        "2026/2027",
                        clubs,
                        fixture.matchday(),
                        1
                );

        var second =
                service.loadRoleAwareClubProfiles(
                        "2026/2027",
                        clubs,
                        fixture.matchday(),
                        1
                );

        assertThat(second)
                .isEqualTo(first);
    }

    @Test
    void existingLegacyWindowStillWorks() {

        Map<Long, ClubFixtureProfile> profiles =
                service.loadClubProfiles(
                        "2026/2027",
                        List.of(
                                fixture.homeClubId(),
                                fixture.awayClubId()
                        ),
                        fixture.matchday(),
                        1
                );

        assertThat(profiles)
                .containsKeys(
                        fixture.homeClubId(),
                        fixture.awayClubId()
                );

        assertThat(
                profiles.get(
                                fixture.homeClubId()
                        )
                        .matchdays()
                        .get(
                                fixture.matchday()
                        )
                        .difficultyScore()
        ).isNotNull();
    }

    @Test
    void roleSpecificScoresAreActuallyDifferent() {

        B69RoleFixtureDay home =
                service.loadRoleAwareClubProfiles(
                                "2026/2027",
                                List.of(
                                        fixture.homeClubId()
                                ),
                                fixture.matchday(),
                                1
                        )
                        .get(
                                fixture.homeClubId()
                        )
                        .matchdays()
                        .get(
                                fixture.matchday()
                        );

        assertThat(
                List.of(
                        home.difficultyScore("P"),
                        home.difficultyScore("D"),
                        home.difficultyScore("C"),
                        home.difficultyScore("A")
                )
                        .stream()
                        .distinct()
                        .count()
        )
                .isGreaterThan(1);
    }

    @Test
    void invalidWindowIsRejected() {

        assertThatThrownBy(() ->
                service.loadRoleAwareClubProfiles(
                        "2026/2027",
                        List.of(1L),
                        0,
                        5
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "Invalid fixture window"
                );
    }

    private void upsertStrength(
            Long seasonId,
            Long clubId,
            int overall,
            int attack,
            int defense,
            int home,
            int away
    ) {

        jdbcClient.sql("""
                INSERT INTO club_strength_ratings (
                    club_id,
                    season_id,
                    overall_rating,
                    attacking_rating,
                    defensive_rating,
                    home_rating,
                    away_rating
                )
                VALUES (
                    :clubId,
                    :seasonId,
                    :overall,
                    :attack,
                    :defense,
                    :home,
                    :away
                )

                ON CONFLICT (
                    club_id,
                    season_id
                )

                DO UPDATE SET
                    overall_rating =
                        EXCLUDED.overall_rating,

                    attacking_rating =
                        EXCLUDED.attacking_rating,

                    defensive_rating =
                        EXCLUDED.defensive_rating,

                    home_rating =
                        EXCLUDED.home_rating,

                    away_rating =
                        EXCLUDED.away_rating
                """)
                .param(
                        "clubId",
                        clubId
                )
                .param(
                        "seasonId",
                        seasonId
                )
                .param(
                        "overall",
                        overall
                )
                .param(
                        "attack",
                        attack
                )
                .param(
                        "defense",
                        defense
                )
                .param(
                        "home",
                        home
                )
                .param(
                        "away",
                        away
                )
                .update();
    }

    private record FixtureIdentity(
            int matchday,
            Long homeClubId,
            Long awayClubId
    ) {
    }
}