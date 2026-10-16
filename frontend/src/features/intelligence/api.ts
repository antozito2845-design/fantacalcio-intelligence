import { z } from 'zod';
import { httpRequest } from '@/api/client/httpClient';
import { AppError } from '@/api/errors/AppError';

const number = z.number();
const nullableNumber = number.nullable();
const lineupPlayerSchema = z.object({
  playerId: number,
  playerName: z.string(),
  club: z.string(),
  role: z.string(),
  opponent: z.string(),
  home: z.boolean(),
  // Missing signals stay null so the UI never fabricates scores or availability.
  fixtureDifficulty: nullableNumber.optional().default(null),
  selectionScore: nullableNumber.optional().default(null),
  availabilityStatus: z.string().nullable().optional().default(null),
  reason: z.string(),
});
export const lineupSchema = z.object({
  season: z.string(),
  matchday: number,
  squadId: nullableNumber,
  formation: z.string(),
  complete: z.boolean(),
  selectedPlayers: number,
  missingPlayers: number,
  formationScore: number,
  // Collections are optional in older/partial responses; absence means no data, never fake players.
  roles: z
    .record(z.string(), z.object({ required: number, selected: number, missing: number }))
    .optional()
    .default({}),
  starters: z.array(lineupPlayerSchema).optional().default([]),
  bench: z.array(lineupPlayerSchema).optional().default([]),
  maxSubstitutions: number,
  usedSubstitutions: number,
  remainingSubstitutions: number,
  substitutions: z
    .array(
      z.object({
        outgoingPlayer: lineupPlayerSchema,
        incomingPlayer: lineupPlayerSchema,
        reason: z.string(),
      }),
    )
    .optional()
    .default([]),
  warnings: z.array(z.string()).optional().default([]),
});
export const fixtureSchema = z.object({
  fixtureId: number,
  matchday: number,
  homeClub: z.string(),
  awayClub: z.string(),
  homeDifficulty: z.string(),
  awayDifficulty: z.string(),
});
const roleMatchupSchema = z.object({
  role: z.string(),
  difficultyScore: number,
  difficultyLevel: z.string(),
  evidenceTier: z.string(),
  evidenceCoverage: number,
});
export const matchupSchema = z.object({
  fixtureId: number,
  matchday: number,
  kickoffAt: z.string().nullable(),
  clubId: number,
  club: z.string(),
  opponentClubId: number,
  opponent: z.string(),
  home: z.boolean(),
  status: z.string(),
  roles: z.array(roleMatchupSchema),
});
export const scoutingSchema = z.object({
  playerId: number,
  playerName: z.string(),
  club: z.string(),
  role: z.string(),
  season: z.string(),
  previousClub: z.string().nullable(),
  previousLeague: z.string().nullable(),
  previousCountryCode: z.string().nullable(),
  transferType: z.string().nullable(),
  adaptationScore: number,
  potentialScore: number,
  riskScore: number,
  confidenceScore: number,
  recommendation: z.string(),
  scoutingSummary: z.string(),
  eligibilityStatus: z.string(),
  updatedAt: z.string(),
});
export const seasonSchema = z.object({
  id: number,
  code: z.string(),
  startYear: number,
  endYear: number,
  current: z.boolean(),
});
export const clubSchema = z.object({
  id: number,
  name: z.string(),
  shortName: z.string(),
  slug: z.string(),
  countryCode: z.string(),
  active: z.boolean(),
});

function qs(values: Record<string, string | number | undefined>) {
  const p = new URLSearchParams();
  Object.entries(values).forEach(([k, v]) => {
    if (v !== undefined && v !== '') p.set(k, String(v));
  });
  return p.toString();
}
async function parse<T>(
  path: string,
  schema: z.ZodType<T>,
  signal?: AbortSignal,
  getAuthorization?: () => string | undefined,
) {
  const body = await httpRequest<unknown>(path, { signal, getAuthorization });
  const parsed = schema.safeParse(body);
  if (!parsed.success) {
    throw new AppError('Il servizio ha restituito dati non conformi al contratto', {
      kind: 'invalid-response',
      cause: parsed.error,
    });
  }
  return parsed.data;
}
export const intelligenceApi = {
  seasons: (signal?: AbortSignal) => parse('/seasons', seasonSchema.array(), signal),
  clubs: (signal?: AbortSignal) => parse('/clubs', clubSchema.array(), signal),
  lineup: (
    matchday: number,
    season?: string,
    squadId?: number,
    getAuthorization?: () => string | undefined,
    signal?: AbortSignal,
  ) =>
    parse(
      `/lineups/recommended?${qs({ season, matchday, squadId })}`,
      lineupSchema,
      signal,
      getAuthorization,
    ),
  fixtures: (matchday: number, signal?: AbortSignal) =>
    parse(`/fixtures/matchday/${matchday}`, fixtureSchema.array(), signal),
  matchups: (club: string, fromMatchday: number, toMatchday: number, signal?: AbortSignal) =>
    parse(
      `/fixtures/club/${encodeURIComponent(club)}/matchups?${qs({ fromMatchday, toMatchday })}`,
      matchupSchema.array(),
      signal,
    ),
  scouting: (
    season: string,
    filters: { role?: string; club?: string; recommendation?: string },
    signal?: AbortSignal,
  ) => parse(`/scouting/arrivals?${qs({ season, ...filters })}`, scoutingSchema.array(), signal),
};
export type Lineup = z.infer<typeof lineupSchema>;
export type LineupPlayer = z.infer<typeof lineupPlayerSchema>;
