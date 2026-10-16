import { z } from 'zod';
import { httpRequest } from '@/api/client/httpClient';

const numeric = z.number();
const nullableNumber = numeric.nullable();
const playerSchema = z.object({
  id: numeric,
  name: z.string(),
  club: z.string(),
  clubSlug: z.string(),
  fantacalcioRole: z.string(),
  gazzettaRole: z.string().nullable(),
  fantamarktRole: z.string().nullable(),
  sourcesCount: numeric,
});
export const playerPageSchema = z.object({
  content: z.array(playerSchema),
  page: numeric,
  size: numeric,
  totalElements: numeric,
  totalPages: numeric,
});

const historicalPlayerSchema = z.object({
  id: numeric,
  name: z.string(),
  active: z.boolean(),
  season: z.string(),
  club: z.string().nullable(),
  clubSlug: z.string().nullable(),
  fantacalcioRole: z.string(),
  appearances: nullableNumber,
  fantasyAverage: nullableNumber,
  goals: nullableNumber,
  assists: nullableNumber,
});

export const historicalPlayerPageSchema = z.object({
  content: z.array(historicalPlayerSchema),
  page: numeric,
  size: numeric,
  totalElements: numeric,
  totalPages: numeric,
});
const evaluationSchema = z
  .object({
    projectedFantasyAverage: nullableNumber,
    projectedAppearances: nullableNumber,
    projectedStarts: nullableNumber,
    projectedGoals: nullableNumber,
    projectedAssists: nullableNumber,
    reliabilityScore: nullableNumber,
    potentialScore: nullableNumber,
    riskScore: nullableNumber,
    valueScore: nullableNumber,
    summary: z.string().nullable(),
  })
  .nullable();
const playerSeasonStatisticsSchema = z
  .object({
    season: z.string(),
    source: z.string(),
    sourceExternalId: z.string().nullish(),
    club: z.string().nullish(),
    historicalClubs: z.array(z.string()).optional().default([]),
    appearances: nullableNumber.optional(),
    starts: nullableNumber.optional(),
    minutesPlayed: nullableNumber.optional(),
    averageRating: nullableNumber.optional(),
    fantasyAverage: nullableNumber.optional(),
    goals: nullableNumber.optional(),
    assists: nullableNumber.optional(),
    penaltiesTaken: nullableNumber.optional(),
    penaltiesScored: nullableNumber.optional(),
    penaltiesMissed: nullableNumber.optional(),
    yellowCards: nullableNumber.optional(),
    redCards: nullableNumber.optional(),
    cleanSheets: nullableNumber.optional(),
    goalsConceded: nullableNumber.optional(),
    saves: nullableNumber.optional(),
    penaltiesSaved: nullableNumber.optional(),
    expectedGoals: nullableNumber.optional(),
    expectedAssists: nullableNumber.optional(),
    goalsPer90: nullableNumber.optional(),
    assistsPer90: nullableNumber.optional(),
    minutesPerAppearance: nullableNumber.optional(),
    startPercentage: nullableNumber.optional(),
    importedAt: z.string().nullish(),
    sourceUpdatedAt: z.string().nullish(),
    importBatchId: nullableNumber.optional(),
  })
  .passthrough();

export const historicalPlayerDetailSchema = z.object({
  id: numeric,
  name: z.string(),
  active: z.boolean(),
  season: z.string(),
  club: z.string().nullable(),
  clubSlug: z.string().nullable(),
  roles: z.record(z.string(), z.string()),
  statistics: playerSeasonStatisticsSchema.array(),
});

