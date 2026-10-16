package it.fantacalciointelligence.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void shouldExposeHealthWithoutAuthentication()
            throws Exception {

        mockMvc.perform(
                        get("/actuator/health")
                )
                .andExpect(status().isOk());
    }

    @Test
    void shouldExposeReadinessProbeWithoutAuthentication()
            throws Exception {

        mockMvc.perform(
                        get("/actuator/health/readiness")
                )
                .andExpect(status().isOk());
    }

    @Test
    void shouldProtectInfoEndpoint()
            throws Exception {

        mockMvc.perform(
                        get("/actuator/info")
                )
                .andExpect(status().isUnauthorized());

        mockMvc.perform(
                        get("/actuator/info")
                                .with(
                                        httpBasic(
                                                "admin-test",
                                                "admin-pass"
                                        )
                                )
                )
                .andExpect(status().isOk());
    }

    @Test
    void shouldStoreEncodedPasswords() {

        UserDetails administrator =
                userDetailsService
                        .loadUserByUsername(
                                "admin-test"
                        );

        assertThat(
                administrator.getPassword()
        ).doesNotContain(
                "{noop}"
        );

        assertThat(
                passwordEncoder.matches(
                        "admin-pass",
                        administrator.getPassword()
                )
        ).isTrue();
    }

    @Test
    void shouldAllowConfiguredCorsOrigin()
            throws Exception {

        mockMvc.perform(
                        options("/api/v1/players")
                                .header(
                                        HttpHeaders.ORIGIN,
                                        "http://localhost:4200"
                                )
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                        "GET"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        header().string(
                                HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                                "http://localhost:4200"
                        )
                );
    }

    @Test
    void shouldAllowConfiguredLoopbackCorsOrigin()
            throws Exception {

        mockMvc.perform(
                        options("/api/v1/auction/sessions")
                                .header(
                                        HttpHeaders.ORIGIN,
                                        "http://127.0.0.1:5173"
                                )
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                        "POST"
                                )
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                        "authorization,content-type"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        header().string(
                                HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                                "http://127.0.0.1:5173"
                        )
                );
    }

    @Test
    void shouldRejectUnknownCorsOrigin()
            throws Exception {

        mockMvc.perform(
                        options("/api/v1/players")
                                .header(
                                        HttpHeaders.ORIGIN,
                                        "https://evil.example"
                                )
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                        "GET"
                                )
                )
                .andExpect(status().isForbidden());
    }
}
