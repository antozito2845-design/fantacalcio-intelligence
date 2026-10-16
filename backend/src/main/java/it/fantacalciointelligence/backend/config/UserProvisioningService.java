package it.fantacalciointelligence.backend.config;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.common.ConflictException;

@Service
public class UserProvisioningService {

    private final JdbcClient jdbcClient;
    private final PasswordEncoder passwordEncoder;

    public UserProvisioningService(JdbcClient jdbcClient, PasswordEncoder passwordEncoder) {
        this.jdbcClient = jdbcClient;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public ProvisionedUserResponse create(ProvisionUserRequest request) {
        try {
            Long id = jdbcClient.sql("""
                    INSERT INTO app_users (username, email, password_hash, role, enabled)
                    VALUES (:username, :email, :passwordHash, :role, :enabled)
                    RETURNING id
                    """)
                    .param("username", request.username().trim())
                    .param("email", request.email().trim())
                    .param("passwordHash", passwordEncoder.encode(request.password()))
                    .param("role", request.role())
                    .param("enabled", request.enabled())
                    .query(Long.class).single();
            return new ProvisionedUserResponse(id, request.username().trim(), request.email().trim(),
                    request.role(), request.enabled());
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Username o email già esistente");
        }
    }
}
