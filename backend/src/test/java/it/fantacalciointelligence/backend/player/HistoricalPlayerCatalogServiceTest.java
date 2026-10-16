package it.fantacalciointelligence.backend.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import it.fantacalciointelligence.backend.common.PageResponse;

class HistoricalPlayerCatalogServiceTest {

    @SuppressWarnings({
            "unchecked",
            "rawtypes"
    })
    @Test
    void shouldBuildHistoricalIdentityFirstPageWithoutRegistrations() {
        NamedParameterJdbcTemplate jdbcTemplate =
                mock(NamedParameterJdbcTemplate.class);

        HistoricalPlayerCatalogResponse expected =
                new HistoricalPlayerCatalogResponse(
                        653L,
                        "Andrea Belotti",
                        false,
                        "2024/2025",
                        null,
                        null,
                        "A",
                        24,
                        new BigDecimal("6.10"),
                        5,
                        2
                );

        when(
                jdbcTemplate.query(
                        anyString(),
                        anyMap(),
                        any(RowMapper.class)
                )
        ).thenReturn(List.of(expected));

        when(
                jdbcTemplate.queryForObject(
                        anyString(),
                        anyMap(),
                        eq(Long.class)
                )
        ).thenReturn(679L);

        HistoricalPlayerCatalogService service =
                new HistoricalPlayerCatalogService(
                        jdbcTemplate
                );

        PageResponse<HistoricalPlayerCatalogResponse> result =
                service.findPlayers(
                        "2024/2025",
                        "Belotti",
                        "A",
                        0,
                        25
                );

        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(25);
        assertThat(result.totalElements()).isEqualTo(679);
        assertThat(result.totalPages()).isEqualTo(28);
        assertThat(result.content())
                .containsExactly(expected);
    }

    @Test
    void shouldClampPaginationBoundaries() {
        NamedParameterJdbcTemplate jdbcTemplate =
                mock(NamedParameterJdbcTemplate.class);

        when(
                jdbcTemplate.query(
                        anyString(),
                        anyMap(),
                        any(RowMapper.class)
                )
        ).thenReturn(List.of());

        when(
                jdbcTemplate.queryForObject(
                        anyString(),
                        anyMap(),
                        eq(Long.class)
                )
        ).thenReturn(0L);

        HistoricalPlayerCatalogService service =
                new HistoricalPlayerCatalogService(
                        jdbcTemplate
                );

        PageResponse<HistoricalPlayerCatalogResponse> result =
                service.findPlayers(
                        "2025/2026",
                        null,
                        null,
                        -5,
                        1000
                );

        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(100);
        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
    }

    @Test
    void shouldRejectMissingHistoricalSeason() {
        NamedParameterJdbcTemplate jdbcTemplate =
                mock(NamedParameterJdbcTemplate.class);

        HistoricalPlayerCatalogService service =
                new HistoricalPlayerCatalogService(
                        jdbcTemplate
                );

        org.assertj.core.api.Assertions
                .assertThatThrownBy(
                        () -> service.findPlayers(
                                " ",
                                null,
                                null,
                                0,
                                25
                        )
                )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stagione");
    }
}
