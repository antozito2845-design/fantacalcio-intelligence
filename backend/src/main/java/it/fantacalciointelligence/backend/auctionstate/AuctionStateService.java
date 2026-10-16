package it.fantacalciointelligence.backend.auctionstate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.auctionstate.AuctionStateDtos.CreateSessionRequest;
import it.fantacalciointelligence.backend.auctionstate.AuctionStateDtos.NominationRequest;
import it.fantacalciointelligence.backend.auctionstate.AuctionStateDtos.PlayerStateResponse;
import it.fantacalciointelligence.backend.auctionstate.AuctionStateDtos.PurchaseResponse;
import it.fantacalciointelligence.backend.auctionstate.AuctionStateDtos.SessionResponse;
import it.fantacalciointelligence.backend.auctionstate.AuctionStateDtos.StateResponse;
import it.fantacalciointelligence.backend.common.BadRequestException;
import it.fantacalciointelligence.backend.common.ConflictException;
import it.fantacalciointelligence.backend.common.ResourceNotFoundException;

@Service
public class AuctionStateService {
    private final JdbcClient jdbc;
    public AuctionStateService(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Transactional
    public SessionResponse create(CreateSessionRequest request) {
        return create(request, "legacy");
    }

    @Transactional
    public SessionResponse create(
            CreateSessionRequest request,
            String ownerUsername
    ) {

        if (ownerUsername == null
                || ownerUsername.isBlank()
                || ownerUsername.length() > 120) {
            throw new BadRequestException(
                    "Owner sessione asta non valido"
            );
        }

        if (request == null || request.squadId() == null || request.squadId() <= 0)
            throw new BadRequestException("squadId non valido");
        Map<String,Object> source = jdbc.sql("""
                SELECT squad.id squad_id, season.id season_id, season.code,
                       league.teams_count, squad.initial_budget total_budget,
                       league.defense_modifier
                FROM fantasy_squads squad
                JOIN league_configurations league ON league.id=squad.league_configuration_id
                JOIN seasons season ON season.id=league.season_id
                WHERE squad.id=:squadId
                """).param("squadId", request.squadId()).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Rosa non trovata"));
        Long id = jdbc.sql("""
                INSERT INTO auction_sessions(
                    squad_id,
                    season_id,
                    league_teams,
                    total_budget,
                    defense_modifier,
                    owner_username
                )
                VALUES(
                    :squad,
                    :season,
                    :teams,
                    :budget,
                    :modifier,
                    :owner
                )
                RETURNING id
                """).param("squad", number(source,"squad_id").longValue())
                .param("season", number(source,"season_id").longValue())
                .param("teams", number(source,"teams_count").intValue())
                .param("budget", number(source,"total_budget").intValue())
                .param("modifier", source.get("defense_modifier"))
                .param("owner", ownerUsername.trim())
                .query(Long.class)
                .single();
        event(id,"SESSION_CREATED",null,null,"{}");
        return find(id);
    }

    @Transactional
    public SessionResponse activate(Long id) {
        Map<String,Object> session = lock(id);
        requireStatus(session, AuctionSessionStatus.DRAFT);
        int initialized = jdbc.sql("""
                INSERT INTO auction_player_states(session_id,player_id,fantasy_role)
                SELECT :session, player.id, role.fantasy_role
                FROM auction_sessions auction
                JOIN players player ON player.active=TRUE
                JOIN player_registrations registration ON registration.player_id=player.id
                     AND registration.season_id=auction.season_id
                     AND registration.registration_status='ACTIVE'
                JOIN season_clubs season_club ON season_club.season_id=auction.season_id
                     AND season_club.club_id=registration.club_id
                JOIN player_roles role ON role.player_id=player.id
                     AND role.season_id=auction.season_id AND role.source='FANTACALCIO'
                     AND role.fantasy_role IN ('P','D','C','A')
                WHERE auction.id=:session
                ON CONFLICT(session_id,player_id) DO NOTHING
                """).param("session",id).update();
        if (initialized == 0) throw new ConflictException("Nessun player eleggibile per la sessione");
        jdbc.sql("""
                UPDATE auction_player_states state SET availability='ACQUIRED_BY_USER',
                    version=state.version+1,updated_at=CURRENT_TIMESTAMP
                FROM auction_sessions auction
                JOIN fantasy_squad_players roster ON roster.squad_id=auction.squad_id
                WHERE state.session_id=:session AND auction.id=:session
                  AND state.player_id=roster.player_id
                """).param("session",id).update();
        try {
            jdbc.sql("""
                    UPDATE auction_sessions SET status='ACTIVE',player_state_initialized=TRUE,
                        version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=:id
                    """).param("id",id).update();
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Esiste gia' una sessione ACTIVE");
        }
        event(id,"SESSION_ACTIVATED",null,null,"{\"initializedPlayers\":"+initialized+"}");
        return find(id);
    }

    @Transactional
    public SessionResponse nominate(Long id, NominationRequest request) {
        if (request == null || request.playerId() == null || request.playerId() <= 0
                || request.currentPrice() == null || request.currentPrice() < 1)
            throw new BadRequestException("Nomination non valida");
        Map<String,Object> session=lock(id); requireStatus(session,AuctionSessionStatus.ACTIVE);
        String availability=jdbc.sql("""
                SELECT availability FROM auction_player_states
                WHERE session_id=:session AND player_id=:player FOR UPDATE
                """).param("session",id).param("player",request.playerId()).query(String.class).optional()
                .orElseThrow(() -> new ResourceNotFoundException("Player non presente nella sessione"));
        if (!"AVAILABLE".equals(availability)) throw new ConflictException("Player non disponibile");
        jdbc.sql("""
                UPDATE auction_sessions SET current_player_id=:player,current_price=:price,
                    version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=:id
                """).param("player",request.playerId()).param("price",request.currentPrice())
                .param("id",id).update();
        event(id,"NOMINATION_SET",request.playerId(),null,
                "{\"currentPrice\":"+request.currentPrice()+"}");
        return find(id);
    }

    @Transactional
    public SessionResponse complete(Long id) {
        Map<String,Object> session=lock(id); requireStatus(session,AuctionSessionStatus.ACTIVE);
        jdbc.sql("""
                UPDATE auction_sessions SET status='COMPLETED',current_player_id=NULL,current_price=NULL,
                    version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=:id
                """).param("id",id).update();
        event(id,"SESSION_COMPLETED",null,null,"{}"); return find(id);
    }

    @Transactional(readOnly=true)
    public SessionResponse find(Long id) {
        return jdbc.sql(sessionSql()+" WHERE auction.id=:id").param("id",id)
                .query((rs,n)->session(rs)).optional()
                .orElseThrow(()->new ResourceNotFoundException("Sessione asta non trovata"));
    }

    @Transactional(readOnly=true)
    public StateResponse state(Long id) {
        SessionResponse session=find(id);
        List<PurchaseResponse> purchases=jdbc.sql("""
                SELECT id,session_id,player_id,buyer_type,paid_price,idempotency_key,created_at
                FROM auction_purchases WHERE session_id=:id ORDER BY id
                """).param("id",id).query((rs,n)->new PurchaseResponse(rs.getLong("id"),
                        rs.getLong("session_id"),rs.getLong("player_id"),
                        AuctionBuyerType.valueOf(rs.getString("buyer_type")),
                        (Integer)rs.getObject("paid_price"),rs.getString("idempotency_key"),
                        rs.getObject("created_at",OffsetDateTime.class))).list();
        List<PlayerStateResponse> players=jdbc.sql("""
                SELECT state.player_id,player.display_name,state.fantasy_role,state.availability
                FROM auction_player_states state JOIN players player ON player.id=state.player_id
                WHERE state.session_id=:id ORDER BY state.player_id
                """).param("id",id).query((rs,n)->new PlayerStateResponse(rs.getLong("player_id"),
                        rs.getString("display_name"),rs.getString("fantasy_role"),
                        AuctionPlayerAvailability.valueOf(rs.getString("availability")))).list();
        int spent=jdbc.sql("SELECT COALESCE(SUM(purchase_price),0) FROM fantasy_squad_players WHERE squad_id=:id")
                .param("id",session.squadId()).query(Integer.class).single();
        boolean complete=session.playerStateInitialized() && players.size()==eligibleCount(session.id());
        return new StateResponse(session,spent,session.totalBudget()-spent,purchases,players,complete);
    }

    Map<String,Object> lock(Long id) {
        return jdbc.sql("""
                SELECT id,squad_id,season_id,league_teams,total_budget,defense_modifier,
                       status,player_state_initialized,current_player_id,current_price,
                       version,created_at,updated_at
                FROM auction_sessions WHERE id=:id FOR UPDATE
                """).param("id",id)
                .query().listOfRows().stream().findFirst()
                .orElseThrow(()->new ResourceNotFoundException("Sessione asta non trovata"));
    }
    void requireStatus(Map<String,Object> row,AuctionSessionStatus status){
        if(!status.name().equals(row.get("status")))throw new ConflictException("Stato sessione non compatibile");
    }
    void event(Long session,String type,Long player,String key,String details){
        jdbc.sql("""
                INSERT INTO auction_events(session_id,event_type,player_id,idempotency_key,details)
                VALUES(:session,:type,:player,:key,CAST(:details AS jsonb))
                """).param("session",session).param("type",type).param("player",player)
                .param("key",key).param("details",details).update();
    }
    private int eligibleCount(Long id){return jdbc.sql("""
            SELECT COUNT(DISTINCT player.id) FROM auction_sessions auction
            JOIN players player ON player.active=TRUE
            JOIN player_registrations registration ON registration.player_id=player.id
                 AND registration.season_id=auction.season_id AND registration.registration_status='ACTIVE'
            JOIN season_clubs season_club ON season_club.season_id=auction.season_id
                 AND season_club.club_id=registration.club_id
            JOIN player_roles role ON role.player_id=player.id AND role.season_id=auction.season_id
                 AND role.source='FANTACALCIO' AND role.fantasy_role IN ('P','D','C','A')
            WHERE auction.id=:id
            """).param("id",id).query(Integer.class).single();}
    private String sessionSql(){return """
            SELECT auction.*,season.code FROM auction_sessions auction
            JOIN seasons season ON season.id=auction.season_id
            """;}
    private SessionResponse session(java.sql.ResultSet rs)throws java.sql.SQLException{return new SessionResponse(
            rs.getLong("id"),rs.getLong("squad_id"),rs.getString("code"),rs.getInt("league_teams"),
            rs.getInt("total_budget"),rs.getBoolean("defense_modifier"),
            AuctionSessionStatus.valueOf(rs.getString("status")),rs.getBoolean("player_state_initialized"),
            (Long)rs.getObject("current_player_id"),(Integer)rs.getObject("current_price"),rs.getLong("version"),
            rs.getObject("created_at",OffsetDateTime.class),rs.getObject("updated_at",OffsetDateTime.class));}
    private Number number(Map<String,Object> row,String key){return (Number)row.get(key);}
}
