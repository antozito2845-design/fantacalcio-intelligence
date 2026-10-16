package it.fantacalciointelligence.backend.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CorsConfigurationSource corsConfigurationSource,
            RateLimitFilter rateLimitFilter
    ) throws Exception {

        return http
                .csrf(csrf -> csrf.disable())

                .cors(cors ->
                        cors.configurationSource(
                                corsConfigurationSource
                        )
                )

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .authorizeHttpRequests(auth -> auth

                        // ====================================================
                        // ADMIN namespace
                        // ====================================================

                        .requestMatchers(
                                "/api/v1/admin/**"
                        )
                        .hasRole("ADMIN")

                        // ====================================================
                        // Administrative mutations outside /admin
                        // ====================================================

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/imports/players/csv",
                                "/api/v1/reconciliation/players"
                        )
                        .hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/v1/availabilities/**",
                                "/api/v1/league/**"
                        )
                        .hasRole("ADMIN")

                        // ====================================================
                        // User-controlled squad mutations
                        // ====================================================

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/squad/**",
                                "/api/v1/squads/**"
                        )
                        .authenticated()

                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/v1/squad/**",
                                "/api/v1/squads/**"
                        )
                        .authenticated()

                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/squad/**",
                                "/api/v1/squads/**"
                        )
                        .authenticated()

                        // ====================================================
                        // B60.1 auction state resources:
                        // authentication + OWNER/ADMIN service-level check
                        // ====================================================

                        .requestMatchers(
                                "/api/v1/auction/sessions",
                                "/api/v1/auction/sessions/**"
                        )
                        .authenticated()

                        // ====================================================
                        // Public read/use APIs
                        // ====================================================
                        // NOTE: /api/v1/lineups/** is intentionally NOT in this
                        // public list: it exposes the owner-scoped roster of an
                        // authenticated user. It falls through to
                        // anyRequest().authenticated() and the service enforces
                        // squad ownership.

                        .requestMatchers(
                                "/api/v1/system/status",
                                "/api/v1/seasons",
                                "/api/v1/clubs",
                                "/api/v1/players",
                                "/api/v1/players/**",
                                "/api/v1/auction/**",
                                "/api/v1/league/**",
                                "/api/v1/fixtures/**",
                                "/api/v1/pairings/**",
                                "/api/v1/scouting",
                                "/api/v1/scouting/**",
                                "/actuator/health",
                                "/actuator/health/**"
                        )
                        .permitAll()

                        // Everything not explicitly public is authenticated.
                        .anyRequest()
                        .authenticated()
                )

                .httpBasic(Customizer.withDefaults())

                .addFilterBefore(rateLimitFilter, BasicAuthenticationFilter.class)

                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {

        return PasswordEncoderFactories
                .createDelegatingPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(

            @Value("${app.security.cors.allowed-origins}")
            String allowedOrigins
    ) {

        List<String> origins =
                Arrays.stream(
                                allowedOrigins.split(",")
                        )
                        .map(String::trim)
                        .filter(origin ->
                                !origin.isBlank()
                        )
                        .toList();

        if (origins.isEmpty()) {
            throw new IllegalStateException(
                    "Configurare almeno una origine CORS consentita"
            );
        }

        if (origins.contains("*")) {
            throw new IllegalStateException(
                    "CORS wildcard non consentito"
            );
        }

        CorsConfiguration configuration =
                new CorsConfiguration();

        configuration.setAllowedOrigins(origins);

        configuration.setAllowedMethods(
                List.of(
                        "GET",
                        "POST",
                        "PUT",
                        "PATCH",
                        "DELETE",
                        "OPTIONS"
                )
        );

        configuration.setAllowedHeaders(
                List.of(
                        HttpHeaders.AUTHORIZATION,
                        HttpHeaders.CONTENT_TYPE,
                        HttpHeaders.ACCEPT,
                        HttpHeaders.CACHE_CONTROL
                )
        );

        configuration.setExposedHeaders(
                List.of(
                        HttpHeaders.LOCATION
                )
        );

        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
                "/api/**",
                configuration
        );

        return source;
    }

}
