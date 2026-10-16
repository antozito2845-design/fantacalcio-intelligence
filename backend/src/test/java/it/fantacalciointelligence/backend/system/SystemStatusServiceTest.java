package it.fantacalciointelligence.backend.system;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class SystemStatusServiceTest {

    @Test
    void resolvesProductionFromProdProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThat(SystemStatusService.resolveRuntimeEnvironment(environment))
                .isEqualTo("production");
    }

    @Test
    void resolvesDevelopmentFromDevProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");

        assertThat(SystemStatusService.resolveRuntimeEnvironment(environment))
                .isEqualTo("development");
    }

    @Test
    void resolvesSpringDefaultWhenNoProfileIsActive() {
        MockEnvironment environment = new MockEnvironment();

        assertThat(SystemStatusService.resolveRuntimeEnvironment(environment))
                .isEqualTo("default");
    }
}
