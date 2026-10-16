import {
  auctionRankingSchema,
  catalogReadinessSchema,
  clubSchema,
  fixtureSchema,
  playerPageSchema,
  recommendedLineupSchema,
  scoutingArrivalSchema,
  seasonSchema,
  systemStatusSchema,
} from '@/api/contracts/dashboard';
import { httpRequest } from '@/api/client/httpClient';
import { AppError } from '@/api/errors/AppError';
import type { z } from 'zod';

function query(path: string, params: Record<string, string | number | boolean | undefined>) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined) search.set(key, String(value));
  });
  const suffix = search.toString();
  return suffix ? `${path}?${suffix}` : path;
}

async function getValidated<T extends z.ZodType>(
  path: string,
  schema: T,
  getAuthorization?: () => string | undefined,
): Promise<z.infer<T>> {
  const body = await httpRequest<unknown>(path, { getAuthorization });
  const parsed = schema.safeParse(body);
  if (!parsed.success) {
    throw new AppError('Il servizio ha restituito dati non conformi al contratto', {
      kind: 'invalid-response',
    });
  }
  return parsed.data;
}

export const dashboardApi = {
  system: () => getValidated('/system/status', systemStatusSchema),
  seasons: () => getValidated('/seasons', seasonSchema.array()),
  clubs: () => getValidated('/clubs', clubSchema.array()),
  players: (season?: string) =>
    getValidated(query('/players', { season, page: 0, size: 5 }), playerPageSchema),
  auction: (season?: string) =>
    getValidated(
      query('/auction/intelligence-rankings', { season, budget: 500, teams: 8, limit: 5 }),
      auctionRankingSchema,
    ),
  fixtures: (season?: string, matchday = 1) =>
    getValidated(query(`/fixtures/matchday/${matchday}`, { season }), fixtureSchema.array()),
  lineup: (
    season?: string,
    matchday = 1,
    squadId?: number,
    getAuthorization?: () => string | undefined,
  ) =>
    getValidated(
      query('/lineups/recommended', { season, matchday, squadId }),
      recommendedLineupSchema,
      getAuthorization,
    ),
  scouting: (season?: string) =>
    getValidated(query('/scouting/arrivals', { season }), scoutingArrivalSchema.array()),
  squads: (getAuthorization: () => string | undefined) =>
    httpRequest<unknown[]>('/squads', { getAuthorization }),
  readiness: (season: string | undefined, getAuthorization: () => string | undefined) =>
    getValidated(
      query('/admin/catalog/readiness', { season }),
      catalogReadinessSchema,
      getAuthorization,
    ),
};
