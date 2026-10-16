package it.fantacalciointelligence.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "DATABASE_URL=jdbc:postgresql://localhost:5432/fantacalcio_intelligence_test",
        "DATABASE_USERNAME=fantacalcio_app",
        "DATABASE_PASSWORD=FantaDev2026!",
        "ADMIN_USERNAME=prod-admin-test",
        "ADMIN_PASSWORD=prod-admin-pass",
        "APP_USER_USERNAME=prod-user-test",
        "APP_USER_PASSWORD=prod-user-pass",
        "CORS_ALLOWED_ORIGINS=https://app.example.test"
})
@ActiveProfiles("prod")
@AutoConfigureMockMvc
class ProductionProfileSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void validProductionConfigurationStartsAndHealthHidesComponents() throws Exception {
        String health = mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(health).contains("\"status\"")
                .doesNotContain("components", "jdbc:", "FantaDev2026!");
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }

    @Test
    void missingAdminPasswordPreventsProductionStartup() {
        SpringApplication application = new SpringApplication(
                it.fantacalciointelligence.backend.BackendApplication.class
        );

        assertThatThrownBy(() -> application.run(
                "--spring.profiles.active=prod",
                "--server.port=0",
                "--DATABASE_URL=jdbc:h2:mem:missing-prod-secret;MODE=PostgreSQL",
                "--DATABASE_USERNAME=sa",
                "--DATABASE_PASSWORD=test-database-password",
                "--ADMIN_USERNAME=isolated-prod-admin",
                "--ADMIN_PASSWORD=",
                "--APP_USER_USERNAME=isolated-prod-user",
                "--APP_USER_PASSWORD=isolated-prod-user-password",
                "--CORS_ALLOWED_ORIGINS=https://app.example.test",
                "--spring.flyway.enabled=false",
                "--spring.jpa.hibernate.ddl-auto=none",
                "--app.data-refresh.scheduler.enabled=false",
                "--app.source-ingestion.enabled=false"
        ))
                .hasStackTraceContaining("app.security.admin.password");
    }
}
