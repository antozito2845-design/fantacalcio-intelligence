package it.fantacalciointelligence.backend.auctionstate;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.auctionstate.AuctionStateDtos.CreateSessionRequest;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuctionSessionOwnershipIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    AuctionStateService state;

    Long sandboxSquad;

    @BeforeEach
    void fixture() {

        B59TestFixture.addFantacalcioRoles(
                jdbc
        );

        sandboxSquad =
                B59TestFixture.createSquad(
                        jdbc
                );
    }

    @Test
    void ownerCanReadOwnSession() throws Exception {

        var session =
                state.create(
                        new CreateSessionRequest(
                                sandboxSquad
                        ),
                        "owner-a"
                );

        mvc.perform(
                        get(
                                "/api/v1/auction/sessions/"
                                        + session.id()
                        )
                                .with(
                                        user("owner-a")
                                                .roles("USER")
                                )
                )
                .andExpect(
                        status().isOk()
                );
    }

    @Test
    void differentUserCannotDiscoverSession() throws Exception {

        var session =
                state.create(
                        new CreateSessionRequest(
                                sandboxSquad
                        ),
                        "owner-a"
                );

        mvc.perform(
                        get(
                                "/api/v1/auction/sessions/"
                                        + session.id()
                        )
                                .with(
                                        user("owner-b")
                                                .roles("USER")
                                )
                )
                .andExpect(
                        status().isNotFound()
                );
    }

    @Test
    void adminCanReadAnySession() throws Exception {

        var session =
                state.create(
                        new CreateSessionRequest(
                                sandboxSquad
                        ),
                        "owner-a"
                );

        mvc.perform(
                        get(
                                "/api/v1/auction/sessions/"
                                        + session.id()
                        )
                                .with(
                                        user("administrator")
                                                .roles("ADMIN")
                                )
                )
                .andExpect(
                        status().isOk()
                );
    }

    @Test
    void anonymousCannotReadSession() throws Exception {

        var session =
                state.create(
                        new CreateSessionRequest(
                                sandboxSquad
                        ),
                        "owner-a"
                );

        mvc.perform(
                        get(
                                "/api/v1/auction/sessions/"
                                        + session.id()
                        )
                )
                .andExpect(
                        status().isUnauthorized()
                );
    }
}
