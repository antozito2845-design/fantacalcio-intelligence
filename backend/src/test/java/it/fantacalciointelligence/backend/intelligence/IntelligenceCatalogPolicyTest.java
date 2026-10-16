package it.fantacalciointelligence.backend.intelligence;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import it.fantacalciointelligence.backend.catalog.readiness.CatalogReadinessResponse;
import it.fantacalciointelligence.backend.catalog.readiness.CatalogReadinessService;
import it.fantacalciointelligence.backend.common.ConflictException;

class IntelligenceCatalogPolicyTest {
    private final CatalogReadinessService readiness = mock(CatalogReadinessService.class);
    private final IntelligenceCatalogPolicy policy = new IntelligenceCatalogPolicy(readiness);

    @Test
    void blockedCurrentCatalogPreventsPremiumCalculation() {
        CatalogReadinessResponse response = mock(CatalogReadinessResponse.class);
        when(response.status()).thenReturn("BLOCKED");
        when(readiness.evaluateLatest("2026/2027")).thenReturn(response);

        assertThatThrownBy(() -> policy.assertReadable("2026/2027", true))
                .isInstanceOf(ConflictException.class).hasMessageContaining("BLOCKED");
    }

    @Test
    void readyCurrentCatalogAllowsPremiumCalculation() {
        CatalogReadinessResponse response = mock(CatalogReadinessResponse.class);
        when(response.status()).thenReturn("READY");
        when(readiness.evaluateLatest("2026/2027")).thenReturn(response);

        assertThatCode(() -> policy.assertReadable("2026/2027", true)).doesNotThrowAnyException();
    }

    @Test
    void historicalProfileDoesNotRequireCurrentCatalogGate() {
        assertThatCode(() -> policy.assertReadable("2025/2026", false)).doesNotThrowAnyException();
        verifyNoInteractions(readiness);
    }
}
