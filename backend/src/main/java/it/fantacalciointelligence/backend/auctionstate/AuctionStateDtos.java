package it.fantacalciointelligence.backend.auctionstate;

import java.time.OffsetDateTime;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class AuctionStateDtos {

    private AuctionStateDtos() {
    }

    public record CreateSessionRequest(
            @NotNull
            @Positive
            Long squadId
    ) {
    }

    public record NominationRequest(
            @NotNull
            @Positive
            Long playerId,

            @NotNull
            @Min(1)
            Integer currentPrice
    ) {
    }

    public record PurchaseRequest(
            @NotNull
            @Positive
            Long playerId,

            @NotNull
            AuctionBuyerType buyerType,

            Integer paidPrice,

            @NotBlank
            @Size(max = 120)
            String idempotencyKey
    ) {
    }

    public record SessionDecisionRequest(
            @NotNull
            @Min(1)
            Integer proposedBid,

            @Min(1)
            @Max(38)
            Integer currentMatchday,

            @Min(1)
            @Max(10)
            Integer horizon,

            @Min(1)
            @Max(20)
            Integer alternativeLimit
    ) {
    }

    public record SessionResponse(
            Long id,
            Long squadId,
            String season,
            int leagueTeams,
            int totalBudget,
            boolean defenseModifier,
            AuctionSessionStatus status,
            boolean playerStateInitialized,
            Long currentPlayerId,
            Integer currentPrice,
            long version,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record PurchaseResponse(
            Long id,
            Long sessionId,
            Long playerId,
            AuctionBuyerType buyerType,
            Integer paidPrice,
            String idempotencyKey,
            OffsetDateTime createdAt
    ) {
    }

    public record PlayerStateResponse(
            Long playerId,
            String playerName,
            String role,
            AuctionPlayerAvailability availability
    ) {
    }

    public record StateResponse(
            SessionResponse session,
            int spentBudget,
            int remainingBudget,
            List<PurchaseResponse> purchases,
            List<PlayerStateResponse> players,
            boolean availabilityComplete
    ) {
    }
}