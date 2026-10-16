package it.fantacalciointelligence.backend.auctionstate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.auctionstate.AuctionStateDtos.CreateSessionRequest;
import it.fantacalciointelligence.backend.auctionstate.AuctionStateDtos.PurchaseRequest;
import it.fantacalciointelligence.backend.common.ConflictException;

@SpringBootTest
@Transactional
class AuctionStateLifecycleIntegrationTest {
    @Autowired AuctionStateService state;
    @Autowired AuctionPurchaseService purchases;
    @Autowired JdbcClient jdbc;
    Long sandboxSquad;

    @BeforeEach void fixture(){B59TestFixture.addFantacalcioRoles(jdbc);sandboxSquad=B59TestFixture.createSquad(jdbc);}

    @Test
    void createsInitializesOnlyEligiblePlayersAndCompletesWithoutHistoryLoss(){
        var created=state.create(new CreateSessionRequest(squadId()));
        assertThat(created.status()).isEqualTo(AuctionSessionStatus.DRAFT);
        assertThat(created.season()).isEqualTo("2026/2027");
        assertThatThrownBy(()->purchases.purchase(created.id(),new PurchaseRequest(
                eligibleNotOwned(),AuctionBuyerType.OTHER,null,"draft-key")))
                .isInstanceOf(ConflictException.class);

        var active=state.activate(created.id());
        var snapshot=state.state(created.id());
        assertThat(active.status()).isEqualTo(AuctionSessionStatus.ACTIVE);
        assertThat(snapshot.availabilityComplete()).isTrue();
        assertThat(snapshot.players()).isNotEmpty().allSatisfy(player->
                assertThat(isEligible(player.playerId(),active.season())).isTrue());
        assertThat(snapshot.players()).extracting(AuctionStateDtos.PlayerStateResponse::playerId)
                .doesNotHaveDuplicates();

        var completed=state.complete(created.id());
        assertThat(completed.status()).isEqualTo(AuctionSessionStatus.COMPLETED);
        assertThat(state.state(created.id()).players()).hasSameSizeAs(snapshot.players());
        assertThatThrownBy(()->purchases.purchase(created.id(),new PurchaseRequest(
                eligibleNotOwned(),AuctionBuyerType.OTHER,null,"completed-key")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void initializationExcludesActiveRegistrationAtNonMemberClub(){
        Long excluded=eligibleNotOwned();
        jdbc.sql("""
                DELETE FROM season_clubs membership
                USING seasons season, player_registrations registration
                WHERE season.code='2026/2027' AND membership.season_id=season.id
                  AND registration.player_id=:player AND registration.season_id=season.id
                  AND membership.club_id=registration.club_id
                """).param("player",excluded).update();

        var session=activated();

        assertThat(state.state(session.id()).players())
                .extracting(AuctionStateDtos.PlayerStateResponse::playerId)
                .doesNotContain(excluded);
    }

    @Test
    void otherPurchaseIsIdempotentAndNeverChangesUserBudgetOrRoster(){
        var session=activated();
        Long player=availablePlayer(session.id(),null);
        var before=state.state(session.id());
        var request=new PurchaseRequest(player,AuctionBuyerType.OTHER,null,"other-idempotent");
        var first=purchases.purchase(session.id(),request);
        var retry=purchases.purchase(session.id(),request);
        var after=state.state(session.id());
        assertThat(retry).isEqualTo(first);
        assertThat(after.remainingBudget()).isEqualTo(before.remainingBudget());
        assertThat(after.spentBudget()).isEqualTo(before.spentBudget());
        assertThat(after.purchases()).hasSize(1);
        assertThat(after.players()).filteredOn(p->p.playerId().equals(player)).singleElement()
                .extracting(AuctionStateDtos.PlayerStateResponse::availability)
                .isEqualTo(AuctionPlayerAvailability.ACQUIRED_BY_OTHER);
        assertThatThrownBy(()->purchases.purchase(session.id(),new PurchaseRequest(
                player,AuctionBuyerType.OTHER,2,"other-idempotent")))
                .isInstanceOf(ConflictException.class).hasMessageContaining("payload diverso");
    }

    @Test
    void userPurchaseUpdatesAuthoritativeRosterExactlyOnceAndPreservesReserve(){
        var session=activated();
        Long player=availablePlayer(session.id(),"role-open");
        int before=state.state(session.id()).remainingBudget();
        var request=new PurchaseRequest(player,AuctionBuyerType.USER,1,"user-idempotent");
        var first=purchases.purchase(session.id(),request);
        var retry=purchases.purchase(session.id(),request);
        var after=state.state(session.id());
        assertThat(retry.id()).isEqualTo(first.id());
        assertThat(after.remainingBudget()).isEqualTo(before-1);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM fantasy_squad_players WHERE squad_id=:s AND player_id=:p")
                .param("s",session.squadId()).param("p",player).query(Integer.class).single()).isEqualTo(1);
        assertThat(after.purchases()).hasSize(1);
        assertThat(after.players()).filteredOn(p->p.playerId().equals(player)).singleElement()
                .extracting(AuctionStateDtos.PlayerStateResponse::availability)
                .isEqualTo(AuctionPlayerAvailability.ACQUIRED_BY_USER);
    }

    @Test
    void duplicatePlayerPurchaseConflictsAndDoesNotPartiallyMutate(){
        var session=activated(); Long player=availablePlayer(session.id(),null);
        purchases.purchase(session.id(),new PurchaseRequest(player,AuctionBuyerType.OTHER,3,"winner"));
        assertThatThrownBy(()->purchases.purchase(session.id(),new PurchaseRequest(
                player,AuctionBuyerType.USER,1,"loser"))).isInstanceOf(ConflictException.class);
        assertThat(state.state(session.id()).purchases()).hasSize(1);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM fantasy_squad_players WHERE squad_id=:s AND player_id=:p")
                .param("s",session.squadId()).param("p",player).query(Integer.class).single()).isZero();
    }

    @Test
    void unsafeUserPurchaseRollsBackPurchaseRosterAndAvailability(){
        var session=activated();Long player=availablePlayer(session.id(),"role-open");
        assertThatThrownBy(()->purchases.purchase(session.id(),new PurchaseRequest(
                player,AuctionBuyerType.USER,session.totalBudget()+1,"unsafe")))
                .isInstanceOf(ConflictException.class);
        assertThat(state.state(session.id()).purchases()).isEmpty();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM fantasy_squad_players WHERE squad_id=:s AND player_id=:p")
                .param("s",session.squadId()).param("p",player).query(Integer.class).single()).isZero();
        assertThat(state.state(session.id()).players()).filteredOn(p->p.playerId().equals(player)).singleElement()
                .extracting(AuctionStateDtos.PlayerStateResponse::availability)
                .isEqualTo(AuctionPlayerAvailability.AVAILABLE);
    }

