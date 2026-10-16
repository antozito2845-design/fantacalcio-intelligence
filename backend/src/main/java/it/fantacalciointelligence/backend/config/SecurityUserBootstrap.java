package it.fantacalciointelligence.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SecurityUserBootstrap implements ApplicationRunner {

    private final JdbcClient jdbcClient;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;
    private final String userUsername;
    private final String userPassword;

    public SecurityUserBootstrap(
            JdbcClient jdbcClient,
            PasswordEncoder passwordEncoder,
            @Value("${app.security.admin.username}") String adminUsername,
            @Value("${app.security.admin.password}") String adminPassword,
            @Value("${app.security.user.username}") String userUsername,
            @Value("${app.security.user.password}") String userPassword
    ) {
        this.jdbcClient = jdbcClient;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = require("app.security.admin.username", adminUsername);
        this.adminPassword = require("app.security.admin.password", adminPassword);
        this.userUsername = require("app.security.user.username", userUsername);
        this.userPassword = require("app.security.user.password", userPassword);
        if (adminUsername.equalsIgnoreCase(userUsername)) {
            throw new IllegalStateException("Gli utenti ADMIN e USER devono essere distinti");
        }
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        createIfMissing(adminUsername, adminPassword, "ADMIN");
        createIfMissing(userUsername, userPassword, "USER");
    }

    private void createIfMissing(String username, String password, String role) {
        String existingRole = jdbcClient.sql("""
                SELECT role FROM app_users WHERE LOWER(username) = LOWER(:username)
                """)
                .param("username", username)
                .query(String.class)
                .optional()
                .orElse(null);
        if (existingRole != null) {
            if (!role.equals(existingRole)) {
                throw new IllegalStateException(
                        "Ruolo bootstrap incoerente per l'utente configurato " + username
                );
            }
            return;
        }
        jdbcClient.sql("""
                INSERT INTO app_users (username, email, password_hash, role)
                SELECT :username, :email, :passwordHash, :role
                WHERE NOT EXISTS (
                    SELECT 1 FROM app_users WHERE LOWER(username) = LOWER(:username)
                )
                """)
                .param("username", username)
                .param("email", username + "@bootstrap.invalid")
                .param("passwordHash", passwordEncoder.encode(password))
                .param("role", role)
                .update();
    }

    private static String require(String property, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Configurazione di sicurezza mancante: " + property);
        }
        return value.trim();
    }
}
