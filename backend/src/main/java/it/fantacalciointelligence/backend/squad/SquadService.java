package it.fantacalciointelligence.backend.squad;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.common.ConflictException;
import it.fantacalciointelligence.backend.common.ResourceNotFoundException;

@Service
public class SquadService {

    private final JdbcClient jdbcClient;

    public SquadService(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional(readOnly = true)
    public SquadResponse findActiveSquad() {
        Map<String, Object> squad =
                findActiveSquadRow();

        return buildSquadResponse(squad);
    }

    @Transactional(readOnly = true)
    public SquadResponse findSquad(
            Long squadId
    ) {
        return buildSquadResponse(
                findSquadRow(squadId)
        );
    }

    @Transactional(readOnly = true)
    public List<SquadSummaryResponse> findSquads(
            String season,
            String username,
            boolean administrator
    ) {
        return jdbcClient.sql("""
                SELECT
                    squad.id,
                    squad.name,
                    squad.initial_budget,
                    squad.active,

                    season.code AS season,

                    league.goalkeepers_count
                        + league.defenders_count
                        + league.midfielders_count
                        + league.forwards_count
                            AS maximum_players,

                    COUNT(squad_player.id)
                        AS total_players,

                    COALESCE(
                        SUM(squad_player.purchase_price),
                        0
                    ) AS spent_budget

                FROM fantasy_squads squad

                JOIN league_configurations league
                    ON league.id =
                        squad.league_configuration_id

                JOIN seasons season
                    ON season.id =
                        league.season_id

                LEFT JOIN fantasy_squad_players squad_player
                    ON squad_player.squad_id =
                        squad.id

                WHERE season.code = :season
                  AND (:administrator = TRUE OR LOWER(squad.owner_username) = LOWER(:username))

                GROUP BY
                    squad.id,
                    squad.name,
                    squad.initial_budget,
                    squad.active,
                    season.code,
                    league.goalkeepers_count,
                    league.defenders_count,
                    league.midfielders_count,
                    league.forwards_count

                ORDER BY
                    squad.active DESC,
                    squad.id
                """)
                .param("season", season)
                .param("username", username)
                .param("administrator", administrator)
                .query((rs, rowNumber) -> {
                    int initialBudget =
                            rs.getInt(
                                    "initial_budget"
                            );

                    int spentBudget =
                            rs.getInt(
                                    "spent_budget"
                            );

                    return new SquadSummaryResponse(
                            rs.getLong("id"),
                            rs.getString("name"),
                            rs.getString("season"),
                            initialBudget,
                            spentBudget,
                            initialBudget - spentBudget,
                            rs.getInt(
                                    "total_players"
                            ),
                            rs.getInt(
                                    "maximum_players"
                            ),
                            rs.getBoolean("active")
                    );
                })
                .list();
    }

    /** Internal compatibility path; HTTP callers always use the ownership-aware overload. */
    @Transactional(readOnly = true)
    public List<SquadSummaryResponse> findSquads(String season) {
        return findSquads(season, "", true);
    }

    @Transactional
    public SquadResponse createSquad(CreateSquadRequest request, String ownerUsername) {
        Map<String, Object> league = jdbcClient.sql("""
                SELECT id, total_budget
                FROM league_configurations
                WHERE id = :leagueId
                """)
                .param("leagueId", request.leagueConfigurationId())
                .query().listOfRows().stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Configurazione lega non trovata: " + request.leagueConfigurationId()
                ));

        if (request.active()) {
            jdbcClient.sql("""
                    UPDATE fantasy_squads
                    SET active = FALSE, updated_at = CURRENT_TIMESTAMP
                    WHERE active = TRUE AND LOWER(owner_username) = LOWER(:owner)
                    """)
                    .param("owner", ownerUsername)
                    .update();
        }

