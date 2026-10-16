package it.fantacalciointelligence.backend.config;

import java.io.IOException;
import java.time.Clock;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int maximumRequests;
    private final long windowMillis;
    private final Clock clock;

    @Autowired
    public RateLimitFilter(
            @Value("${app.security.rate-limit.requests:60}") int maximumRequests,
            @Value("${app.security.rate-limit.window:1m}") java.time.Duration window
    ) {
        this(maximumRequests, window, Clock.systemUTC());
    }

    RateLimitFilter(int maximumRequests, java.time.Duration window, Clock clock) {
        if (maximumRequests < 1 || window == null || window.isZero() || window.isNegative()) {
            throw new IllegalStateException("Configurazione rate limit non valida");
        }
        this.maximumRequests = maximumRequests;
        this.windowMillis = window.toMillis();
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        boolean authenticationAttempt = request.getHeader("Authorization") != null;
        return "GET".equals(method)
                && !path.startsWith("/api/v1/admin/")
                && !authenticationAttempt;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long now = clock.millis();
        cleanupExpired(now);
        String principal = identity(request);
        // Remote address is taken from the servlet container; arbitrary forwarded headers are ignored here.
        String key = principal + '|' + request.getRemoteAddr();
        Window current = windows.compute(key, (ignored, previous) ->
                previous == null || now - previous.startedAt >= windowMillis
                        ? new Window(now, 1)
                        : new Window(previous.startedAt, previous.count + 1));
        if (current.count > maximumRequests) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Limite richieste superato\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String identity(HttpServletRequest request) {
        if (request.getUserPrincipal() != null) {
            return request.getUserPrincipal().getName().toLowerCase(Locale.ROOT);
        }
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
            try {
                String decoded = new String(
                        Base64.getDecoder().decode(authorization.substring(6).trim()),
                        StandardCharsets.UTF_8
                );
                int separator = decoded.indexOf(':');
                if (separator > 0) {
                    return decoded.substring(0, separator).toLowerCase(Locale.ROOT);
                }
            } catch (IllegalArgumentException ignored) {
                // Malformed credentials share the anonymous bucket; no credential material is logged.
            }
        }
        return "anonymous";
    }

    private void cleanupExpired(long now) {
        windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt >= windowMillis);
    }

    int trackedIdentities() {
        return windows.size();
    }

    private record Window(long startedAt, int count) {
    }
}
