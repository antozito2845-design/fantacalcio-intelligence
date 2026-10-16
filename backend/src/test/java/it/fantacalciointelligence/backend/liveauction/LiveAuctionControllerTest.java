package it.fantacalciointelligence.backend.liveauction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class LiveAuctionControllerTest {
    @Test
    void delegatesReadOnlyDecisionRequest() {
        LiveAuctionDecisionService service = mock(LiveAuctionDecisionService.class);
        LiveAuctionController controller = new LiveAuctionController(service);
        var request = new LiveAuctionDecisionRequest("2026/2027", 8, 1000, 1000,
                false, "D", 1L, 10, null, List.of(), null, null, 10, List.of());
        var expected = new LiveAuctionDecisionResponse(LiveAuctionAction.INSUFFICIENT_EVIDENCE,
                1L, null, "D", 10, null, null, null, null, 1000, 990, null, null,
                null, null, null, BigDecimal.ZERO, LiveAuctionEvidenceStatus.INSUFFICIENT_EVIDENCE,
                List.of(), List.of(), List.of());
        when(service.decide(request)).thenReturn(expected);
        assertThat(controller.decide(request).getBody()).isSameAs(expected);
        verify(service).decide(request);
    }
}