export const playerDetailSchema = z.object({
  id: numeric,
  name: z.string(),
  club: z.string(),
  clubSlug: z.string(),
  roles: z.record(z.string(), z.string()),
  tacticalProfile: z
    .object({
      realPosition: z.string().nullable(),
      secondaryPosition: z.string().nullable(),
      tacticalRole: z.string().nullable(),
      attackingInvolvement: nullableNumber,
      defensiveInvolvement: nullableNumber,
      setPieceTaker: z.boolean(),
      penaltyTaker: z.boolean(),
      cornerTaker: z.boolean(),
      freeKickTaker: z.boolean(),
      projectedStarter: z.boolean(),
      starterProbability: nullableNumber,
      competitionLevel: z.string().nullable(),
      tacticalNotes: z.string().nullable(),
    })
    .nullable(),
  clubTactics: z.unknown().nullable(),
  evaluation: evaluationSchema,
  statistics: playerSeasonStatisticsSchema.array(),
});
const auctionPlayerSchema = z.object({
  position: numeric,
  playerId: numeric,
  playerName: z.string(),
  club: z.string(),
  fantasyRole: z.string(),
  intelligenceScore: numeric,
  minimumPrice: numeric,
  recommendedPrice: numeric,
  maximumPrice: numeric,
  budgetPercentage: numeric,
  reliabilityScore: nullableNumber,
  potentialScore: nullableNumber,
  riskScore: nullableNumber,
  starterProbability: nullableNumber,
  availabilityStatus: z.string().nullable(),
  availabilityMatchday: nullableNumber,
  strengths: z.array(z.string()),
  risks: z.array(z.string()),
  finalRecommendation: z.string(),
});
export const rankingSchema = z.object({
  season: z.string(),
  budget: numeric,
  teams: numeric,
  defenseModifier: z.boolean(),
  role: z.string().nullable(),
  club: z.string().nullable(),
  maximumPrice: nullableNumber,
  recommendation: z.string().nullable(),
  excludeUnavailable: z.boolean(),
  totalPlayers: numeric,
  players: z.array(auctionPlayerSchema),
});
export const strategySchema = z.object({
  season: z.string(),
  totalBudget: numeric,
  teams: numeric,
  defenseModifier: z.boolean(),
  totalRequiredPlayers: numeric,
  totalPlannedSpending: numeric,
  expectedRemainingBudget: numeric,
  withinBudget: z.boolean(),
  strategySummary: z.string(),
  roles: z.array(
    z
      .object({
        role: z.string(),
        requiredSlots: numeric,
        budgetPercentage: numeric,
        allocatedBudget: numeric,
        plannedSpending: numeric,
        remainingRoleBudget: numeric,
        warnings: z.array(z.string()),
      })
      .passthrough(),
  ),
  globalWarnings: z.array(z.string()),
});
const squadPlayerSchema = z.object({
  id: numeric,
  playerId: numeric,
  playerName: z.string(),
  club: z.string(),
  role: z.string(),
  purchasePrice: numeric,
});
export const squadSchema = z.object({
  id: numeric,
  name: z.string(),
  season: z.string(),
  initialBudget: numeric,
  spentBudget: numeric,
  remainingBudget: numeric,
  totalPlayers: numeric,
  maximumPlayers: numeric,
  roles: z.record(z.string(), z.object({ current: numeric, maximum: numeric, remaining: numeric })),
  players: z.array(squadPlayerSchema),
});
const leagueConfigurationSchema = z.object({
  id: numeric,
  name: z.string(),
  season: z.string(),
  teamsCount: numeric,
  totalBudget: numeric,
  goalkeepersCount: numeric,
  defendersCount: numeric,
  midfieldersCount: numeric,
  forwardsCount: numeric,
  maxSubstitutions: numeric,
  defenseModifier: z.boolean(),
  captainBonus: z.boolean(),
  voteSource: z.string(),
  active: z.boolean(),
});
export const analysisSchema = z
  .object({
    squadId: numeric,
    squadName: z.string(),
    season: z.string(),
    totalPlayers: numeric,
    maximumPlayers: numeric,
    remainingSlots: numeric,
    constraintsValid: z.boolean(),
    squadComplete: z.boolean(),
    summary: z.string(),
    roles: z.array(
      z.object({
        role: z.string(),
        currentPlayers: numeric,
        maximumPlayers: numeric,
        remainingSlots: numeric,
        complete: z.boolean(),
        coverageStatus: z.string(),
        warnings: z.array(z.string()),
      }),
    ),
    globalWarnings: z.array(z.string()),
  })
  .passthrough();
export const sessionSchema = z.object({
  id: numeric,
  squadId: numeric,
  season: z.string(),
  leagueTeams: numeric,
  totalBudget: numeric,
  defenseModifier: z.boolean(),
  status: z.string(),
  playerStateInitialized: z.boolean(),
  currentPlayerId: nullableNumber,
  currentPrice: nullableNumber,
  version: numeric,
  createdAt: z.string(),
  updatedAt: z.string(),
});
export const purchaseSchema = z.object({
  id: numeric,
  sessionId: numeric,
  playerId: numeric,
  buyerType: z.enum(['USER', 'OTHER']),
  paidPrice: nullableNumber,
  idempotencyKey: z.string(),
  createdAt: z.string(),
});
export const sessionDecisionSchema = z
  .object({
    action: z.string(),
    currentPlayerId: numeric,
    currentPlayerName: z.string().nullable(),
    currentRole: z.string(),
    currentPrice: nullableNumber,
    proposedBid: numeric,
    evaluatedBid: numeric,
    remainingBudget: numeric,
    budgetAfterBid: numeric,
    priceZone: z.string().nullable(),
    reasons: z.array(z.string()),
    warnings: z.array(z.string()),
  })
  .passthrough();
export const stateSchema = z.object({
  session: sessionSchema,
  spentBudget: numeric,
  remainingBudget: numeric,
  purchases: z.array(purchaseSchema),
  players: z.array(
    z.object({
      playerId: numeric,
      playerName: z.string(),
      role: z.string(),
      availability: z.string(),
    }),
  ),
  availabilityComplete: z.boolean(),
});

