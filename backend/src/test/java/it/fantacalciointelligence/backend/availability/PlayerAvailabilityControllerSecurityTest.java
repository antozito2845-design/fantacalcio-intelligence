package it.fantacalciointelligence.backend.availability;

import static org.springframework.test.web.servlet
        .request.MockMvcRequestBuilders.put;

import static org.springframework.test.web.servlet
        .result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PlayerAvailabilityControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldRejectUpdateWithoutAuthentication()
            throws Exception {

        mockMvc.perform(
                        put(
                                "/api/v1/availabilities/players/{playerId}",
                                1
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(validRequest())
                )
                .andExpect(
                        status().isUnauthorized()
                );
    }

    @Test
    @WithMockUser(
            username = "standard-user",
            roles = "USER"
    )
    void shouldRejectUpdateForStandardUser()
            throws Exception {

        mockMvc.perform(
                        put(
                                "/api/v1/availabilities/players/{playerId}",
                                1
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(validRequest())
                )
                .andExpect(
                        status().isForbidden()
                );
    }

    private String validRequest() {
        return """
                {
                  "season": "2026/2027",
                  "matchday": 1,
                  "status": "AVAILABLE",
                  "startingProbability": 90,
                  "availabilityProbability": 100,
                  "injuryDescription": null,
                  "expectedReturnDate": null,
                  "source": "MANUAL"
                }
                """;
    }
}