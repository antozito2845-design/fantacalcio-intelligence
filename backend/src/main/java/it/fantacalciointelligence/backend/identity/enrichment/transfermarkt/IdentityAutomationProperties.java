package it.fantacalciointelligence.backend.identity.enrichment.transfermarkt;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.identity-enrichment.automation")
public class IdentityAutomationProperties {

    private boolean enabled = false;

    private int batchLimit = 10;

    private Duration failedRetry =
            Duration.ofDays(1);

    private Duration unresolvedRetry =
            Duration.ofDays(7);

    private Duration matchedRetry =
            Duration.ofDays(30);

    public void validateEnabledConfiguration() {

        if (!enabled) {
            return;
        }

        if (batchLimit < 1 || batchLimit > 50) {
            throw new IllegalStateException(
                    "app.identity-enrichment.automation.batch-limit deve essere compreso tra 1 e 50"
            );
        }

        validateDuration(
                failedRetry,
                "failed-retry"
        );

        validateDuration(
                unresolvedRetry,
                "unresolved-retry"
        );

        validateDuration(
                matchedRetry,
                "matched-retry"
        );
    }

    private void validateDuration(
            Duration duration,
            String property
    ) {

        if (
                duration == null
                        || duration.isZero()
                        || duration.isNegative()
        ) {

            throw new IllegalStateException(
                    "app.identity-enrichment.automation."
                            + property
                            + " deve essere maggiore di zero"
            );
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getBatchLimit() {
        return batchLimit;
    }

    public void setBatchLimit(int batchLimit) {
        this.batchLimit = batchLimit;
    }

    public Duration getFailedRetry() {
        return failedRetry;
    }

    public void setFailedRetry(Duration failedRetry) {
        this.failedRetry = failedRetry;
    }

    public Duration getUnresolvedRetry() {
        return unresolvedRetry;
    }

    public void setUnresolvedRetry(
            Duration unresolvedRetry
    ) {
        this.unresolvedRetry = unresolvedRetry;
    }

    public Duration getMatchedRetry() {
        return matchedRetry;
    }

    public void setMatchedRetry(
            Duration matchedRetry
    ) {
        this.matchedRetry = matchedRetry;
    }
}