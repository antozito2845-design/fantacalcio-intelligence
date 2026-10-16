package it.fantacalciointelligence.backend.reconciliation.preview;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.common.ConflictException;
import it.fantacalciointelligence.backend.reconciliation.identity.PlayerIdentityResolutionService;
import it.fantacalciointelligence.backend.reconciliation.identity.ResolvedPlayerIdentity;

@Service
public class ReconciliationPreviewService {

    private static final String FANTACALCIO =
            "FANTACALCIO";

    private static final String GAZZETTA =
            "GAZZETTA";

    private static final String FANTAMARKT =
            "FANTAMARKT";

    private static final String CONFIRMED =
            "CONFIRMED";

    private static final String UNRESOLVED =
            "UNRESOLVED";

    private final JdbcClient jdbcClient;

    private final PlayerIdentityResolutionService
            identityResolutionService;

    public ReconciliationPreviewService(
            JdbcClient jdbcClient,
            PlayerIdentityResolutionService
                    identityResolutionService
    ) {
        this.jdbcClient = jdbcClient;
        this.identityResolutionService =
                identityResolutionService;
    }

    @Transactional(readOnly = true)
    public ReconciliationPreviewResponse preview(
            long fantacalcioBatchId,
            long gazzettaBatchId,
            Long fantamarktBatchId,
            boolean onlyUnresolved
    ) {

        validateBatch(
                fantacalcioBatchId,
                FANTACALCIO
        );

        validateBatch(
                gazzettaBatchId,
                GAZZETTA
        );

        if (fantamarktBatchId != null) {
            validateBatch(
                    fantamarktBatchId,
                    FANTAMARKT
            );
        }

        List<ResolvedPlayerIdentity> identities =
                identityResolutionService.resolve(
                        fantacalcioBatchId,
                        gazzettaBatchId,
                        fantamarktBatchId
                );

        int confirmedPlayers =
                (int) identities.stream()
                        .filter(this::isConfirmed)
                        .count();

        int unresolvedPlayers =
                identities.size()
                        - confirmedPlayers;

        Map<String, Integer> methodCounts =
                buildMethodCounts(
                        identities
                );

        List<ReconciliationPreviewPlayer> players =
                identities.stream()
                        .filter(identity ->
                                !onlyUnresolved
                                        || !isConfirmed(
                                                identity
                                        )
                        )
                        .map(this::toPreviewPlayer)
                        .toList();

        return new ReconciliationPreviewResponse(
                fantacalcioBatchId,
                gazzettaBatchId,
                fantamarktBatchId,
                identities.size(),
                confirmedPlayers,
                unresolvedPlayers,
                methodCounts,
                players
        );
    }

    private boolean isConfirmed(
            ResolvedPlayerIdentity identity
    ) {
        return identity.sourcesCount() >= 2;
    }

    private Map<String, Integer> buildMethodCounts(
            List<ResolvedPlayerIdentity> identities
    ) {

        Map<String, Integer> counts =
                new TreeMap<>();

        for (ResolvedPlayerIdentity identity
                : identities) {

            String key =
                    isConfirmed(identity)
                            ? safeMethod(
                                    identity
                                            .identityResolutionMethod()
                            )
                            : UNRESOLVED;

            counts.merge(
                    key,
                    1,
                    Integer::sum
            );
        }

        return new LinkedHashMap<>(
                counts
        );
    }

    private ReconciliationPreviewPlayer
            toPreviewPlayer(
                    ResolvedPlayerIdentity identity
            ) {

        return new ReconciliationPreviewPlayer(
                isConfirmed(identity)
                        ? CONFIRMED
                        : UNRESOLVED,

                identity.identityResolutionMethod(),
                identity.sourcesCount(),

                identity.fantacalcioExternalId(),
                identity.fantacalcioName(),
                identity.fantacalcioClub(),

                identity.gazzettaExternalId(),
                identity.gazzettaName(),
                identity.gazzettaClub(),

                identity.fantamarktExternalId(),
                identity.fantamarktName(),
                identity.fantamarktClub()
        );
    }

    private void validateBatch(
            long batchId,
            String expectedSource
    ) {

        List<Map<String, Object>> batches =
                jdbcClient.sql("""
                        SELECT
                            id,
                            source,
                            status

                        FROM import_batches

                        WHERE id = :batchId
                        """)
                        .param(
                                "batchId",
                                batchId
                        )
                        .query()
                        .listOfRows();

        if (batches.isEmpty()) {
            throw new ConflictException(
                    "Batch non trovato: "
                            + batchId
            );
        }

        Map<String, Object> batch =
                batches.getFirst();

        String actualSource =
                String.valueOf(
                        batch.get("source")
                );

        String status =
                String.valueOf(
                        batch.get("status")
                );

        if (!expectedSource.equals(
                actualSource
        )) {
            throw new ConflictException(
                    "Batch "
                            + batchId
                            + " atteso come "
                            + expectedSource
                            + " ma trovato "
                            + actualSource
            );
        }

        if (!"COMPLETED".equals(status)) {
            throw new ConflictException(
                    "Batch "
                            + batchId
                            + " non completato: "
                            + status
            );
        }
    }

    private String safeMethod(
            String value
    ) {
        if (value == null
                || value.isBlank()) {
            return "UNKNOWN";
        }

        return value.trim();
    }
}