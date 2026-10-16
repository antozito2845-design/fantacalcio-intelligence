package it.fantacalciointelligence.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

import it.fantacalciointelligence.backend.common.ConflictException;

@SpringBootTest
class UserBootstrapProvisioningIntegrationTest {

    @Autowired JdbcClient jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired UserProvisioningService provisioning;

    @BeforeEach
    void cleanup() {
        jdbc.sql("DELETE FROM app_users WHERE username LIKE 'corrective-%'").update();
    }

    @Test
    void bootstrapDoesNotOverwritePasswordAndFailsOnRoleMismatch() {
        SecurityUserBootstrap first = new SecurityUserBootstrap(jdbc, encoder,
                "corrective-admin", "initial-admin-password", "corrective-user", "initial-user-password");
        first.run(new DefaultApplicationArguments());
        String hash = hash("corrective-user");

        SecurityUserBootstrap retry = new SecurityUserBootstrap(jdbc, encoder,
                "corrective-admin", "changed-admin-password", "corrective-user", "changed-user-password");
        retry.run(new DefaultApplicationArguments());
        assertThat(hash("corrective-user")).isEqualTo(hash);
        assertThat(encoder.matches("initial-user-password", hash)).isTrue();

        jdbc.sql("UPDATE app_users SET role='ADMIN' WHERE username='corrective-user'").update();
        assertThatThrownBy(() -> retry.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Ruolo bootstrap incoerente");
    }

    @Test
    void provisioningHashesPasswordAndRejectsCaseInsensitiveUsernameOrEmail() {
        provisioning.create(new ProvisionUserRequest("corrective-alpha", "alpha@example.test",
                "a-secure-password", "USER", true));
        assertThat(encoder.matches("a-secure-password", hash("corrective-alpha"))).isTrue();

        assertThatThrownBy(() -> provisioning.create(new ProvisionUserRequest(
                "CORRECTIVE-ALPHA", "other@example.test", "another-password", "USER", true)))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> provisioning.create(new ProvisionUserRequest(
                "corrective-beta", "ALPHA@example.test", "another-password", "USER", true)))
                .isInstanceOf(ConflictException.class);
    }

    private String hash(String username) {
        return jdbc.sql("SELECT password_hash FROM app_users WHERE username=:username")
                .param("username", username).query(String.class).single();
    }
}
