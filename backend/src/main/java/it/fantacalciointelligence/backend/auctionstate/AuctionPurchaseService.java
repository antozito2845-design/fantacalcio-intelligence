package it.fantacalciointelligence.backend.auctionstate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.auctionstate.AuctionStateDtos.PurchaseRequest;
import it.fantacalciointelligence.backend.auctionstate.AuctionStateDtos.PurchaseResponse;
import it.fantacalciointelligence.backend.common.BadRequestException;
import it.fantacalciointelligence.backend.common.ConflictException;
import it.fantacalciointelligence.backend.common.ResourceNotFoundException;
import it.fantacalciointelligence.backend.liveauction.LiveAuctionBudgetGuard;
import it.fantacalciointelligence.backend.squadconstruction.BoughtPlayerRequest;
import it.fantacalciointelligence.backend.squadconstruction.DynamicBudgetEngine;
import it.fantacalciointelligence.backend.squadconstruction.SquadConstructionRequest;

@Service
public class AuctionPurchaseService {
    private final JdbcClient jdbc;
    private final AuctionStateService stateService;
    private final DynamicBudgetEngine budgetEngine;
    private final LiveAuctionBudgetGuard budgetGuard;

    public AuctionPurchaseService(JdbcClient jdbc,AuctionStateService stateService,
            DynamicBudgetEngine budgetEngine,LiveAuctionBudgetGuard budgetGuard){
        this.jdbc=jdbc;this.stateService=stateService;this.budgetEngine=budgetEngine;this.budgetGuard=budgetGuard;
    }

    @Transactional
    public PurchaseResponse purchase(Long sessionId,PurchaseRequest request){
        validate(request);
        Map<String,Object> session=stateService.lock(sessionId);
        stateService.requireStatus(session,AuctionSessionStatus.ACTIVE);
        PurchaseResponse previous=findByKey(sessionId,request.idempotencyKey());
        if(previous!=null){
            if(previous.playerId().equals(request.playerId())&&previous.buyerType()==request.buyerType()
                    &&Objects.equals(previous.paidPrice(),request.paidPrice()))return previous;
            throw new ConflictException("Idempotency key gia' usata con payload diverso");
        }
        Map<String,Object> playerState=jdbc.sql("""
                SELECT id,session_id,player_id,fantasy_role,availability,version,created_at,updated_at
                FROM auction_player_states WHERE session_id=:session AND player_id=:player FOR UPDATE
                """).param("session",sessionId).param("player",request.playerId()).query().listOfRows().stream()
                .findFirst().orElseThrow(()->new ResourceNotFoundException("Player non presente nella sessione"));
        if(!"AVAILABLE".equals(playerState.get("availability")))throw new ConflictException("Player gia' non disponibile");
        String role=playerState.get("fantasy_role").toString();
        boolean eligible=jdbc.sql("""
                SELECT EXISTS(SELECT 1 FROM auction_sessions auction
                JOIN players player ON player.id=:player AND player.active=TRUE
                JOIN player_registrations registration ON registration.player_id=player.id
                     AND registration.season_id=auction.season_id AND registration.registration_status='ACTIVE'
                JOIN season_clubs season_club ON season_club.season_id=auction.season_id
                     AND season_club.club_id=registration.club_id
                JOIN player_roles role ON role.player_id=player.id AND role.season_id=auction.season_id
                     AND role.source='FANTACALCIO' AND role.fantasy_role=:role
                WHERE auction.id=:session)
                """).param("player",request.playerId()).param("role",role)
                .param("session",sessionId).query(Boolean.class).single();
        if(!eligible)throw new ConflictException("Player non piu' eleggibile nella sessione");
        if(request.buyerType()==AuctionBuyerType.USER) addUserPlayer(session,request,role);
        try{
            Long id=jdbc.sql("""
                    INSERT INTO auction_purchases(session_id,player_id,buyer_type,paid_price,idempotency_key)
                    VALUES(:session,:player,:buyer,:price,:key) RETURNING id
                    """).param("session",sessionId).param("player",request.playerId())
                    .param("buyer",request.buyerType().name()).param("price",request.paidPrice())
                    .param("key",request.idempotencyKey().trim()).query(Long.class).single();
            String availability=request.buyerType()==AuctionBuyerType.USER?"ACQUIRED_BY_USER":"ACQUIRED_BY_OTHER";
            jdbc.sql("""
                    UPDATE auction_player_states SET availability=:availability,version=version+1,
                        updated_at=CURRENT_TIMESTAMP WHERE session_id=:session AND player_id=:player
                    """).param("availability",availability).param("session",sessionId)
                    .param("player",request.playerId()).update();
            jdbc.sql("""
                    UPDATE auction_sessions SET current_player_id=NULL,current_price=NULL,
                        version=version+1,updated_at=CURRENT_TIMESTAMP
                    WHERE id=:session AND current_player_id=:player
                    """).param("session",sessionId).param("player",request.playerId()).update();
            stateService.event(sessionId,"PURCHASE_"+request.buyerType().name(),request.playerId(),
                    request.idempotencyKey().trim(),request.paidPrice()==null?"{}":"{\"paidPrice\":"+request.paidPrice()+"}");
            return find(id);
        }catch(DataIntegrityViolationException ex){throw new ConflictException("Purchase concorrente o duplicato");}
    }

