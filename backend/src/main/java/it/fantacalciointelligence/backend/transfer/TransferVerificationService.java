package it.fantacalciointelligence.backend.transfer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.common.BadRequestException;
import it.fantacalciointelligence.backend.common.ConflictException;
import it.fantacalciointelligence.backend.common.ResourceNotFoundException;

@Service
public class TransferVerificationService {

    private static final Set<String> ALLOWED_SOURCES = Set.of(
            "FIGC",
            "LEGA_SERIE_A",
            "OFFICIAL_CLUB",
            "FANTACALCIO",
            "GAZZETTA",
            "FANTAMARKT"
    );

    private static final Set<String> OFFICIAL_SOURCES = Set.of(
            "FIGC",
            "LEGA_SERIE_A",
            "OFFICIAL_CLUB"
    );

    private static final String CLAIM_SELECT = """
            SELECT
                claim.id,
                claim.player_id,
                player.display_name AS player_name,
                season.code AS season,
                from_club.name AS from_club,
                to_club.name AS to_club,
                claim.destination_club_name,
                claim.source,
                claim.source_reference,
                claim.source_published_at,
                claim.verification_source,
                claim.verification_reference,
                claim.verification_published_at,
                claim.observed_at,
                claim.effective_at,
                claim.verification_status,
                claim.verified_at,
                claim.rejection_reason
            FROM player_transfer_claims claim
            JOIN players player ON player.id = claim.player_id
            JOIN seasons season ON season.id = claim.season_id
            JOIN clubs from_club ON from_club.id = claim.from_club_id
            LEFT JOIN clubs to_club ON to_club.id = claim.to_club_id
            """;

    private final JdbcClient jdbcClient;

    public TransferVerificationService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional
    public TransferClaimResponse createClaim(TransferClaimRequest request) {
        validateCreateRequest(request);

        String source = normalizeSource(request.source());
        Long seasonId = findSeasonId(request.season());
        requirePlayer(request.playerId());

        Map<String, Object> currentRegistration = findCurrentActiveRegistration(
                request.playerId(),
                seasonId
        );
        Long fromClubId = numberValue(currentRegistration, "club_id");
        String fromClub = stringValue(currentRegistration, "club_name");

        Long toClubId = null;
        String destinationClubName;
        if (request.leavingSerieA()) {
            destinationClubName = requireText(
                    request.destinationClubName(),
                    "La destinazione fuori dalla Serie A e' obbligatoria"
            );
        } else {
            String targetClub = requireText(
                    request.targetClub(),
                    "La nuova squadra di Serie A e' obbligatoria"
            );
            Map<String, Object> targetClubRow = findSeasonClub(targetClub, seasonId);
            toClubId = numberValue(targetClubRow, "id");
            destinationClubName = stringValue(targetClubRow, "name");
            if (fromClubId.equals(toClubId)) {
                throw new ConflictException(
                        "La squadra proposta coincide con la squadra corrente: " + fromClub
                );
            }
        }

        Long claimId = insertClaim(
                request,
                source,
                seasonId,
                fromClubId,
                toClubId,
                destinationClubName
        );
        return findById(claimId);
    }

    /** Compatibility path for claims originally created from an official source. */
    @Transactional
    public TransferClaimResponse verifyOfficial(Long claimId) {
        TransferClaimRow claim = findClaimRowForUpdate(claimId);
        if ("OFFICIAL".equals(claim.verificationStatus())) {
            return findById(claimId);
        }
        if (!OFFICIAL_SOURCES.contains(claim.source())) {
            throw new ConflictException(
                    "La fonte " + claim.source() + " non e' una fonte ufficiale"
            );
        }
        OfficialTransferVerificationRequest request = new OfficialTransferVerificationRequest(
                claim.source(),
                claim.sourceReference(),
                claim.sourcePublishedAt(),
                claim.effectiveAt()
        );
        return confirmOfficial(claim, request);
    }

    @Transactional
    public TransferClaimResponse verifyOfficial(
            Long claimId,
            OfficialTransferVerificationRequest request
    ) {
        TransferClaimRow claim = findClaimRowForUpdate(claimId);
        return confirmOfficial(claim, request);
    }

