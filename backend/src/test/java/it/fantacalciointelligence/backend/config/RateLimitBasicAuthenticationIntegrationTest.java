package it.fantacalciointelligence.backend.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "app.security.rate-limit.requests=2",
        "app.security.rate-limit.window=10m"
})
@AutoConfigureMockMvc
class RateLimitBasicAuthenticationIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Test
    void repeatedWrongBasicPasswordIsLimitedBeforeAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/info").with(httpBasic("bruteforce-user", "wrong-1")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/info").with(httpBasic("bruteforce-user", "wrong-2")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/info").with(httpBasic("bruteforce-user", "wrong-3")))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void ordinaryPublicGetIsOutsidePolicy() throws Exception {
        for (int index = 0; index < 5; index++) {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk());
        }
    }
}
