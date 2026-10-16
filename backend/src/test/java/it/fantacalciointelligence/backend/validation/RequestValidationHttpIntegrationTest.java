package it.fantacalciointelligence.backend.validation;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class RequestValidationHttpIntegrationTest {
    @Autowired MockMvc mvc;

    @Test
    void unknownRouteUsesSecurityForAnonymousAndSanitized404ForAuthenticatedUser() throws Exception {
        mvc.perform(get("/api/v1/does-not-exist"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/does-not-exist")
                .with(httpBasic("user-test", "user-pass")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Risorsa non trovata"))
                .andExpect(jsonPath("$.path").value("/api/v1/does-not-exist"))
                .andExpect(content().string(not(containsString("stackTrace"))))
                .andExpect(content().string(not(containsString("user-pass"))));
    }

    @Test
    void squadConstructionRejectsRangesBudgetSlotsAndBoughtPlayer() throws Exception {
        mvc.perform(post("/api/v1/auction/squad-strategy/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"season":"2026/2027","teams":5,"initialBudget":149,
                         "remainingBudget":150,"defenseModifier":false,"currentPhaseRole":"X",
                         "rosterSlots":{"X":-1},
                         "playersAlreadyBought":[{"playerId":0,"role":"X","pricePaid":0}],"limit":10}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void liveAuctionRejectsPublicLeagueRanges() throws Exception {
        mvc.perform(post("/api/v1/auction/live/decision")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"season":"2026/2027","leagueTeams":13,"totalBudget":1001,
                         "remainingBudget":100,"currentRole":"D","currentPlayerId":1,
                         "currentPrice":1,"proposedBid":1,"playersAlreadyBought":[]}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transferClaimRejectsNonPositivePlayerAndIncoherentDomesticDestination() throws Exception {
        mvc.perform(post("/api/v1/admin/transfers/claims")
                .with(httpBasic("admin-test", "admin-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"playerId":0,"season":"2026/2027","leavingSerieA":false,
                         "destinationClubName":"Estero","source":"official",
                         "sourceReference":"reference","sourcePublishedAt":"2026-08-14T10:00:00Z",
                         "effectiveAt":"2026-08-14"}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transferClaimRejectsTargetClubWhenLeavingSerieA() throws Exception {
        mvc.perform(post("/api/v1/admin/transfers/claims")
                .with(httpBasic("admin-test", "admin-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"playerId":1,"season":"2026/2027","targetClub":"Roma",
                         "leavingSerieA":true,"destinationClubName":"Arsenal","source":"official",
                         "sourceReference":"reference","sourcePublishedAt":"2026-08-14T10:00:00Z",
                         "effectiveAt":"2026-08-14"}
                        """))
                .andExpect(status().isBadRequest());
    }
}
