package it.fantacalciointelligence.backend.config;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProvisionUserRequest(
        @NotBlank @Size(min = 3, max = 100) String username,
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 12, max = 200) String password,
        @NotBlank @Pattern(regexp = "USER|ADMIN") String role,
        boolean enabled
) {
}
