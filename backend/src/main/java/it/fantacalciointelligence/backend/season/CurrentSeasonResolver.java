package it.fantacalciointelligence.backend.season;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component("currentSeasonResolver")
public class CurrentSeasonResolver {

    private final JdbcClient jdbcClient;

    public CurrentSeasonResolver(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public String resolve() {
        var seasons = jdbcClient.sql("""
                SELECT code FROM seasons
                WHERE current_season = TRUE
                ORDER BY id
                """).query(String.class).list();
        if (seasons.size() != 1) {
            throw new IllegalStateException(
                    "Deve esistere esattamente una stagione corrente; trovate: " + seasons.size()
            );
        }
        return seasons.getFirst();
    }
}
