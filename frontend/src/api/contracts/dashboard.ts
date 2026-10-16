import { z } from 'zod';

export const systemStatusSchema = z.object({
  status: z.string(),
  application: z.string(),
  version: z.string(),
  environment: z.string(),
  checkedAt: z.string(),
});

export const seasonSchema = z.object({
  id: z.number(),
  code: z.string(),
  startYear: z.number(),
  endYear: z.number(),
  current: z.boolean(),
});

export const clubSchema = z.object({
  id: z.number(),
  name: z.string(),
  shortName: z.string(),
  slug: z.string(),
  countryCode: z.string(),
  active: z.boolean(),
});

export const playerSchema = z.object({
  id: z.number(),
  name: z.string(),
  club: z.string(),
  clubSlug: z.string(),
  fantacalcioRole: z.string().nullable(),
  gazzettaRole: z.string().nullable(),
  fantamarktRole: z.string().nullable(),
  sourcesCount: z.number(),
});

export const playerPageSchema = z.object({
  content: z.array(playerSchema),
  page: z.number(),
  size: z.number(),
  totalElements: z.number(),
  totalPages: z.number(),
});

export const auctionItemSchema = z.object({
  position: z.number(),
  playerId: z.number(),
  playerName: z.string(),
  club: z.string(),
  fantasyRole: z.string(),
  intelligenceScore: z.number(),
  minimumPrice: z.number(),
  recommendedPrice: z.number(),
  maximumPrice: z.number(),
  budgetPercentage: z.number(),
  reliabilityScore: z.number(),
  potentialScore: z.number(),
  riskScore: z.number(),
  starterProbability: z.number(),
  availabilityStatus: z.string().nullable(),
  availabilityMatchday: z.number().nullable(),
  strengths: z.array(z.string()),
  risks: z.array(z.string()),
  finalRecommendation: z.string(),
});

export const auctionRankingSchema = z.object({
  season: z.string(),
  budget: z.number(),
  teams: z.number(),
  defenseModifier: z.boolean(),
  role: z.string().nullable(),
  club: z.string().nullable(),
  maximumPrice: z.number().nullable(),
  recommendation: z.string().nullable(),
  excludeUnavailable: z.boolean(),
  totalPlayers: z.number(),
  players: z.array(auctionItemSchema),
});

export const fixtureSchema = z.object({
  fixtureId: z.number(),
  matchday: z.number(),
  homeClub: z.string(),
  awayClub: z.string(),
  homeDifficulty: z.string(),
  awayDifficulty: z.string(),
});

export const lineupPlayerSchema = z.object({
  playerId: z.number(),
  playerName: z.string(),
  club: z.string(),
  role: z.string(),
  opponent: z.string(),
  home: z.boolean(),
  // The real engine omits missing signals as null; it never invents a neutral value.
  fixtureDifficulty: z.number().nullable(),
  selectionScore: z.number().nullable(),
  availabilityStatus: z.string().nullable(),
  reason: z.string(),
});

export const recommendedLineupSchema = z.object({
  season: z.string(),
  matchday: z.number(),
  squadId: z.number().nullable(),
  formation: z.string(),
  complete: z.boolean(),
  selectedPlayers: z.number(),
  missingPlayers: z.number(),
  formationScore: z.number(),
  starters: z.array(lineupPlayerSchema),
  warnings: z.array(z.string()),
});

export const scoutingArrivalSchema = z.object({
  playerId: z.number(),
  playerName: z.string(),
  club: z.string(),
  role: z.string(),
  season: z.string(),
  previousClub: z.string().nullable(),
  previousLeague: z.string().nullable(),
  previousCountryCode: z.string().nullable(),
  transferType: z.string().nullable(),
  adaptationScore: z.number(),
  potentialScore: z.number(),
  riskScore: z.number(),
  confidenceScore: z.number(),
  recommendation: z.string(),
  scoutingSummary: z.string(),
  eligibilityStatus: z.string(),
  updatedAt: z.string(),
});

export const catalogReadinessSchema = z.object({
  season: z.string(),
  activeCatalogPlayers: z.number(),
  qualityPercentage: z.number(),
  readinessStatus: z.string(),
  totalOperationalPlayers: z.number(),
  totalClubs: z.number(),
  playersByRole: z.object({ P: z.number(), D: z.number(), C: z.number(), A: z.number() }),
  errorCount: z.number(),
  warningCount: z.number(),
});

export type SystemStatusDto = z.infer<typeof systemStatusSchema>;
export type SeasonDto = z.infer<typeof seasonSchema>;
export type ClubDto = z.infer<typeof clubSchema>;
export type PlayerPageDto = z.infer<typeof playerPageSchema>;
export type AuctionRankingDto = z.infer<typeof auctionRankingSchema>;
export type FixtureDto = z.infer<typeof fixtureSchema>;
export type RecommendedLineupDto = z.infer<typeof recommendedLineupSchema>;
export type ScoutingArrivalDto = z.infer<typeof scoutingArrivalSchema>;
export type CatalogReadinessDto = z.infer<typeof catalogReadinessSchema>;
