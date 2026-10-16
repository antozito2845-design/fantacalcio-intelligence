package it.fantacalciointelligence.backend.auctionstate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.auctionstate.AuctionStateDtos.*;
import it.fantacalciointelligence.backend.liveauction.LiveAuctionDecisionRequest;
import it.fantacalciointelligence.backend.liveauction.LiveAuctionDecisionResponse;
import it.fantacalciointelligence.backend.liveauction.LiveAuctionDecisionService;

@SpringBootTest
@Transactional
class AuctionContextAdapterIntegrationTest {
    @Autowired AuctionStateService state;
    @Autowired AuctionPurchaseService purchases;
    @Autowired AuctionContextAdapter adapter;
    @Autowired JdbcClient jdbc;
    @MockitoBean LiveAuctionDecisionService b58;
    Long sandboxSquad;
    @BeforeEach void fixture(){B59TestFixture.addFantacalcioRoles(jdbc);sandboxSquad=B59TestFixture.createSquad(jdbc);}

    @Test
    void buildsCertifiedB58ContextWithExactRosterUnavailableAndCurrentPrice(){
        var session=state.activate(state.create(new CreateSessionRequest(squadId())).id());
        Long other=available(session.id());
        purchases.purchase(session.id(),new PurchaseRequest(other,AuctionBuyerType.OTHER,null,"other"));
        Long current=available(session.id());
        state.nominate(session.id(),new NominationRequest(current,7));
        when(b58.decide(any())).thenReturn(org.mockito.Mockito.mock(LiveAuctionDecisionResponse.class));

        adapter.decide(session.id(),new SessionDecisionRequest(9,null,null,10));

        ArgumentCaptor<LiveAuctionDecisionRequest> captor=ArgumentCaptor.forClass(LiveAuctionDecisionRequest.class);
        verify(b58).decide(captor.capture());
        var context=captor.getValue();
        assertThat(context.currentPlayerId()).isEqualTo(current);
        assertThat(context.currentPrice()).isEqualTo(7);
        assertThat(context.proposedBid()).isEqualTo(9);
        assertThat(context.availabilityComplete()).isTrue();
        assertThat(context.unavailablePlayerIds()).containsExactly(other);
        int spent=context.playersAlreadyBought().stream().mapToInt(p->p.pricePaid()).sum();
        assertThat(context.remainingBudget()).isEqualTo(context.totalBudget()-spent);
    }
    private Long squadId(){return sandboxSquad;}
    private Long available(Long session){return jdbc.sql("SELECT player_id FROM auction_player_states WHERE session_id=:s AND availability='AVAILABLE' ORDER BY player_id LIMIT 1").param("s",session).query(Long.class).single();}
}