    private void addUserPlayer(Map<String,Object> session,PurchaseRequest request,String role){
        Long squad=((Number)session.get("squad_id")).longValue();
        jdbc.sql("SELECT id FROM fantasy_squads WHERE id=:id FOR UPDATE").param("id",squad).query(Long.class).single();
        String season=jdbc.sql("SELECT code FROM seasons WHERE id=:id")
                .param("id",session.get("season_id")).query(String.class).single();
        List<BoughtPlayerRequest> bought=jdbc.sql("""
                SELECT player_id,fantasy_role,purchase_price FROM fantasy_squad_players
                WHERE squad_id=:squad ORDER BY id
                """).param("squad",squad).query((rs,n)->new BoughtPlayerRequest(rs.getLong("player_id"),
                        rs.getString("fantasy_role"),rs.getInt("purchase_price"))).list();
        int spent=bought.stream().mapToInt(BoughtPlayerRequest::pricePaid).sum();
        int total=((Number)session.get("total_budget")).intValue();
        int remaining=total-spent;
        Map<String,Integer> slots=jdbc.sql("""
                SELECT league.goalkeepers_count p,league.defenders_count d,
                       league.midfielders_count c,league.forwards_count a
                FROM fantasy_squads squad JOIN league_configurations league
                     ON league.id=squad.league_configuration_id WHERE squad.id=:squad
                """).param("squad",squad).query((rs,n)->Map.of("P",rs.getInt("p"),"D",rs.getInt("d"),
                        "C",rs.getInt("c"),"A",rs.getInt("a"))).single();
        var budget=budgetEngine.calculate(new SquadConstructionRequest(season,
                ((Number)session.get("league_teams")).intValue(),total,remaining,
                (Boolean)session.get("defense_modifier"),role,slots,bought,1));
        if(!budgetGuard.evaluate(request.paidPrice(),remaining,budget).sustainable())
            throw new ConflictException("Purchase non sostenibile per budget/slot B54");
        try{
            jdbc.sql("""
                    INSERT INTO fantasy_squad_players(squad_id,player_id,season_id,fantasy_role,purchase_price)
                    VALUES(:squad,:player,:season,:role,:price)
                    """).param("squad",squad).param("player",request.playerId())
                    .param("season",session.get("season_id")).param("role",role)
                    .param("price",request.paidPrice()).update();
        }catch(DataIntegrityViolationException ex){throw new ConflictException("Player gia' presente nella rosa");}
    }

    private void validate(PurchaseRequest r){
        if(r==null||r.playerId()==null||r.playerId()<=0||r.buyerType()==null
                ||r.idempotencyKey()==null||r.idempotencyKey().isBlank()||r.idempotencyKey().length()>120)
            throw new BadRequestException("Purchase payload non valido");
        if(r.buyerType()==AuctionBuyerType.USER&&(r.paidPrice()==null||r.paidPrice()<1))
            throw new BadRequestException("paidPrice USER deve essere almeno 1");
        if(r.buyerType()==AuctionBuyerType.OTHER&&r.paidPrice()!=null&&r.paidPrice()<1)
            throw new BadRequestException("paidPrice OTHER, se noto, deve essere almeno 1");
    }
    private PurchaseResponse findByKey(Long session,String key){return jdbc.sql("""
            SELECT id,session_id,player_id,buyer_type,paid_price,idempotency_key,created_at
            FROM auction_purchases WHERE session_id=:session AND idempotency_key=:key
            """).param("session",session).param("key",key.trim()).query((rs,n)->map(rs)).optional().orElse(null);}
    private PurchaseResponse find(Long id){return jdbc.sql("""
            SELECT id,session_id,player_id,buyer_type,paid_price,idempotency_key,created_at
            FROM auction_purchases WHERE id=:id
            """).param("id",id).query((rs,n)->map(rs)).single();}
    private PurchaseResponse map(java.sql.ResultSet rs)throws java.sql.SQLException{return new PurchaseResponse(
            rs.getLong("id"),rs.getLong("session_id"),rs.getLong("player_id"),
            AuctionBuyerType.valueOf(rs.getString("buyer_type")),(Integer)rs.getObject("paid_price"),
            rs.getString("idempotency_key"),rs.getObject("created_at",OffsetDateTime.class));}
}