function params(values: Record<string, string | number | boolean | undefined>) {
  const p = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined && value !== '') p.set(key, String(value));
  });
  return p.toString();
}
async function parsed<T>(
  path: string,
  schema: z.ZodType<T>,
  signal?: AbortSignal,
  getAuthorization?: () => string | undefined,
  init?: RequestInit,
) {
  return schema.parse(await httpRequest<unknown>(path, { ...init, signal, getAuthorization }));
}

export const featureApi = {
  players: (
    filters: { search?: string; club?: string; role?: string; page: number; size: number },
    signal?: AbortSignal,
  ) => parsed(`/players?${params(filters)}`, playerPageSchema, signal),
  historicalPlayers: (
    filters: {
      season: string;
      search?: string;
      role?: string;
      page: number;
      size: number;
    },
    signal?: AbortSignal,
  ) => parsed(`/players/history?${params(filters)}`, historicalPlayerPageSchema, signal),
  historicalPlayer: (id: string, season: string, signal?: AbortSignal) =>
    parsed(`/players/history/${id}?${params({ season })}`, historicalPlayerDetailSchema, signal),
  player: (id: string, signal?: AbortSignal) =>
    parsed(`/players/${id}`, playerDetailSchema, signal),
  ranking: (role: string, signal?: AbortSignal) =>
    parsed(
      `/auction/intelligence-rankings?${params({ role: role || undefined, limit: 600 })}`,
      rankingSchema,
      signal,
    ),
  strategy: (signal?: AbortSignal) => parsed('/auction/strategy', strategySchema, signal),
  squad: (auth: () => string | undefined, signal?: AbortSignal) =>
    parsed('/squad', squadSchema, signal, auth),
  leagueConfiguration: (signal?: AbortSignal) =>
    parsed('/league/configuration', leagueConfigurationSchema, signal),
  createSquad: (auth: () => string | undefined, leagueConfigurationId: number, name: string) =>
    parsed('/squads', squadSchema, undefined, auth, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ leagueConfigurationId, name, active: true }),
    }),
  analysis: (auth: () => string | undefined, signal?: AbortSignal) =>
    parsed('/squad-analysis', analysisSchema, signal, auth),
  addPlayer: (auth: () => string | undefined, playerId: number, purchasePrice: number) =>
    parsed('/squad/players', squadSchema, undefined, auth, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ playerId, purchasePrice }),
    }),
  removePlayer: (auth: () => string | undefined, playerId: number) =>
    parsed(`/squad/players/${playerId}`, squadSchema, undefined, auth, { method: 'DELETE' }),
  updatePlayerPrice: (auth: () => string | undefined, playerId: number, purchasePrice: number) =>
    parsed(`/squad/players/${playerId}`, squadSchema, undefined, auth, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ purchasePrice }),
    }),
  createSession: (auth: () => string | undefined, squadId: number) =>
    parsed('/auction/sessions', sessionSchema, undefined, auth, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ squadId }),
    }),
  sessionState: (auth: () => string | undefined, id: string, signal?: AbortSignal) =>
    parsed(`/auction/sessions/${id}/state`, stateSchema, signal, auth),
  activateSession: (auth: () => string | undefined, id: string) =>
    parsed(`/auction/sessions/${id}/activate`, sessionSchema, undefined, auth, { method: 'POST' }),
  completeSession: (auth: () => string | undefined, id: string) =>
    parsed(`/auction/sessions/${id}/complete`, sessionSchema, undefined, auth, { method: 'POST' }),
  nominatePlayer: (
    auth: () => string | undefined,
    id: string,
    playerId: number,
    currentPrice: number,
  ) =>
    parsed(`/auction/sessions/${id}/nomination`, sessionSchema, undefined, auth, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ playerId, currentPrice }),
    }),
  sessionDecision: (auth: () => string | undefined, id: string, proposedBid: number) =>
    parsed(`/auction/sessions/${id}/decision`, sessionDecisionSchema, undefined, auth, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ proposedBid }),
    }),
  purchasePlayer: (
    auth: () => string | undefined,
    id: string,
    request: {
      playerId: number;
      buyerType: 'USER' | 'OTHER';
      paidPrice: number | null;
      idempotencyKey: string;
    },
  ) =>
    parsed(`/auction/sessions/${id}/purchases`, purchaseSchema, undefined, auth, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    }),
};
export type PlayerPage = z.infer<typeof playerPageSchema>;
export type HistoricalPlayerPage = z.infer<typeof historicalPlayerPageSchema>;
export type PlayerSeasonStatistics = z.infer<typeof playerSeasonStatisticsSchema>;
export type Squad = z.infer<typeof squadSchema>;
export type AuctionState = z.infer<typeof stateSchema>;
export type AuctionDecision = z.infer<typeof sessionDecisionSchema>;
