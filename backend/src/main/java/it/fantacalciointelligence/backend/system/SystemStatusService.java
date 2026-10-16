package it.fantacalciointelligence.backend.system;

import java.time.OffsetDateTime;
import java.util.Arrays;

import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class SystemStatusService {

    private final JdbcClient jdbcClient;
    private final Environment environment;

    public SystemStatusService(JdbcClient jdbcClient, Environment environment) {
        this.jdbcClient = jdbcClient;
        this.environment = environment;
    }

    public SystemStatusResponse getStatus() {
        String runtimeEnvironment = resolveRuntimeEnvironment(environment);

        return jdbcClient.sql("""
                SELECT application_name, application_version
                FROM system_information
                ORDER BY id DESC
                LIMIT 1
                """)
                .query((resultSet, rowNumber) -> new SystemStatusResponse(
                        "UP",
                        resultSet.getString("application_name"),
                        resultSet.getString("application_version"),
                        runtimeEnvironment,
                        OffsetDateTime.now()
                ))
                .single();
    }

    static String resolveRuntimeEnvironment(Environment environment) {
        String[] activeProfiles = environment.getActiveProfiles();
        if (Arrays.asList(activeProfiles).contains("prod")) {
            return "production";
        }
        if (Arrays.asList(activeProfiles).contains("dev")) {
            return "development";
        }
        if (activeProfiles.length > 0) {
            return activeProfiles[0];
        }

        return Arrays.stream(environment.getDefaultProfiles())
                .findFirst()
                .orElse("default");
    }
}
