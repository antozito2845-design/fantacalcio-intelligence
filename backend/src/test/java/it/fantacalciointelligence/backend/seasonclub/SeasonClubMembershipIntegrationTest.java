package it.fantacalciointelligence.backend.seasonclub;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SeasonClubMembershipIntegrationTest {
    private static final Set<String> SEEDED_2026_2027 = Set.of(
            "atalanta", "bologna", "cagliari", "como", "fiorentina",
            "frosinone", "genoa", "inter", "juventus", "lazio",
            "lecce", "milan", "monza", "napoli", "parma", "roma",
            "sassuolo", "torino", "udinese", "venezia");

    @Autowired JdbcClient jdbc;

    @Test
    void v54BackfillsEveryExplicitlySeeded2026SeasonClub() {
        Set<String> memberships = Set.copyOf(jdbc.sql("""
                SELECT club.slug FROM season_clubs membership
                JOIN seasons season ON season.id=membership.season_id
                JOIN clubs club ON club.id=membership.club_id
                WHERE season.code='2026/2027'
                """).query(String.class).list());

        assertThat(memberships).containsAll(SEEDED_2026_2027);
    }

    @Test
    void sameGlobalClubCanBelongToOneSeasonAndNotAnother() {
        long first=season("2110/2111",2110);long second=season("2111/2112",2111);
        long club=jdbc.sql("""
                INSERT INTO clubs(name,short_name,slug,country_code,active)
                VALUES('B61 Membership','B61 Membership','b61-membership','IT',TRUE) RETURNING id
                """).query(Long.class).single();
        jdbc.sql("INSERT INTO season_clubs(season_id,club_id) VALUES(:season,:club)")
                .param("season",first).param("club",club).update();

        assertThat(member(first,club)).isTrue();
        assertThat(member(second,club)).isFalse();
    }

    private long season(String code,int year){return jdbc.sql("""
            INSERT INTO seasons(code,start_year,end_year,current_season)
            VALUES(:code,:start,:end,FALSE) RETURNING id
            """).param("code",code).param("start",year).param("end",year+1).query(Long.class).single();}
    private boolean member(long season,long club){return jdbc.sql("""
            SELECT EXISTS(SELECT 1 FROM season_clubs WHERE season_id=:season AND club_id=:club)
            """).param("season",season).param("club",club).query(Boolean.class).single();}
}
