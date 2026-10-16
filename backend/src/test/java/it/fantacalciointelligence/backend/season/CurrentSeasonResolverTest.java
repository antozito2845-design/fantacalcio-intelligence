package it.fantacalciointelligence.backend.season;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class CurrentSeasonResolverTest {

    @Test
    void resolvesExactlyOneCurrentSeason() {
        assertThat(resolver(List.of("2027/2028")).resolve()).isEqualTo("2027/2028");
    }

    @Test
    void rejectsZeroCurrentSeasons() {
        assertThatThrownBy(() -> resolver(List.of()).resolve())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("trovate: 0");
    }

    @Test
    void rejectsMultipleCurrentSeasons() {
        assertThatThrownBy(() -> resolver(List.of("2026/2027", "2027/2028")).resolve())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("trovate: 2");
    }

    private CurrentSeasonResolver resolver(List<String> values) {
        JdbcClient jdbc = mock(JdbcClient.class);
        JdbcClient.StatementSpec statement = mock(JdbcClient.StatementSpec.class);
        @SuppressWarnings("unchecked")
        JdbcClient.MappedQuerySpec<String> query = mock(JdbcClient.MappedQuerySpec.class);
        when(jdbc.sql(anyString())).thenReturn(statement);
        when(statement.query(String.class)).thenReturn(query);
        when(query.list()).thenReturn(values);
        return new CurrentSeasonResolver(jdbc);
    }
}