        Long squadId = jdbcClient.sql("""
                INSERT INTO fantasy_squads (
                    league_configuration_id, name, initial_budget, active, owner_username
                ) VALUES (:leagueId, :name, :budget, :active, :owner)
                RETURNING id
                """)
                .param("leagueId", request.leagueConfigurationId())
                .param("name", request.name().trim())
                .param("budget", number(league, "total_budget").intValue())
                .param("active", request.active())
                .param("owner", ownerUsername)
                .query(Long.class).single();
        return findSquad(squadId);
    }

    @Transactional(readOnly = true)
    public SquadResponse findActiveSquad(String username, boolean administrator) {
        return buildSquadResponse(findActiveSquadRow(username, administrator));
    }

    @Transactional(readOnly = true)
    public SquadResponse findSquad(Long squadId, String username, boolean administrator) {
        assertAccess(squadId, username, administrator);
        return findSquad(squadId);
    }

    @Transactional
    public SquadResponse addPlayer(
            AddSquadPlayerRequest request
    ) {
        Map<String, Object> squad =
                findActiveSquadRow();

        Long squadId =
                number(squad, "id").longValue();

        return addPlayer(
                squadId,
                request
        );
    }

    @Transactional
    public SquadResponse addPlayer(
            Long squadId,
            AddSquadPlayerRequest request
    ) {
        lockSquad(squadId);
        Map<String, Object> squad =
                findSquadRow(squadId);

        Long seasonId =
                number(
                        squad,
                        "season_id"
                ).longValue();

        int initialBudget =
                number(
                        squad,
                        "initial_budget"
                ).intValue();

        ensurePlayerNotAlreadyPresent(
                squadId,
                request.playerId()
        );

        Map<String, Object> player =
                findEligiblePlayer(
                        request.playerId(),
                        seasonId
                );

        String role =
                player.get(
                        "fantasy_role"
                ).toString();

        ensureRoleHasSpace(
                squad,
                squadId,
                role
        );

        int remainingBudget =
                initialBudget
                        - calculateSpentBudget(
                                squadId
                        );

        if (
                request.purchasePrice()
                        > remainingBudget
        ) {
            throw new ConflictException(
                    "Budget insufficiente"
            );
        }

        int emptySlotsAfterPurchase =
                calculateRemainingSlots(
                        squad
                ) - 1;

        int budgetAfterPurchase =
                remainingBudget
                        - request.purchasePrice();

        if (
                budgetAfterPurchase
                        < emptySlotsAfterPurchase
        ) {
            throw new ConflictException(
                    "Devi conservare almeno un credito "
                            + "per ogni posto ancora libero"
            );
        }

        jdbcClient.sql("""
                INSERT INTO fantasy_squad_players (
                    squad_id,
                    player_id,
                    season_id,
                    fantasy_role,
                    purchase_price
                )
                VALUES (
                    :squadId,
                    :playerId,
                    :seasonId,
                    :role,
                    :purchasePrice
                )
                """)
                .param("squadId", squadId)
                .param(
                        "playerId",
                        request.playerId()
                )
                .param("seasonId", seasonId)
                .param("role", role)
                .param(
                        "purchasePrice",
                        request.purchasePrice()
                )
                .update();

        return findSquad(squadId);
    }

    @Transactional
    public SquadResponse removePlayer(
            Long playerId
    ) {
        Map<String, Object> squad =
                findActiveSquadRow();

        Long squadId =
                number(squad, "id")
                        .longValue();

        return removePlayer(
                squadId,
                playerId
        );
    }

    @Transactional
    public SquadResponse removePlayer(
            Long squadId,
            Long playerId
    ) {
        lockSquad(squadId);
        findSquadRow(squadId);

        int deleted =
                jdbcClient.sql("""
                        DELETE FROM fantasy_squad_players
                        WHERE squad_id = :squadId
                          AND player_id = :playerId
                        """)
                        .param(
                                "squadId",
                                squadId
                        )
                        .param(
                                "playerId",
                                playerId
                        )
                        .update();

        if (deleted == 0) {
            throw new ResourceNotFoundException(
                    "Giocatore non presente nella rosa"
            );
        }

        return findSquad(squadId);
    }

    @Transactional
    public SquadResponse updatePlayerPrice(
            Long squadId,
            Long playerId,
            UpdateSquadPlayerPriceRequest request
    ) {
        lockSquad(squadId);
        Map<String, Object> squad = findSquadRow(squadId);

        int currentPrice = jdbcClient.sql("""
                SELECT purchase_price
                FROM fantasy_squad_players
                WHERE squad_id = :squadId
                  AND player_id = :playerId
                """)
                .param("squadId", squadId)
                .param("playerId", playerId)
                .query(Integer.class)
                .optional()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Giocatore non presente nella rosa"
                ));

        int initialBudget = number(squad, "initial_budget").intValue();
        int spentWithoutPlayer = calculateSpentBudget(squadId) - currentPrice;
        int budgetAfterUpdate = initialBudget - spentWithoutPlayer - request.purchasePrice();

        if (budgetAfterUpdate < 0) {
            throw new ConflictException("Budget insufficiente");
        }

        int emptySlots = calculateRemainingSlots(squad);
        if (budgetAfterUpdate < emptySlots) {
            throw new ConflictException(
                    "Devi conservare almeno un credito per ogni posto ancora libero"
            );
        }

        jdbcClient.sql("""
                UPDATE fantasy_squad_players
                SET purchase_price = :purchasePrice
                WHERE squad_id = :squadId
                  AND player_id = :playerId
                """)
                .param("purchasePrice", request.purchasePrice())
                .param("squadId", squadId)
                .param("playerId", playerId)
                .update();

        return findSquad(squadId);
    }

    private SquadResponse buildSquadResponse(
            Map<String, Object> squad
    ) {
        Long squadId =
                number(squad, "id")
                        .longValue();

        int initialBudget =
                number(
                        squad,
                        "initial_budget"
                ).intValue();

        List<SquadPlayerResponse> players =
                findSquadPlayers(squadId);

        int spentBudget =
                players.stream()
                        .mapToInt(
                                SquadPlayerResponse::
                                        purchasePrice
                        )
                        .sum();

        int maxGoalkeepers =
                number(
                        squad,
                        "goalkeepers_count"
                ).intValue();

        int maxDefenders =
                number(
                        squad,
                        "defenders_count"
                ).intValue();

        int maxMidfielders =
                number(
                        squad,
                        "midfielders_count"
                ).intValue();

        int maxForwards =
                number(
                        squad,
                        "forwards_count"
                ).intValue();

        Map<String, SquadRoleSummaryResponse> roles =
                new LinkedHashMap<>();

        roles.put(
                "P",
                roleSummary(
                        countRole(players, "P"),
                        maxGoalkeepers
                )
        );

        roles.put(
                "D",
                roleSummary(
                        countRole(players, "D"),
                        maxDefenders
                )
        );

        roles.put(
                "C",
                roleSummary(
                        countRole(players, "C"),
                        maxMidfielders
                )
        );

        roles.put(
                "A",
                roleSummary(
                        countRole(players, "A"),
                        maxForwards
                )
        );

        int maximumPlayers =
                maxGoalkeepers
                        + maxDefenders
                        + maxMidfielders
                        + maxForwards;

        return new SquadResponse(
                squadId,
                squad.get("name").toString(),
                squad.get("season").toString(),
                initialBudget,
                spentBudget,
                initialBudget - spentBudget,
                players.size(),
                maximumPlayers,
                roles,
                players
        );
    }

    private List<SquadPlayerResponse> findSquadPlayers(
            Long squadId
    ) {
        return jdbcClient.sql("""
                SELECT
                    squad_player.id,
                    player.id AS player_id,
                    player.display_name,
                    club.name AS club_name,
                    squad_player.fantasy_role,
                    squad_player.purchase_price

                FROM fantasy_squad_players squad_player

                JOIN players player
                    ON player.id =
                        squad_player.player_id

                JOIN player_registrations registration
                    ON registration.player_id =
                        player.id
                    AND registration.season_id =
                        squad_player.season_id

                JOIN clubs club
                    ON club.id =
                        registration.club_id

                WHERE squad_player.squad_id =
                        :squadId

                ORDER BY
                    CASE squad_player.fantasy_role
                        WHEN 'P' THEN 1
                        WHEN 'D' THEN 2
                        WHEN 'C' THEN 3
                        WHEN 'A' THEN 4
                        ELSE 5
                    END,
                    player.display_name
                """)
                .param("squadId", squadId)
                .query((rs, rowNumber) ->
                        new SquadPlayerResponse(
                                rs.getLong("id"),
                                rs.getLong(
                                        "player_id"
                                ),
                                rs.getString(
                                        "display_name"
                                ),
                                rs.getString(
                                        "club_name"
                                ),
                                rs.getString(
                                        "fantasy_role"
                                ),
                                rs.getInt(
                                        "purchase_price"
                                )
                        )
                )
                .list();
    }

    private Map<String, Object> findEligiblePlayer(
            Long playerId,
            Long seasonId
    ) {
        return jdbcClient.sql("""
                SELECT DISTINCT
                    player.id,
                    player.display_name,
                    role.fantasy_role

                FROM players player

                JOIN player_roles role
                    ON role.player_id =
                        player.id
                    AND role.season_id =
                        :seasonId
                    AND role.source =
                        'FANTACALCIO'

                JOIN player_registrations registration
                    ON registration.player_id =
                        player.id
                    AND registration.season_id =
                        :seasonId

                JOIN player_reconciliation_results reconciliation
                    ON reconciliation.player_id =
                        player.id
                    AND reconciliation.season_id =
                        :seasonId

                WHERE player.id = :playerId
                  AND player.active = TRUE
                  AND registration.registration_status =
                        'ACTIVE'
                """)
                .param(
                        "seasonId",
                        seasonId
                )
                .param(
                        "playerId",
                        playerId
                )
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Giocatore non disponibile "
                                        + "per la stagione della rosa"
                        )
                );
    }

    private void ensurePlayerNotAlreadyPresent(
            Long squadId,
            Long playerId
    ) {
        boolean alreadyPresent =
                jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM fantasy_squad_players
                        WHERE squad_id = :squadId
                          AND player_id = :playerId
                        """)
                        .param(
                                "squadId",
                                squadId
                        )
                        .param(
                                "playerId",
                                playerId
                        )
                        .query(Integer.class)
                        .single() > 0;

        if (alreadyPresent) {
            throw new ConflictException(
                    "Giocatore già presente nella rosa"
            );
        }
    }

    private void ensureRoleHasSpace(
            Map<String, Object> squad,
            Long squadId,
            String role
    ) {
        int maximum =
                switch (role) {
                    case "P" ->
                            number(
                                    squad,
                                    "goalkeepers_count"
                            ).intValue();

                    case "D" ->
                            number(
                                    squad,
                                    "defenders_count"
                            ).intValue();

                    case "C" ->
                            number(
                                    squad,
                                    "midfielders_count"
                            ).intValue();

                    case "A" ->
                            number(
                                    squad,
                                    "forwards_count"
                            ).intValue();

                    default ->
                            throw new ConflictException(
                                    "Ruolo non valido"
                            );
                };

        int current =
                jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM fantasy_squad_players
                        WHERE squad_id = :squadId
                          AND fantasy_role = :role
                        """)
                        .param(
                                "squadId",
                                squadId
                        )
                        .param("role", role)
                        .query(Integer.class)
                        .single();

        if (current >= maximum) {
            throw new ConflictException(
                    "Limite giocatori raggiunto "
                            + "per il ruolo "
                            + role
            );
        }
    }

    private int calculateSpentBudget(
            Long squadId
    ) {
        return jdbcClient.sql("""
                SELECT COALESCE(
                    SUM(purchase_price),
                    0
                )
                FROM fantasy_squad_players
                WHERE squad_id = :squadId
                """)
                .param("squadId", squadId)
                .query(Integer.class)
                .single();
    }

    private Map<String, Object> findActiveSquadRow() {
        return jdbcClient.sql(
                baseSquadQuery()
                        + """
                        WHERE squad.active = TRUE
                        ORDER BY squad.id DESC
                        LIMIT 1
                        """
                )
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Rosa attiva non trovata"
                        )
                );
    }

    private Map<String, Object> findActiveSquadRow(String username, boolean administrator) {
        return jdbcClient.sql(baseSquadQuery() + """
                WHERE squad.active = TRUE
                  AND (:administrator = TRUE OR LOWER(squad.owner_username) = LOWER(:username))
                ORDER BY squad.id DESC
                LIMIT 1
                """)
                .param("username", username)
                .param("administrator", administrator)
                .query().listOfRows().stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Rosa attiva non trovata"));
    }

    @Transactional(readOnly = true)
    public void assertAccess(Long squadId, String username, boolean administrator) {
        if (administrator) {
            findSquadRow(squadId);
            return;
        }
        boolean allowed = jdbcClient.sql("""
                SELECT COUNT(*) FROM fantasy_squads
                WHERE id = :squadId AND LOWER(owner_username) = LOWER(:username)
                """)
                .param("squadId", squadId)
                .param("username", username)
                .query(Integer.class).single() > 0;
        if (!allowed) {
            throw new ResourceNotFoundException("Rosa non trovata: " + squadId);
        }
    }

    private void lockSquad(Long squadId) {
        jdbcClient.sql("SELECT id FROM fantasy_squads WHERE id = :squadId FOR UPDATE")
                .param("squadId", squadId)
                .query(Long.class)
                .optional()
                .orElseThrow(() -> new ResourceNotFoundException("Rosa non trovata: " + squadId));
    }

    private Map<String, Object> findSquadRow(
            Long squadId
    ) {
        return jdbcClient.sql(
                baseSquadQuery()
                        + """
                        WHERE squad.id = :squadId
                        """
                )
                .param("squadId", squadId)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Rosa non trovata: "
                                        + squadId
                        )
                );
    }

    private String baseSquadQuery() {
        return """
                SELECT
                    squad.id,
                    squad.name,
                    squad.initial_budget,
                    squad.active,

                    league.goalkeepers_count,
                    league.defenders_count,
                    league.midfielders_count,
                    league.forwards_count,

                    season.id AS season_id,
                    season.code AS season

                FROM fantasy_squads squad

                JOIN league_configurations league
                    ON league.id =
                        squad.league_configuration_id

                JOIN seasons season
                    ON season.id =
                        league.season_id
                """;
    }

    private int calculateRemainingSlots(
            Map<String, Object> squad
    ) {
        Long squadId =
                number(squad, "id")
                        .longValue();

        int maximumPlayers =
                number(
                        squad,
                        "goalkeepers_count"
                ).intValue()
                + number(
                        squad,
                        "defenders_count"
                ).intValue()
                + number(
                        squad,
                        "midfielders_count"
                ).intValue()
                + number(
                        squad,
                        "forwards_count"
                ).intValue();

        int currentPlayers =
                jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM fantasy_squad_players
                        WHERE squad_id = :squadId
                        """)
                        .param(
                                "squadId",
                                squadId
                        )
                        .query(Integer.class)
                        .single();

        return maximumPlayers
                - currentPlayers;
    }

    private int countRole(
            List<SquadPlayerResponse> players,
            String role
    ) {
        return (int) players.stream()
                .filter(player ->
                        role.equals(
                                player.role()
                        )
                )
                .count();
    }

    private SquadRoleSummaryResponse roleSummary(
            int current,
            int maximum
    ) {
        return new SquadRoleSummaryResponse(
                current,
                maximum,
                Math.max(
                        0,
                        maximum - current
                )
        );
    }

    private Number number(
            Map<String, Object> row,
            String key
    ) {
        Object value = row.get(key);

        if (value instanceof Number number) {
            return number;
        }

        throw new IllegalStateException(
                "Valore numerico mancante: "
                        + key
        );
    }
}
