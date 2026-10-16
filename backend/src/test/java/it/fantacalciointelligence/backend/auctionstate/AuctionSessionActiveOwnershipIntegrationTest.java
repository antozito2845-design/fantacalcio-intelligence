package it.fantacalciointelligence.backend.auctionstate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.auctionstate.AuctionStateDtos.CreateSessionRequest;
import it.fantacalciointelligence.backend.common.ConflictException;

@SpringBootTest
@Transactional
class AuctionSessionActiveOwnershipIntegrationTest {

    @Autowired
    AuctionStateService state;

    @Autowired
    JdbcClient jdbc;

    Long squadOne;
    Long squadTwo;
    Long squadThree;

    @BeforeEach
    void fixture() {

        B59TestFixture.addFantacalcioRoles(jdbc);

        squadOne = B59TestFixture.createSquad(jdbc);
        squadTwo = B59TestFixture.createSquad(jdbc);
        squadThree = B59TestFixture.createSquad(jdbc);
    }

    @Test
    void differentOwnersCanHaveActiveSessionsAtSameTime() {

        var first =
                state.create(
                        new CreateSessionRequest(squadOne),
                        "owner-a"
                );

        var second =
                state.create(
                        new CreateSessionRequest(squadTwo),
                        "owner-b"
                );

        var firstActive = state.activate(first.id());
        var secondActive = state.activate(second.id());

        assertThat(firstActive.status())
                .isEqualTo(AuctionSessionStatus.ACTIVE);

        assertThat(secondActive.status())
                .isEqualTo(AuctionSessionStatus.ACTIVE);

        int activeCount =
                jdbc.sql("""
                        SELECT COUNT(*)
                        FROM auction_sessions
                        WHERE status = 'ACTIVE'
                          AND owner_username IN ('owner-a', 'owner-b')
                        """)
                        .query(Integer.class)
                        .single();

        assertThat(activeCount)
                .isEqualTo(2);
    }

    @Test
    void sameOwnerCannotHaveTwoActiveSessions() {

        var first =
                state.create(
                        new CreateSessionRequest(squadOne),
                        "owner-a"
                );

        var second =
                state.create(
                        new CreateSessionRequest(squadTwo),
                        "owner-a"
                );

        state.activate(first.id());

        assertThatThrownBy(
                () -> state.activate(second.id())
        )
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining(
                        "sessione ACTIVE"
                );

        /*
         * Non eseguire altre query qui:
         * PostgreSQL invalida la transazione corrente dopo
         * la violazione del unique constraint.
         *
         * Il vincolo DB è precisamente ciò che questo test
         * vuole certificare.
         */
    }

    @Test
    void sameSquadCannotHaveTwoActiveSessionsEvenWithDifferentOwners() {

        var first =
                state.create(
                        new CreateSessionRequest(squadThree),
                        "owner-a"
                );

        var second =
                state.create(
                        new CreateSessionRequest(squadThree),
                        "owner-b"
                );

        state.activate(first.id());

        assertThatThrownBy(
                () -> state.activate(second.id())
        )
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining(
                        "sessione ACTIVE"
                );

        /*
         * Anche qui nessuna query successiva al constraint
         * violation nella stessa transazione PostgreSQL.
         */
    }
}