    private TransferClaimResponse confirmOfficial(
            TransferClaimRow claim,
            OfficialTransferVerificationRequest request
    ) {
        if ("OFFICIAL".equals(claim.verificationStatus())) {
            return findById(claim.id());
        }
        if ("REJECTED".equals(claim.verificationStatus())) {
            throw new ConflictException(
                    "Una segnalazione rifiutata non puo' diventare ufficiale"
            );
        }

        ValidatedOfficialEvidence evidence = validateOfficialEvidence(request);
        if (claim.toClubId() != null) {
            requireSeasonMembership(claim.toClubId(), claim.seasonId());
        }

        lockAndValidateCurrentRegistration(claim);
        if (claim.toClubId() == null) {
            applySerieAExit(claim, evidence.effectiveAt());
        } else {
            applySerieATransfer(claim, evidence.effectiveAt());
        }

        markOfficial(claim.id(), evidence);
        saveRegistrationEvent(claim, evidence);
        rejectSupersededClaims(claim);
        return findById(claim.id());
    }

    @Transactional
    public TransferClaimResponse reject(Long claimId, String reason) {
        TransferClaimRow claim = findClaimRowForUpdate(claimId);
        if ("OFFICIAL".equals(claim.verificationStatus())) {
            throw new ConflictException(
                    "Un trasferimento gia' ufficiale non puo' essere rifiutato"
            );
        }
        if ("REJECTED".equals(claim.verificationStatus())) {
            return findById(claimId);
        }

        String rejectionReason = requireText(reason, "Motivo del rifiuto obbligatorio");
        jdbcClient.sql("""
                UPDATE player_transfer_claims
                SET verification_status = 'REJECTED',
                    rejection_reason = :reason,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :claimId
                """)
                .param("reason", rejectionReason)
                .param("claimId", claimId)
                .update();
        return findById(claimId);
    }

    public List<TransferClaimResponse> findPending(String season) {
        return jdbcClient.sql(CLAIM_SELECT + """
                WHERE season.code = :season
                  AND claim.verification_status IN ('DETECTED', 'PENDING_VERIFICATION')
                ORDER BY claim.observed_at DESC, claim.id DESC
                """)
                .param("season", season)
                .query((resultSet, rowNumber) -> mapResponse(resultSet))
                .list();
    }

