package it.fantacalciointelligence.backend.ingestion;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SourceIngestionSecurityTest {
    @Autowired MockMvc mvc;@MockitoBean SourceIngestionService service;
    @Test void anonymousRejected()throws Exception{mvc.perform(post("/api/v1/admin/source-ingestion/run")).andExpect(status().isUnauthorized());}
    @Test void userForbidden()throws Exception{mvc.perform(post("/api/v1/admin/source-ingestion/run").with(httpBasic("user-test","user-pass")))
            .andExpect(status().isForbidden());}
    @Test void adminReachesControllerAndNoUrlParameterExists()throws Exception{when(service.ingestAll("2026/2027")).thenReturn(List.of());
        mvc.perform(post("/api/v1/admin/source-ingestion/run").with(httpBasic("admin-test","admin-pass")))
            .andExpect(status().isOk());}
}
