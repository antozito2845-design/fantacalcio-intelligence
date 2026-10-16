package it.fantacalciointelligence.backend.refresh;

import org.springframework.stereotype.Component;

/** Test seam for deterministic refresh recovery certification; production implementation is a no-op. */
@Component
public class RefreshFaultInjector {

    public void checkpoint(String step) {
        // Deliberately empty in production.
    }
}
