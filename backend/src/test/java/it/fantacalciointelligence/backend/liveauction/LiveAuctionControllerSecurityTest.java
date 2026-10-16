package it.fantacalciointelligence.backend.liveauction;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import it.fantacalciointelligence.backend.common.BadRequestException;
import it.fantacalciointelligence.backend.common.ResourceNotFoundException;

@SpringBootTest
@AutoConfigureMockMvc
class LiveAuctionControllerSecurityTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private LiveAuctionDecisionService service;

    @Test
    void publicAuctionPolicyAllowsValidDecision() throws Exception {
        when(service.decide(any())).thenReturn(response());
        mockMvc.perform(post("/api/v1/auction/live/decision")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validJson()))
                .andExpect(status().isOk());
    }

    @Test
    void invalidCurrentBidMapsToBadRequest() throws Exception {
        when(service.decide(any())).thenThrow(new BadRequestException("currentBid deve essere almeno 1"));
        mockMvc.perform(post("/api/v1/auction/live/decision")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownPlayerMapsToNotFound() throws Exception {
        when(service.decide(any())).thenThrow(new ResourceNotFoundException("Giocatore non trovato"));
        mockMvc.perform(post("/api/v1/auction/live/decision")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validJson()))
                .andExpect(status().isNotFound());
    }

    @Test
    void validNewContractIsPublic() throws Exception {
        when(service.decide(any())).thenReturn(response());
        mockMvc.perform(post("/api/v1/auction/live/decision")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"season":"2026/2027","leagueTeams":8,"totalBudget":1000,
                         "remainingBudget":1000,"defenseModifier":false,"currentRole":"D",
                         "currentPlayerId":1,"currentPrice":10,"proposedBid":12,
                         "playersAlreadyBought":[],"unavailablePlayerIds":[2,3],
                         "availabilityComplete":true}
                        """))
                .andExpect(status().isOk());
    }

    private String validJson() {
        return """
                {"season":"2026/2027","leagueTeams":8,"totalBudget":1000,
                 "remainingBudget":1000,"defenseModifier":false,"currentRole":"D",
                 "currentPlayerId":1,"currentBid":10,"playersAlreadyBought":[]}
                """;
    }

    private LiveAuctionDecisionResponse response() {
        return new LiveAuctionDecisionResponse(LiveAuctionAction.INSUFFICIENT_EVIDENCE,
                1L, null, "D", 10, null, null, null, null, 1000, 990, null, null,
                null, null, null, BigDecimal.ZERO, LiveAuctionEvidenceStatus.INSUFFICIENT_EVIDENCE,
                List.of(), List.of(), List.of());
    }
}
