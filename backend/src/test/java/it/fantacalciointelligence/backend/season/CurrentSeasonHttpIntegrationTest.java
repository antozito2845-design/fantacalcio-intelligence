package it.fantacalciointelligence.backend.season;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import it.fantacalciointelligence.backend.common.PageResponse;
import it.fantacalciointelligence.backend.player.PlayerCatalogService;
import it.fantacalciointelligence.backend.player.PlayerDetailService;

@SpringBootTest
@AutoConfigureMockMvc
class CurrentSeasonHttpIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean PlayerCatalogService catalog;
    @MockitoBean PlayerDetailService detail;

    @Test
    void missingSeasonUsesDatabaseCurrentSeason() throws Exception {
        when(catalog.findPlayers(any(), any(), any(), any(), any(Integer.class), any(Integer.class)))
                .thenReturn(new PageResponse<>(List.of(), 0, 25, 0, 0));
        mvc.perform(get("/api/v1/players")).andExpect(status().isOk());
        verify(catalog).findPlayers(eq("2026/2027"), any(), any(), any(), eq(0), eq(25));
    }

    @Test
    void explicitSeasonOverridesDatabaseCurrentSeason() throws Exception {
        when(catalog.findPlayers(any(), any(), any(), any(), any(Integer.class), any(Integer.class)))
                .thenReturn(new PageResponse<>(List.of(), 0, 25, 0, 0));
        mvc.perform(get("/api/v1/players").param("season", "2025/2026"))
                .andExpect(status().isOk());
        verify(catalog).findPlayers(eq("2025/2026"), any(), any(), any(), eq(0), eq(25));
    }
}
