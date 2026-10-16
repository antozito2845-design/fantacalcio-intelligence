package it.fantacalciointelligence.backend.config;

public record ProvisionedUserResponse(
        Long id,
        String username,
        String email,
        String role,
        boolean enabled
) {
}
