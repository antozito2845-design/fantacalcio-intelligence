package it.fantacalciointelligence.backend.auctionstate;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import it.fantacalciointelligence.backend.common.ResourceNotFoundException;

@Service
public class AuctionSessionAccessService {

    private final JdbcClient jdbc;

    public AuctionSessionAccessService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public String authenticatedUsername(Authentication authentication) {

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null
                || authentication.getName().isBlank()
                || "anonymousUser".equalsIgnoreCase(authentication.getName())) {

            throw new ResourceNotFoundException(
                    "Sessione asta non trovata"
            );
        }

        return authentication.getName().trim();
    }

    public void requireAccess(
            Long sessionId,
            Authentication authentication
    ) {

        String username = authenticatedUsername(authentication);

        String owner = jdbc.sql("""
                SELECT owner_username
                FROM auction_sessions
                WHERE id = :id
                """)
                .param("id", sessionId)
                .query(String.class)
                .optional()
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Sessione asta non trovata"
                        )
                );

        if (isAdmin(authentication)) {
            return;
        }

        if (!owner.equalsIgnoreCase(username)) {
            /*
             * Intenzionalmente 404 e non 403:
             * un utente non proprietario non deve poter verificare
             * l'esistenza di sessioni appartenenti ad altri utenti.
             */
            throw new ResourceNotFoundException(
                    "Sessione asta non trovata"
            );
        }
    }

    private boolean isAdmin(Authentication authentication) {

        return authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        "ROLE_ADMIN".equals(
                                authority.getAuthority()
                        )
                );
    }
}