    public TransferClaimResponse findById(Long claimId) {
        return jdbcClient.sql(CLAIM_SELECT + " WHERE claim.id = :claimId")
                .param("claimId", claimId)
                .query((resultSet, rowNumber) -> mapResponse(resultSet))
                .optional()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Segnalazione trasferimento non trovata: " + claimId
                ));
    }

    private void validateCreateRequest(TransferClaimRequest request) {
        if (request == null) {
            throw new BadRequestException("Richiesta trasferimento mancante");
        }
        if (request.playerId() == null) {
            throw new BadRequestException("playerId obbligatorio");
        }
        requireText(request.season(), "Stagione obbligatoria");

        String source = normalizeSource(requireText(request.source(), "Fonte obbligatoria"));
        if (!ALLOWED_SOURCES.contains(source)) {
            throw new BadRequestException("Fonte trasferimento non supportata: " + source);
        }
        requireText(request.sourceReference(), "Riferimento alla fonte obbligatorio");

        if (request.leavingSerieA()) {
            if (request.targetClub() != null && !request.targetClub().isBlank()) {
                throw new BadRequestException(
                        "Un giocatore che lascia la Serie A non puo' avere targetClub"
                );
            }
            requireText(
                    request.destinationClubName(),
                    "La destinazione fuori dalla Serie A e' obbligatoria"
            );
        } else {
            requireText(request.targetClub(), "La nuova squadra di Serie A e' obbligatoria");
        }
    }

    private ValidatedOfficialEvidence validateOfficialEvidence(
            OfficialTransferVerificationRequest request
    ) {
        if (request == null) {
            throw new BadRequestException("Evidenza ufficiale mancante");
        }
        String source = normalizeSource(requireText(request.source(), "Fonte ufficiale obbligatoria"));
        if (!OFFICIAL_SOURCES.contains(source)) {
            throw new BadRequestException("Fonte di verifica non ufficiale: " + source);
        }
        String reference = requireText(
                request.sourceReference(),
                "Riferimento ufficiale obbligatorio"
        );
        if (request.effectiveAt() == null) {
            throw new BadRequestException("Data effettiva del trasferimento obbligatoria");
        }
        return new ValidatedOfficialEvidence(
                source,
                reference,
                request.sourcePublishedAt(),
                request.effectiveAt()
        );
    }

    private Long insertClaim(
            TransferClaimRequest request,
            String source,
            Long seasonId,
            Long fromClubId,
            Long toClubId,
            String destinationClubName
    ) {
        return jdbcClient.sql("""
                INSERT INTO player_transfer_claims (
                    player_id, season_id, from_club_id, to_club_id,
                    destination_club_name, source, source_reference,
                    source_published_at, effective_at, verification_status
                ) VALUES (
                    :playerId, :seasonId, :fromClubId, :toClubId,
                    :destinationClubName, :source, :sourceReference,
                    :sourcePublishedAt, :effectiveAt, 'DETECTED'
                )
                RETURNING id
                """)
                .param("playerId", request.playerId())
                .param("seasonId", seasonId)
                .param("fromClubId", fromClubId)
                .param("toClubId", toClubId, Types.BIGINT)
                .param("destinationClubName", destinationClubName)
                .param("source", source)
                .param("sourceReference", request.sourceReference().trim())
                .param("sourcePublishedAt", request.sourcePublishedAt(), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("effectiveAt", request.effectiveAt(), Types.DATE)
                .query(Long.class)
                .single();
    }

    private void lockAndValidateCurrentRegistration(TransferClaimRow claim) {
        RegistrationState registration = jdbcClient.sql("""
                SELECT club_id, registration_status
                FROM player_registrations
                WHERE player_id = :playerId
                  AND season_id = :seasonId
                FOR UPDATE
                """)
                .param("playerId", claim.playerId())
                .param("seasonId", claim.seasonId())
                .query((resultSet, rowNumber) -> new RegistrationState(
                        resultSet.getLong("club_id"),
                        resultSet.getString("registration_status")
                ))
                .optional()
                .orElseThrow(() -> new ConflictException(
                        "Registrazione corrente del giocatore non trovata"
                ));

        if (!"ACTIVE".equals(registration.status())
                || !claim.fromClubId().equals(registration.clubId())) {
            throw new ConflictException(
                    "La registrazione corrente non corrisponde allo stato di partenza della claim"
            );
        }
    }

    private void applySerieATransfer(TransferClaimRow claim, LocalDate effectiveAt) {
        int updated = jdbcClient.sql("""
                UPDATE player_registrations
                SET club_id = :toClubId,
                    joined_at = :effectiveAt,
                    left_at = NULL,
                    registration_status = 'ACTIVE',
                    updated_at = CURRENT_TIMESTAMP
                WHERE player_id = :playerId
                  AND season_id = :seasonId
                  AND club_id = :fromClubId
                  AND registration_status = 'ACTIVE'
                """)
                .param("toClubId", claim.toClubId())
                .param("effectiveAt", effectiveAt)
                .param("playerId", claim.playerId())
                .param("seasonId", claim.seasonId())
                .param("fromClubId", claim.fromClubId())
                .update();
        requireSingleRegistrationUpdate(updated);
    }

    private void applySerieAExit(TransferClaimRow claim, LocalDate effectiveAt) {
        int updated = jdbcClient.sql("""
                UPDATE player_registrations
                SET left_at = :effectiveAt,
                    registration_status = 'RELEASED',
                    updated_at = CURRENT_TIMESTAMP
                WHERE player_id = :playerId
                  AND season_id = :seasonId
                  AND club_id = :fromClubId
                  AND registration_status = 'ACTIVE'
                """)
                .param("effectiveAt", effectiveAt)
                .param("playerId", claim.playerId())
                .param("seasonId", claim.seasonId())
                .param("fromClubId", claim.fromClubId())
                .update();
        requireSingleRegistrationUpdate(updated);
    }

    private void requireSingleRegistrationUpdate(int updated) {
        if (updated != 1) {
            throw new ConflictException("Registrazione del giocatore non aggiornata");
        }
    }

    private void markOfficial(Long claimId, ValidatedOfficialEvidence evidence) {
        jdbcClient.sql("""
                UPDATE player_transfer_claims
                SET verification_status = 'OFFICIAL',
                    verification_source = :verificationSource,
                    verification_reference = :verificationReference,
                    verification_published_at = :verificationPublishedAt,
                    effective_at = :effectiveAt,
                    verified_at = CURRENT_TIMESTAMP,
                    rejection_reason = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :claimId
                """)
                .param("verificationSource", evidence.source())
                .param("verificationReference", evidence.reference())
                .param(
                        "verificationPublishedAt",
                        evidence.publishedAt(),
                        Types.TIMESTAMP_WITH_TIMEZONE
                )
                .param("effectiveAt", evidence.effectiveAt())
                .param("claimId", claimId)
                .update();
    }

    private void saveRegistrationEvent(
            TransferClaimRow claim,
            ValidatedOfficialEvidence evidence
    ) {
        String eventType = claim.toClubId() == null
                ? "LEFT_SERIE_A"
                : "TRANSFER_IN_SERIE_A";
        jdbcClient.sql("""
                INSERT INTO player_registration_events (
                    player_id, season_id, from_club_id, to_club_id,
                    destination_club_name, event_type, effective_at,
                    transfer_claim_id, source, source_reference
                ) VALUES (
                    :playerId, :seasonId, :fromClubId, :toClubId,
                    :destinationClubName, :eventType, :effectiveAt,
                    :claimId, :source, :sourceReference
                )
                """)
                .param("playerId", claim.playerId())
                .param("seasonId", claim.seasonId())
                .param("fromClubId", claim.fromClubId())
                .param("toClubId", claim.toClubId(), Types.BIGINT)
                .param("destinationClubName", claim.destinationClubName())
                .param("eventType", eventType)
                .param("effectiveAt", evidence.effectiveAt())
                .param("claimId", claim.id())
                .param("source", evidence.source())
                .param("sourceReference", evidence.reference())
                .update();
    }

    private void rejectSupersededClaims(TransferClaimRow officialClaim) {
        jdbcClient.sql("""
                UPDATE player_transfer_claims
                SET verification_status = 'REJECTED',
                    rejection_reason = :reason,
                    updated_at = CURRENT_TIMESTAMP
                WHERE player_id = :playerId
                  AND season_id = :seasonId
                  AND from_club_id = :fromClubId
                  AND id <> :officialClaimId
                  AND verification_status IN ('DETECTED', 'PENDING_VERIFICATION')
                """)
                .param("reason", "Superseded by official transfer claim " + officialClaim.id())
                .param("playerId", officialClaim.playerId())
                .param("seasonId", officialClaim.seasonId())
                .param("fromClubId", officialClaim.fromClubId())
                .param("officialClaimId", officialClaim.id())
                .update();
    }

    private Long findSeasonId(String season) {
        return jdbcClient.sql("SELECT id FROM seasons WHERE code = :season")
                .param("season", season)
                .query(Long.class)
                .optional()
                .orElseThrow(() -> new ResourceNotFoundException("Stagione non trovata: " + season));
    }

    private void requirePlayer(Long playerId) {
        boolean exists = jdbcClient.sql("SELECT EXISTS(SELECT 1 FROM players WHERE id = :playerId)")
                .param("playerId", playerId)
                .query(Boolean.class)
                .single();
        if (!exists) {
            throw new ResourceNotFoundException("Giocatore non trovato: " + playerId);
        }
    }

    private Map<String, Object> findCurrentActiveRegistration(Long playerId, Long seasonId) {
        return jdbcClient.sql("""
                SELECT registration.club_id, club.name AS club_name
                FROM player_registrations registration
                JOIN clubs club ON club.id = registration.club_id
                WHERE registration.player_id = :playerId
                  AND registration.season_id = :seasonId
                  AND registration.registration_status = 'ACTIVE'
                """)
                .param("playerId", playerId)
                .param("seasonId", seasonId)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Registrazione ACTIVE del giocatore non trovata"
                ));
    }

    private Map<String, Object> findSeasonClub(String club, Long seasonId) {
        return jdbcClient.sql("""
                SELECT club.id, club.name
                FROM season_clubs membership
                JOIN clubs club ON club.id = membership.club_id
                WHERE membership.season_id = :seasonId
                  AND (
                      LOWER(club.name) = LOWER(:club)
                      OR LOWER(club.short_name) = LOWER(:club)
                      OR LOWER(club.slug) = LOWER(:club)
                  )
                ORDER BY club.id
                LIMIT 1
                """)
                .param("seasonId", seasonId)
                .param("club", club)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Squadra non appartenente alla stagione richiesta: " + club
                ));
    }

    private void requireSeasonMembership(Long clubId, Long seasonId) {
        boolean member = jdbcClient.sql("""
                SELECT EXISTS(
                    SELECT 1 FROM season_clubs
                    WHERE season_id = :seasonId AND club_id = :clubId
                )
                """)
                .param("seasonId", seasonId)
                .param("clubId", clubId)
                .query(Boolean.class)
                .single();
        if (!member) {
            throw new ConflictException("Il club target non appartiene alla stagione della claim");
        }
    }

    private TransferClaimRow findClaimRowForUpdate(Long claimId) {
        return jdbcClient.sql("""
                SELECT
                    id, player_id, season_id, from_club_id, to_club_id,
                    destination_club_name, source, source_reference,
                    source_published_at, effective_at, verification_status
                FROM player_transfer_claims
                WHERE id = :claimId
                FOR UPDATE
                """)
                .param("claimId", claimId)
                .query((resultSet, rowNumber) -> new TransferClaimRow(
                        resultSet.getLong("id"),
                        resultSet.getLong("player_id"),
                        resultSet.getLong("season_id"),
                        resultSet.getLong("from_club_id"),
                        nullableLong(resultSet, "to_club_id"),
                        resultSet.getString("destination_club_name"),
                        resultSet.getString("source"),
                        resultSet.getString("source_reference"),
                        resultSet.getObject("source_published_at", OffsetDateTime.class),
                        resultSet.getObject("effective_at", LocalDate.class),
                        resultSet.getString("verification_status")
                ))
                .optional()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Segnalazione trasferimento non trovata: " + claimId
                ));
    }

    private TransferClaimResponse mapResponse(ResultSet resultSet) throws SQLException {
        return new TransferClaimResponse(
                resultSet.getLong("id"),
                resultSet.getLong("player_id"),
                resultSet.getString("player_name"),
                resultSet.getString("season"),
                resultSet.getString("from_club"),
                resultSet.getString("to_club"),
                resultSet.getString("destination_club_name"),
                resultSet.getString("source"),
                resultSet.getString("source_reference"),
                resultSet.getObject("source_published_at", OffsetDateTime.class),
                resultSet.getString("verification_source"),
                resultSet.getString("verification_reference"),
                resultSet.getObject("verification_published_at", OffsetDateTime.class),
                resultSet.getObject("observed_at", OffsetDateTime.class),
                resultSet.getObject("effective_at", LocalDate.class),
                resultSet.getString("verification_status"),
                resultSet.getObject("verified_at", OffsetDateTime.class),
                resultSet.getString("rejection_reason")
        );
    }

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(message);
        }
        return value.trim();
    }

    private String normalizeSource(String source) {
        return source.trim().toUpperCase(Locale.ROOT);
    }

    private Long numberValue(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    private String stringValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private record TransferClaimRow(
            Long id,
            Long playerId,
            Long seasonId,
            Long fromClubId,
            Long toClubId,
            String destinationClubName,
            String source,
            String sourceReference,
            OffsetDateTime sourcePublishedAt,
            LocalDate effectiveAt,
            String verificationStatus
    ) {
    }

    private record RegistrationState(Long clubId, String status) {
    }

    private record ValidatedOfficialEvidence(
            String source,
            String reference,
            OffsetDateTime publishedAt,
            LocalDate effectiveAt
    ) {
    }
}
