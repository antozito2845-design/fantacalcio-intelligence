package it.fantacalciointelligence.backend.auction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;

public final class B51TestMarket {

    private B51TestMarket() {
    }

    public static List<AuctionPriceResponse> pricedPlayers(
            JdbcClient jdbcClient,
            String season,
            int budget,
            int teams,
            boolean defenseModifier
    ) {
        return jdbcClient.sql("""
                SELECT DISTINCT
                    player.id,
                    player.display_name,
                    club.name AS club_name,
                    role.fantasy_role
                FROM players player
                JOIN player_registrations registration
                  ON registration.player_id = player.id
                 AND registration.registration_status = 'ACTIVE'
                JOIN seasons season
                  ON season.id = registration.season_id
                JOIN clubs club
                  ON club.id = registration.club_id
                JOIN season_clubs season_club
                  ON season_club.season_id = season.id
                 AND season_club.club_id = club.id
                JOIN player_roles role
                  ON role.player_id = player.id
                 AND role.season_id = season.id
                 AND role.source = 'FANTACALCIO'
                WHERE player.active = TRUE
                  AND season.code = :season
                ORDER BY player.id
                """)
                .param("season", season)
                .query()
                .listOfRows()
                .stream()
                .map(row -> {
                    long id = ((Number) row.get("id")).longValue();
                    int recommended = (int) (id % 40) + 1;

                    return new AuctionPriceResponse(
                            id,
                            row.get("display_name").toString(),
                            row.get("club_name").toString(),
                            row.get("fantasy_role").toString(),
                            teams,
                            budget,
                            defenseModifier,
                            Math.max(1, recommended - 2),
                            recommended,
                            recommended + 2,
                            BigDecimal.valueOf(recommended)
                                    .multiply(BigDecimal.valueOf(100))
                                    .divide(
                                            BigDecimal.valueOf(budget),
                                            2,
                                            RoundingMode.HALF_UP
                                    ),
                            BigDecimal.valueOf(100 - id % 50),
                            "PRICED",
                            "B51 test price"
                    );
                })
                .toList();
    }
}
