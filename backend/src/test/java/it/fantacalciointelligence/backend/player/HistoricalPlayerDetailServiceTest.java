package it.fantacalciointelligence.backend.player;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class HistoricalPlayerDetailServiceTest {

    @Test
    void shouldRejectMissingHistoricalSeasonBeforeDatabaseAccess() {
        NamedParameterJdbcTemplate jdbcTemplate =
                mock(NamedParameterJdbcTemplate.class);

        HistoricalPlayerDetailService service =
                new HistoricalPlayerDetailService(
                        jdbcTemplate
                );

        assertThatThrownBy(
                () -> service.findById(
                        653L,
                        " "
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stagione");

        verifyNoInteractions(jdbcTemplate);
    }
}
