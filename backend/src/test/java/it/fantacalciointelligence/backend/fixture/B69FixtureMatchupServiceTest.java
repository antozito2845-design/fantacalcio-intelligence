package it.fantacalciointelligence.backend.fixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class B69FixtureMatchupServiceTest {

    @Autowired
    private B69FixtureMatchupService service;

    @Autowired
    private JdbcClient jdbcClient;

    private String clubSlug;

    @BeforeEach
    void setUp() {

        Long seasonId =
                jdbcClient.sql("""
                        SELECT id
                        FROM seasons
                        WHERE code = '2026/2027'
                        """)
                        .query(Long.class)
                        .single();

        List<Club> clubs =
                jdbcClient.sql("""
                        SELECT
                            club.id,
                            club.slug

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
                        .query(
                                (rs, rowNumber) ->
                                        new Club(
                                                rs.getLong("id"),
                                                rs.getString("slug")
                                        )
                        )
                        .list();

        assertThat(clubs)
                .hasSize(2);

        Club home =
                clubs.get(0);

        Club away =
                clubs.get(1);

        clubSlug =
                home.slug();

        strength(
                seasonId,
                home.id(),
                65,
                70,
                68,
                72,
                61
        );

        strength(
                seasonId,
                away.id(),
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
                    1,
                    :home,
                    :away,
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
                        "home",
                        home.id()
                )
                .param(
                        "away",
                        away.id()
                )
                .update();
    }

    @Test
    void returnsRoleAwareFixtureForEveryFantasyRole() {

        List<B69FixtureMatchupResponse> result =
                service.findClubMatchups(
                        "2026/2027",
                        clubSlug,
                        1,
                        1
                );

        assertThat(result)
                .hasSize(1);

        B69FixtureMatchupResponse fixture =
                result.getFirst();

        assertThat(fixture.home())
                .isTrue();

        assertThat(fixture.roles())
                .extracting(
                        B69RoleDifficultyResponse::role
                )
                .containsExactly(
                        "P",
                        "D",
                        "C",
                        "A"
                );

        assertThat(fixture.roles())
                .allSatisfy(role -> {

                    assertThat(
                            role.difficultyScore()
                    ).isNotNull();

                    assertThat(
                            role.difficultyLevel()
                    )
                            .isIn(
                                    "FACILE",
                                    "MEDIA",
                                    "DIFFICILE"
                            );

                    assertThat(
                            role.evidenceCoverage()
                    )
                            .isEqualByComparingTo(
                                    "100.00"
                            );

                    assertThat(
                            role.evidenceTier()
                    )
                            .isEqualTo(
                                    "HIGH"
                            );
                });
    }

    @Test
    void acceptsClubNameAsWellAsSlug() {

        String name =
                jdbcClient.sql("""
                        SELECT name
                        FROM clubs
                        WHERE slug = :slug
                        """)
                        .param(
                                "slug",
                                clubSlug
                        )
                        .query(String.class)
                        .single();

        assertThat(
                service.findClubMatchups(
                        "2026/2027",
                        name,
                        1,
                        1
                )
        ).hasSize(1);
    }

    @Test
    void nonexistentClubIsRejected() {

        assertThatThrownBy(() ->
                service.findClubMatchups(
                        "2026/2027",
                        "club-che-non-esiste",
                        1,
                        1
                )
        )
                .isInstanceOf(
                        it.fantacalciointelligence.backend.common.ResourceNotFoundException.class
                );
    }

    @Test
    void moreThanTenMatchdaysAreRejected() {

        assertThatThrownBy(() ->
                service.findClubMatchups(
                        "2026/2027",
                        clubSlug,
                        1,
                        11
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "cannot exceed 10"
                );
    }

    private void strength(
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
                .param("clubId", clubId)
                .param("seasonId", seasonId)
                .param("overall", overall)
                .param("attack", attack)
                .param("defense", defense)
                .param("home", home)
                .param("away", away)
                .update();
    }

    private record Club(
            Long id,
            String slug
    ) {
    }
}