    @Test
    void purchaseRecheckRejectsPlayerWhoseClubLeavesSeasonAfterInitialization(){
        var session=activated();Long player=availablePlayer(session.id(),null);
        jdbc.sql("""
                DELETE FROM season_clubs membership
                USING auction_sessions auction, player_registrations registration
                WHERE auction.id=:session AND membership.season_id=auction.season_id
                  AND registration.player_id=:player AND registration.season_id=auction.season_id
                  AND membership.club_id=registration.club_id
                """).param("session",session.id()).param("player",player).update();

        assertThatThrownBy(()->purchases.purchase(session.id(),new PurchaseRequest(
                player,AuctionBuyerType.OTHER,null,"membership-removed")))
                .isInstanceOf(ConflictException.class);
        assertThat(state.state(session.id()).purchases()).isEmpty();
        assertThat(state.state(session.id()).players()).filteredOn(p->p.playerId().equals(player)).singleElement()
                .extracting(AuctionStateDtos.PlayerStateResponse::availability)
                .isEqualTo(AuctionPlayerAvailability.AVAILABLE);
    }

    private AuctionStateDtos.SessionResponse activated(){var created=state.create(new CreateSessionRequest(squadId()));return state.activate(created.id());}
    private Long squadId(){return sandboxSquad;}
    private Long eligibleNotOwned(){return jdbc.sql("""
            SELECT p.id FROM players p JOIN player_registrations r ON r.player_id=p.id
            JOIN seasons s ON s.id=r.season_id AND s.code='2026/2027'
            JOIN season_clubs sc ON sc.season_id=s.id AND sc.club_id=r.club_id
            JOIN player_roles pr ON pr.player_id=p.id AND pr.season_id=s.id AND pr.source='FANTACALCIO'
            WHERE p.active=TRUE AND r.registration_status='ACTIVE'
            ORDER BY p.id LIMIT 1
            """).query(Long.class).single();}
    private Long availablePlayer(Long session,String mode){return jdbc.sql("""
            SELECT state.player_id FROM auction_player_states state
            JOIN auction_sessions auction ON auction.id=state.session_id
            JOIN fantasy_squads squad ON squad.id=auction.squad_id
            JOIN league_configurations league ON league.id=squad.league_configuration_id
            WHERE state.session_id=:session AND state.availability='AVAILABLE'
              AND (:open=FALSE OR
                (SELECT COUNT(*) FROM fantasy_squad_players f WHERE f.squad_id=squad.id
                 AND f.fantasy_role=state.fantasy_role) < CASE state.fantasy_role
                 WHEN 'P' THEN league.goalkeepers_count WHEN 'D' THEN league.defenders_count
                 WHEN 'C' THEN league.midfielders_count ELSE league.forwards_count END)
            ORDER BY state.player_id LIMIT 1
            """).param("session",session).param("open",mode!=null).query(Long.class).single();}
    private boolean isEligible(Long player,String season){return jdbc.sql("""
            SELECT EXISTS(SELECT 1 FROM players p JOIN player_registrations r ON r.player_id=p.id
            JOIN seasons s ON s.id=r.season_id
            JOIN season_clubs sc ON sc.season_id=s.id AND sc.club_id=r.club_id
            JOIN player_roles pr ON pr.player_id=p.id AND pr.season_id=s.id AND pr.source='FANTACALCIO'
            WHERE p.id=:p AND s.code=:s AND p.active=TRUE
              AND r.registration_status='ACTIVE' AND pr.fantasy_role IN ('P','D','C','A'))
            """).param("p",player).param("s",season).query(Boolean.class).single();}
}
