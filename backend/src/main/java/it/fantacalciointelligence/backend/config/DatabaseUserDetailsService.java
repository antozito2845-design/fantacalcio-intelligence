package it.fantacalciointelligence.backend.config;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final JdbcClient jdbcClient;

    public DatabaseUserDetailsService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return jdbcClient.sql("""
                SELECT username, password_hash, role, enabled
                FROM app_users
                WHERE LOWER(username) = LOWER(:username)
                """)
                .param("username", username)
                .query((rs, row) -> User.withUsername(rs.getString("username"))
                        .password(rs.getString("password_hash"))
                        .roles(rs.getString("role"))
                        .disabled(!rs.getBoolean("enabled"))
                        .build())
                .optional()
                .orElseThrow(() -> new UsernameNotFoundException("Credenziali non valide"));
    }
}
