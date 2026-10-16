package it.fantacalciointelligence.backend.auctionstate;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.auctionstate.AuctionStateDtos.SessionDecisionRequest;
import it.fantacalciointelligence.backend.common.BadRequestException;
import it.fantacalciointelligence.backend.common.ConflictException;
import it.fantacalciointelligence.backend.liveauction.LiveAuctionDecisionRequest;
import it.fantacalciointelligence.backend.liveauction.LiveAuctionDecisionResponse;
import it.fantacalciointelligence.backend.liveauction.LiveAuctionDecisionService;
import it.fantacalciointelligence.backend.squadconstruction.BoughtPlayerRequest;

@Service
public class AuctionContextAdapter {
    private final JdbcClient jdbc;
    private final AuctionStateService stateService;
    private final LiveAuctionDecisionService decisionService;
    public AuctionContextAdapter(JdbcClient jdbc,AuctionStateService stateService,
            LiveAuctionDecisionService decisionService){this.jdbc=jdbc;this.stateService=stateService;this.decisionService=decisionService;}

    @Transactional(readOnly=true)
    public LiveAuctionDecisionResponse decide(Long sessionId,SessionDecisionRequest request){
        if(request==null||request.proposedBid()==null||request.proposedBid()<1)
            throw new BadRequestException("proposedBid deve essere almeno 1");
        var state=stateService.state(sessionId);
        var session=state.session();
        if(session.status()!=AuctionSessionStatus.ACTIVE)throw new ConflictException("Sessione non ACTIVE");
        if(session.currentPlayerId()==null||session.currentPrice()==null)
            throw new ConflictException("Nessuna nomination corrente");
        String role=state.players().stream().filter(p->p.playerId().equals(session.currentPlayerId()))
                .map(AuctionStateDtos.PlayerStateResponse::role).findFirst()
                .orElseThrow(()->new ConflictException("Nomination fuori dallo stato sessione"));
        List<BoughtPlayerRequest> bought=jdbc.sql("""
                SELECT player_id,fantasy_role,purchase_price FROM fantasy_squad_players
                WHERE squad_id=:squad ORDER BY id
                """).param("squad",session.squadId()).query((rs,n)->new BoughtPlayerRequest(
                        rs.getLong("player_id"),rs.getString("fantasy_role"),rs.getInt("purchase_price"))).list();
        List<Long> unavailable=state.players().stream()
                .filter(p->p.availability()==AuctionPlayerAvailability.ACQUIRED_BY_OTHER
                        ||p.availability()==AuctionPlayerAvailability.EXCLUDED)
                .map(AuctionStateDtos.PlayerStateResponse::playerId).toList();
        Map<String,Integer> slots=jdbc.sql("""
                SELECT league.goalkeepers_count p,league.defenders_count d,
                       league.midfielders_count c,league.forwards_count a
                FROM fantasy_squads squad JOIN league_configurations league
                     ON league.id=squad.league_configuration_id WHERE squad.id=:squad
                """).param("squad",session.squadId()).query((rs,n)->Map.of("P",rs.getInt("p"),
                        "D",rs.getInt("d"),"C",rs.getInt("c"),"A",rs.getInt("a"))).single();
        return decisionService.decide(new LiveAuctionDecisionRequest(session.season(),session.leagueTeams(),
                session.totalBudget(),state.remainingBudget(),session.defenseModifier(),role,
                session.currentPlayerId(),session.currentPrice(),request.proposedBid(),null,slots,bought,
                request.currentMatchday(),request.horizon(),request.alternativeLimit(),List.of(),
                unavailable,state.availabilityComplete()));
    }
}
