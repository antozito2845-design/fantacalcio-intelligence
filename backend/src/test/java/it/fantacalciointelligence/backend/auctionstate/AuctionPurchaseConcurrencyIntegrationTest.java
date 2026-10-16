package it.fantacalciointelligence.backend.auctionstate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import it.fantacalciointelligence.backend.auctionstate.AuctionStateDtos.*;
import it.fantacalciointelligence.backend.common.ConflictException;

@SpringBootTest
class AuctionPurchaseConcurrencyIntegrationTest {
    @Autowired AuctionStateService state;
    @Autowired AuctionPurchaseService purchases;
    @Autowired JdbcClient jdbc;
    Long sessionId; Long playerId; Long squadId; List<Long> fixtureRolePlayers=List.of();

    @BeforeEach void setup(){cleanup();fixtureRolePlayers=B59TestFixture.addFantacalcioRoles(jdbc);squadId=B59TestFixture.createSquad(jdbc);
        sessionId=state.activate(state.create(new CreateSessionRequest(squadId)).id()).id();
        playerId=jdbc.sql("SELECT player_id FROM auction_player_states WHERE session_id=:s AND availability='AVAILABLE' ORDER BY player_id LIMIT 1").param("s",sessionId).query(Long.class).single();}
    @AfterEach void tearDown(){cleanup();if(squadId!=null)jdbc.sql("DELETE FROM fantasy_squads WHERE id=:s").param("s",squadId).update();
        if(!fixtureRolePlayers.isEmpty())jdbc.sql("DELETE FROM player_roles WHERE source='FANTACALCIO' AND player_id IN (:ids)").param("ids",fixtureRolePlayers).update();}

    @Test void realConcurrentUserVersusOtherAllowsExactlyOneWinner()throws Exception{
        var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        var executor=Executors.newFixedThreadPool(2);List<Future<Object>> futures=new ArrayList<>();
        futures.add(executor.submit(()->run(ready,start,new PurchaseRequest(playerId,AuctionBuyerType.USER,1,"race-user"))));
        futures.add(executor.submit(()->run(ready,start,new PurchaseRequest(playerId,AuctionBuyerType.OTHER,null,"race-other"))));
        ready.await();start.countDown();int success=0,conflict=0;
        for(Future<Object> future:futures){Object value=future.get();if(value instanceof PurchaseResponse)success++;if(value instanceof ConflictException)conflict++;}
        executor.shutdown();
        assertThat(success).isEqualTo(1);assertThat(conflict).isEqualTo(1);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM auction_purchases WHERE session_id=:s AND player_id=:p")
                .param("s",sessionId).param("p",playerId).query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM fantasy_squad_players WHERE squad_id=:s AND player_id=:p")
                .param("s",squadId).param("p",playerId).query(Integer.class).single()).isLessThanOrEqualTo(1);
        assertThat(state.state(sessionId).remainingBudget()).isNotNegative();
    }
    private Object run(CountDownLatch ready,CountDownLatch start,PurchaseRequest request){try{ready.countDown();start.await();return purchases.purchase(sessionId,request);}catch(ConflictException ex){return ex;}catch(Exception ex){return ex;}}
    private void cleanup(){jdbc.sql("DELETE FROM auction_events").update();jdbc.sql("DELETE FROM auction_purchases").update();jdbc.sql("DELETE FROM auction_player_states").update();jdbc.sql("DELETE FROM auction_sessions").update();}
}
